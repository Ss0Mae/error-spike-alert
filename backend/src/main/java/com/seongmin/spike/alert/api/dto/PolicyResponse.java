package com.seongmin.spike.alert.api.dto;

import com.seongmin.spike.alert.domain.AlertPolicy;
import java.time.Instant;

public record PolicyResponse(Long id, Long projectId, String environment, String scope, String targetFingerprint,
                             int windowSeconds, int threshold, int cooldownSeconds, String channel, String webhookUrl,
                             boolean enabled, Instant createdAt, Instant updatedAt) {
    public static PolicyResponse from(AlertPolicy p) {
        return new PolicyResponse(p.getId(), p.getProjectId(), p.getEnvironment().name(), p.getScope().name(),
                p.getTargetFingerprint(), p.getWindowSeconds(), p.getThreshold(), p.getCooldownSeconds(), p.getChannel(),
                p.getWebhookUrl(), p.isEnabled(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
