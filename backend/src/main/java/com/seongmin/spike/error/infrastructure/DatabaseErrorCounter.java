package com.seongmin.spike.error.infrastructure;

import com.seongmin.spike.common.config.SpikeProperties;
import com.seongmin.spike.common.monitoring.Metrics;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 장애 시 timestamp 복합 인덱스 COUNT. DB 를 보호하기 위해
 * (1) 세마포어로 동시 COUNT 수 제한, (2) 1초 로컬 캐시(히트 시 로컬 증가), (3) MAX_EXECUTION_TIME 힌트.
 */
@Slf4j
@Component
public class DatabaseErrorCounter implements ErrorCounter {
    private record Cached(AtomicLong count, long expiresAt) {}

    private final JdbcTemplate jdbc;
    private final Metrics metrics;
    private final Semaphore permits;
    private final long cacheTtlMs;
    private final String sqlByFingerprint;
    private final String sqlAllErrors;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public DatabaseErrorCounter(JdbcTemplate jdbc, Metrics metrics, SpikeProperties props) {
        this.jdbc = jdbc;
        this.metrics = metrics;
        this.permits = new Semaphore(props.fallback().permits());
        this.cacheTtlMs = props.fallback().cacheTtlMs();
        String hint = "SELECT /*+ MAX_EXECUTION_TIME(" + props.fallback().queryTimeoutMs() + ") */ COUNT(*) FROM error_events "
                + "WHERE project_id = ? AND environment = ? AND received_at >= ?";
        this.sqlAllErrors = hint;
        this.sqlByFingerprint = hint + " AND fingerprint = ?";
    }

    @Override
    public CountResult increment(CounterRequest r) {
        long now = System.currentTimeMillis();
        Cached c = cache.get(r.cacheKey());
        if (c != null && now < c.expiresAt()) {
            return new CountResult(c.count().incrementAndGet(), DetectionPath.DB_FALLBACK);
        }
        if (!permits.tryAcquire()) return CountResult.skipped();
        try {
            long count = metrics.fallbackDuration.record(() -> query(r));
            cache.put(r.cacheKey(), new Cached(new AtomicLong(count), now + cacheTtlMs));
            // ponytail: unbounded map; evict expired entries only when it grows large. LRU if fingerprints explode.
            if (cache.size() > 10_000) cache.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
            return new CountResult(count, DetectionPath.DB_FALLBACK);
        } catch (DataAccessException e) {
            log.warn("fallback count failed: {}", e.getMessage());
            return CountResult.skipped();
        } finally {
            permits.release();
        }
    }

    private long query(CounterRequest r) {
        LocalDateTime from = LocalDateTime.ofInstant(r.windowStart(), ZoneOffset.UTC);
        Long n = r.fingerprint() == null
                ? jdbc.queryForObject(sqlAllErrors, Long.class, r.projectId(), r.environment().name(), from)
                : jdbc.queryForObject(sqlByFingerprint, Long.class, r.projectId(), r.environment().name(), from, r.fingerprint());
        return n == null ? 0 : n;
    }

    public int availablePermits() { return permits.availablePermits(); }
    /** 테스트용: 세마포어 고갈 상황 재현. */
    public Semaphore permits() { return permits; }
}
