package com.seongmin.spike.error.application;

import com.seongmin.spike.alert.application.Evaluation;
import com.seongmin.spike.alert.application.PolicyCache;
import com.seongmin.spike.alert.application.ThresholdEvaluator;
import com.seongmin.spike.common.monitoring.Metrics;
import com.seongmin.spike.error.api.dto.IngestRequest;
import com.seongmin.spike.error.api.dto.IngestResponse;
import com.seongmin.spike.error.domain.ErrorEvent;
import com.seongmin.spike.error.domain.ErrorEventRepository;
import com.seongmin.spike.project.domain.Project;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 1) MySQL INSERT (repository.save 의 자체 트랜잭션으로 즉시 커밋) → 2) 정책마다 임계값 평가.
 * 일부러 @Transactional 을 붙이지 않는다: 평가(Redis/DB COUNT/비동기 dispatch)는 커밋된 행을 전제로 한다.
 */
@Service
@RequiredArgsConstructor
public class ErrorIngestionService {
    private final ErrorEventRepository events;
    private final Fingerprinter fingerprinter;
    private final PolicyCache policyCache;
    private final ThresholdEvaluator evaluator;
    private final Metrics metrics;
    private final Clock clock;

    public IngestResponse ingest(Project project, IngestRequest req) {
        Timer.Sample sample = Timer.start();
        Instant receivedAt = Instant.now(clock);
        String eventId = req.eventId() != null ? req.eventId() : UUID.randomUUID().toString();
        String fingerprint = req.fingerprint() != null ? req.fingerprint()
                : fingerprinter.generate(req.errorType(), req.message(), req.stackTrace());
        ErrorEvent event = ErrorEvent.builder()
                .projectId(project.getId()).environment(req.environment()).fingerprint(fingerprint)
                .errorType(req.errorType()).message(req.message()).stackTrace(req.stackTrace())
                .occurredAt(req.occurredAt() != null ? req.occurredAt() : receivedAt).receivedAt(receivedAt)
                .eventId(eventId).requestId(req.requestId()).traceId(req.traceId()).serverInstance(req.serverInstance())
                .metadata(req.metadata()).createdAt(receivedAt).build();
        ErrorEvent saved;
        try {
            saved = events.save(event);
        } catch (DataIntegrityViolationException dup) {
            ErrorEvent existing = events.findByProjectIdAndEventId(project.getId(), eventId).orElseThrow();
            return new IngestResponse(existing.getId(), existing.getEventId(), existing.getFingerprint(), true,
                    existing.getReceivedAt(), List.of());
        }
        metrics.errorsReceived(req.environment()).increment();
        List<Evaluation> evaluations = policyCache.matching(project.getId(), req.environment(), fingerprint).stream()
                .map(p -> evaluator.evaluate(p, saved)).toList();
        sample.stop(metrics.ingestionDuration);
        return new IngestResponse(saved.getId(), eventId, fingerprint, false, receivedAt, evaluations);
    }
}
