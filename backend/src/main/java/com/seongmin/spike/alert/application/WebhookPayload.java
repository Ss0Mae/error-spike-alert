package com.seongmin.spike.alert.application;

import java.time.Instant;

public record WebhookPayload(long alertId, long policyId, long projectId, String projectName, String environment,
                             String fingerprint, String errorType, String message, int detectedCount, int threshold,
                             int windowSeconds, Instant detectedAt, Instant windowStartedAt, Instant windowEndedAt,
                             Instant triggerEventReceivedAt, String detectionPath, int attempt) {
    public WebhookPayload withAttempt(int n) {
        return new WebhookPayload(alertId, policyId, projectId, projectName, environment, fingerprint, errorType, message,
                detectedCount, threshold, windowSeconds, detectedAt, windowStartedAt, windowEndedAt,
                triggerEventReceivedAt, detectionPath, n);
    }
}
