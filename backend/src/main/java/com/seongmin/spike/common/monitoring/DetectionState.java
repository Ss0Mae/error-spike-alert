package com.seongmin.spike.common.monitoring;

import org.springframework.stereotype.Component;

/** 현재 감지 경로 상태. NORMAL=Redis, FALLBACK=DB 집계, DEGRADED=최근 5초 안에 감지를 생략한 적 있음. */
@Component
public class DetectionState {
    public enum Mode { NORMAL, FALLBACK, DEGRADED }

    private volatile boolean redisHealthy = true;
    private volatile long lastSkippedAt = 0;

    public void redisHealthy(boolean healthy) { this.redisHealthy = healthy; }
    public void markSkipped() { this.lastSkippedAt = System.currentTimeMillis(); }

    public Mode mode() {
        if (System.currentTimeMillis() - lastSkippedAt < 5_000) return Mode.DEGRADED;
        return redisHealthy ? Mode.NORMAL : Mode.FALLBACK;
    }
}
