package com.seongmin.spike.alert.api.dto;

import com.seongmin.spike.alert.domain.AlertHistory;
import java.time.Instant;

public record AlertResponse(Long id, Long policyId, Long projectId, String fingerprint, int detectedCount, Instant detectedAt,
                            Instant windowStartedAt, Instant windowEndedAt, String status, int attemptCount, Instant sentAt,
                            String failureReason, String detectionPath, Long triggerEventId, String dedupKey) {
    public static AlertResponse summary(AlertHistory h) { return build(h, false); }
    public static AlertResponse detail(AlertHistory h) { return build(h, true); }

    private static AlertResponse build(AlertHistory h, boolean detail) {
        return new AlertResponse(h.getId(), h.getAlertPolicyId(), h.getProjectId(), h.getFingerprint(), h.getDetectedCount(),
                h.getDetectedAt(), h.getWindowStartedAt(), h.getWindowEndedAt(), h.getStatus().name(), h.getAttemptCount(),
                h.getSentAt(), h.getFailureReason(), h.getDetectionPath().name(),
                detail ? h.getTriggerEventId() : null, detail ? h.getDedupKey() : null);
    }
}
