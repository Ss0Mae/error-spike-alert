package com.seongmin.spike.alert.application;

import com.seongmin.spike.alert.domain.AlertHistory;
import com.seongmin.spike.alert.domain.AlertHistoryRepository;
import com.seongmin.spike.alert.domain.AlertPolicy;
import com.seongmin.spike.alert.domain.AlertStatus;
import com.seongmin.spike.common.monitoring.Metrics;
import com.seongmin.spike.error.domain.ErrorEvent;
import com.seongmin.spike.error.infrastructure.CountResult;
import com.seongmin.spike.error.infrastructure.CounterRequest;
import com.seongmin.spike.error.infrastructure.ErrorCounter;
import java.time.Instant;
import java.util.concurrent.RejectedExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** count ≥ threshold → cooldown 획득 → AlertHistory(PENDING) → 비동기 dispatch. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThresholdEvaluator {
    private final ErrorCounter counter;
    private final CooldownManager cooldown;
    private final AlertHistoryRepository histories;
    private final AlertDispatcher dispatcher;
    private final Metrics metrics;

    public Evaluation evaluate(AlertPolicy p, ErrorEvent e) {
        String fpKey = p.fpKey(e.getFingerprint());
        CounterRequest req = new CounterRequest(p.getId(), p.getProjectId(), p.getEnvironment(),
                fpKey.equals("*") ? null : fpKey, p.getWindowSeconds(), e.getReceivedAt());
        CountResult res = counter.increment(req);
        if (res.isSkipped()) return Evaluation.skipped(p);
        if (res.count() < p.getThreshold()) return Evaluation.notTriggered(p, res.count(), res.path());

        String dedup = p.dedupKey(fpKey, e.getReceivedAt());
        if (!cooldown.tryAcquire(p, fpKey, dedup)) {
            metrics.alertsSuppressed.increment();
            return Evaluation.suppressed(p, res.count(), res.path());
        }
        AlertHistory h;
        try {
            h = histories.save(AlertHistory.builder()
                    .alertPolicyId(p.getId()).projectId(p.getProjectId())
                    .fingerprint(fpKey.equals("*") ? null : fpKey)
                    .detectedCount((int) res.count()).detectedAt(e.getReceivedAt())
                    .windowStartedAt(req.windowStart()).windowEndedAt(e.getReceivedAt())
                    .status(AlertStatus.PENDING).attemptCount(0)
                    .dedupKey(dedup).detectionPath(res.path()).triggerEventId(e.getId())
                    .createdAt(Instant.now()).build());
        } catch (DataIntegrityViolationException dup) {
            metrics.alertsSuppressed.increment();
            return Evaluation.suppressed(p, res.count(), res.path());
        }
        metrics.alertsDetected.increment();
        try {
            dispatcher.dispatch(h.getId());
        } catch (RejectedExecutionException rejected) {
            h.markFailed(0, "EXECUTOR_SATURATED");
            histories.save(h);
            metrics.alertsFailed.increment();
            log.warn("alert {} rejected: executor saturated", h.getId());
        }
        return Evaluation.triggered(p, res.count(), res.path(), h.getId());
    }
}
