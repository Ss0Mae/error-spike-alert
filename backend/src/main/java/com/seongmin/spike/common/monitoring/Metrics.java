package com.seongmin.spike.common.monitoring;

import com.seongmin.spike.error.domain.Environment;
import com.seongmin.spike.error.infrastructure.DetectionPath;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/** 커스텀 메트릭 한곳. Counter=발생 건수, Timer=분포(p95/p99), Gauge=현재 값. */
@Component
public class Metrics {
    private final MeterRegistry registry;
    public final Counter alertsDetected, alertsSent, alertsFailed, alertsSuppressed, alertRetry,
            redisCounterFailure, databaseFallback, detectionSkipped, cooldownContention;
    public final Timer ingestionDuration, detectionDelay, fallbackDuration, sendDuration;

    public Metrics(MeterRegistry registry, DetectionState state) {
        this.registry = registry;
        alertsDetected = registry.counter("alerts.detected");
        alertsSent = registry.counter("alerts.sent");
        alertsFailed = registry.counter("alerts.failed");
        alertsSuppressed = registry.counter("alerts.suppressed");
        alertRetry = registry.counter("alert.retry");
        redisCounterFailure = registry.counter("redis.counter.failure");
        databaseFallback = registry.counter("database.fallback");
        detectionSkipped = registry.counter("detection.skipped");
        cooldownContention = registry.counter("cooldown.contention");
        ingestionDuration = registry.timer("error.ingestion.duration");
        detectionDelay = registry.timer("alert.detection.delay");
        fallbackDuration = registry.timer("database.fallback.duration");
        sendDuration = registry.timer("alert.send.duration");
        Gauge.builder("detection.mode", state, s -> s.mode().ordinal())
                .description("0=NORMAL 1=FALLBACK 2=DEGRADED").register(registry);
        for (DetectionPath p : DetectionPath.values()) counterDuration(p);
    }

    public Counter errorsReceived(Environment env) { return registry.counter("errors.received", "environment", env.name()); }
    public Timer counterDuration(DetectionPath path) { return registry.timer("error.counter.duration", "path", path.name()); }
}
