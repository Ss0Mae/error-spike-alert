package com.seongmin.spike.error.infrastructure;

import com.seongmin.spike.common.config.SpikeProperties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** Redis 경로 circuit breaker. 실패 → OPEN(openSeconds) → HALF_OPEN(1회 probe) → 성공 시 CLOSED. */
@Component
public class RedisHealthGuard {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final long openMillis;
    private final AtomicLong openUntil = new AtomicLong(0);
    private final AtomicBoolean probing = new AtomicBoolean(false);
    private volatile boolean forced = false;

    public RedisHealthGuard(SpikeProperties props) {
        this.openMillis = props.redisBreaker().openSeconds() * 1000L;
    }

    /** true 면 이번 호출은 Redis 를 시도해도 된다. HALF_OPEN 에서는 한 스레드만 true. */
    public boolean allow() {
        if (forced) return false;
        long until = openUntil.get();
        if (until == 0) return true;
        if (System.currentTimeMillis() < until) return false;
        return probing.compareAndSet(false, true);
    }

    public void recordSuccess() { openUntil.set(0); probing.set(false); }
    public void recordFailure() { openUntil.set(System.currentTimeMillis() + openMillis); probing.set(false); }

    public boolean isClosed() { return !forced && openUntil.get() == 0; }
    public boolean isForced() { return forced; }
    public long openUntil() { return openUntil.get(); }

    public State state() {
        if (forced) return State.OPEN;
        long until = openUntil.get();
        if (until == 0) return State.CLOSED;
        return System.currentTimeMillis() < until ? State.OPEN : State.HALF_OPEN;
    }

    /** 실험용: Redis 가 살아 있어도 강제로 fallback 경로를 태운다. */
    public void forceOpen(boolean force) {
        this.forced = force;
        if (!force) recordSuccess();
    }
}
