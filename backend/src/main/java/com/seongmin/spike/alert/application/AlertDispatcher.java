package com.seongmin.spike.alert.application;

import com.seongmin.spike.alert.domain.AlertHistory;
import com.seongmin.spike.alert.domain.AlertHistoryRepository;
import com.seongmin.spike.alert.domain.AlertPolicy;
import com.seongmin.spike.alert.domain.AlertPolicyRepository;
import com.seongmin.spike.common.monitoring.Metrics;
import com.seongmin.spike.error.domain.ErrorEvent;
import com.seongmin.spike.error.domain.ErrorEventRepository;
import com.seongmin.spike.project.domain.Project;
import com.seongmin.spike.project.domain.ProjectRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** @Async 진입점. 결과를 SENT/FAILED 로 기록한다. 프로세스가 죽으면 큐의 작업은 사라지고 PENDING 만 남는다(설계 §7·§19). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertDispatcher {
    private final AlertHistoryRepository histories;
    private final AlertPolicyRepository policies;
    private final ProjectRepository projects;
    private final ErrorEventRepository events;
    private final AlertSender sender;
    private final Metrics metrics;

    @Async("alertExecutor")
    public void dispatch(long alertId) {
        AlertHistory h = histories.findById(alertId).orElseThrow();
        AlertPolicy p = policies.findById(h.getAlertPolicyId()).orElseThrow();
        Project project = projects.findById(h.getProjectId()).orElseThrow();
        ErrorEvent trigger = h.getTriggerEventId() == null ? null : events.findById(h.getTriggerEventId()).orElse(null);
        WebhookPayload payload = new WebhookPayload(h.getId(), p.getId(), project.getId(), project.getName(),
                p.getEnvironment().name(), h.getFingerprint(),
                trigger == null ? null : trigger.getErrorType(), trigger == null ? null : trigger.getMessage(),
                h.getDetectedCount(), p.getThreshold(), p.getWindowSeconds(), h.getDetectedAt(),
                h.getWindowStartedAt(), h.getWindowEndedAt(), trigger == null ? null : trigger.getReceivedAt(),
                h.getDetectionPath().name(), 1);

        AlertSender.SendResult r;
        try {
            r = sender.send(p.getWebhookUrl(), alertId, payload);
        } catch (Exception e) {
            r = new AlertSender.SendResult(false, 1, "UNEXPECTED: " + e);
        }
        Instant now = Instant.now();
        if (r.success()) {
            h.markSent(now, r.attempts());
            metrics.alertsSent.increment();
            if (trigger != null) metrics.detectionDelay.record(Duration.between(trigger.getReceivedAt(), now));
        } else {
            h.markFailed(r.attempts(), r.failureReason());
            metrics.alertsFailed.increment();
            log.warn("alert {} failed after {} attempts: {}", alertId, r.attempts(), r.failureReason());
        }
        histories.save(h);
    }
}
