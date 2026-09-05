package com.seongmin.spike.alert.application;

import com.seongmin.spike.alert.domain.AlertHistoryRepository;
import com.seongmin.spike.alert.domain.AlertPolicy;
import com.seongmin.spike.alert.domain.AlertPolicyRepository;
import com.seongmin.spike.common.monitoring.Metrics;
import com.seongmin.spike.error.infrastructure.RedisHealthGuard;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 알림 발송 권한을 원자적으로 얻는다. Redis: SET NX EX. Redis 불가: alert_histories.dedup_key 선검사(+UNIQUE 가 최종 판정). */
@Slf4j
@Component
@RequiredArgsConstructor
public class CooldownManager {
    private final StringRedisTemplate redis;
    private final RedisHealthGuard guard;
    private final AlertHistoryRepository histories;
    private final AlertPolicyRepository policies;
    private final Metrics metrics;

    public static String key(long policyId, String fpKey) { return "cd:" + policyId + ":" + fpKey; }

    public boolean tryAcquire(AlertPolicy policy, String fpKey, String dedupKey) {
        if (policy.getCooldownSeconds() <= 0) return true;
        if (guard.isClosed()) {
            try {
                Boolean ok = redis.opsForValue().setIfAbsent(key(policy.getId(), fpKey),
                        String.valueOf(System.currentTimeMillis()), Duration.ofSeconds(policy.getCooldownSeconds()));
                if (Boolean.TRUE.equals(ok)) return true;
                metrics.cooldownContention.increment();
                return false;
            } catch (Exception e) {
                guard.recordFailure();
                log.warn("cooldown SET NX failed, using DB dedup: {}", e.toString());
            }
        }
        boolean acquired = !histories.existsByDedupKey(dedupKey);
        if (!acquired) metrics.cooldownContention.increment();
        return acquired;
    }

    public record ActiveCooldown(long policyId, String fingerprint, long remainingSeconds) {}

    public List<ActiveCooldown> activeCooldowns(long projectId) {
        List<ActiveCooldown> out = new ArrayList<>();
        if (!guard.isClosed()) return out;
        try {
            for (AlertPolicy p : policies.findByProjectIdOrderByIdAsc(projectId)) {
                String prefix = key(p.getId(), "");
                try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(prefix + "*").count(100).build())) {
                    while (cursor.hasNext()) {
                        String k = cursor.next();
                        Long ttl = redis.getExpire(k, TimeUnit.SECONDS);
                        if (ttl != null && ttl > 0) out.add(new ActiveCooldown(p.getId(), k.substring(prefix.length()), ttl));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("cooldown scan failed: {}", e.toString());
        }
        return out;
    }
}
