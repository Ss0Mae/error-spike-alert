package com.seongmin.spike.error.api.dto;

import com.seongmin.spike.error.domain.ErrorEvent;
import java.time.Instant;
import java.util.Map;

public record ErrorDetail(Long id, Long projectId, String environment, String fingerprint, String errorType, String message,
                          String stackTrace, Instant occurredAt, Instant receivedAt, String eventId, String requestId,
                          String traceId, String serverInstance, Map<String, Object> metadata, Instant createdAt) {
    public static ErrorDetail from(ErrorEvent e) {
        return new ErrorDetail(e.getId(), e.getProjectId(), e.getEnvironment().name(), e.getFingerprint(), e.getErrorType(),
                e.getMessage(), e.getStackTrace(), e.getOccurredAt(), e.getReceivedAt(), e.getEventId(), e.getRequestId(),
                e.getTraceId(), e.getServerInstance(), e.getMetadata(), e.getCreatedAt());
    }
}
