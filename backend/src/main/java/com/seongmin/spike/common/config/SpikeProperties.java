package com.seongmin.spike.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spike")
public record SpikeProperties(String adminToken, long policyCacheRefreshMs, RedisBreaker redisBreaker,
                              Fallback fallback, Alert alert) {
    public record RedisBreaker(int openSeconds) {}
    public record Fallback(int permits, long cacheTtlMs, long queryTimeoutMs) {}
    public record Alert(Executor executor, Webhook webhook, Retry retry) {
        public record Executor(int corePoolSize, int maxPoolSize, int queueCapacity) {}
        public record Webhook(long connectTimeoutMs, long readTimeoutMs, int maxRetryAfterSeconds) {}
        public record Retry(int maxAttempts, long backoffMs, double multiplier) {}
    }
}
