package com.seongmin.spike.error.infrastructure;

import com.seongmin.spike.common.monitoring.DetectionState;
import com.seongmin.spike.common.monitoring.Metrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** breaker 가 허용하면 Redis, 아니면 DB. 메트릭과 DetectionState 를 여기서만 갱신한다. */
@Slf4j
@Primary
@Component
public class FallbackErrorCounter implements ErrorCounter {
    private final RedisErrorCounter redis;
    private final DatabaseErrorCounter db;
    private final RedisHealthGuard guard;
    private final Metrics metrics;
    private final DetectionState state;

    public FallbackErrorCounter(RedisErrorCounter redis, DatabaseErrorCounter db, RedisHealthGuard guard,
                                Metrics metrics, DetectionState state) {
        this.redis = redis; this.db = db; this.guard = guard; this.metrics = metrics; this.state = state;
    }

    @Override
    public CountResult increment(CounterRequest r) {
        if (guard.allow()) {
            Timer.Sample sample = Timer.start();
            try {
                CountResult res = redis.increment(r);
                guard.recordSuccess();
                state.redisHealthy(true);
                sample.stop(metrics.counterDuration(DetectionPath.REDIS));
                return res;
            } catch (Exception e) {
                guard.recordFailure();
                metrics.redisCounterFailure.increment();
                log.warn("redis counter failed, falling back to DB: {}", e.toString());
            }
        }
        state.redisHealthy(false);
        metrics.databaseFallback.increment();
        Timer.Sample sample = Timer.start();
        CountResult res = db.increment(r);
        sample.stop(metrics.counterDuration(DetectionPath.DB_FALLBACK));
        if (res.isSkipped()) {
            metrics.detectionSkipped.increment();
            state.markSkipped();
        }
        return res;
    }
}
