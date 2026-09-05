package com.seongmin.spike.error.api.dto;

import com.seongmin.spike.error.domain.ErrorEvent;
import java.time.Instant;

public record ErrorSummary(Long id, String environment, String fingerprint, String errorType, String message,
                           Instant occurredAt, Instant receivedAt, String requestId, String traceId, String serverInstance) {
    public static ErrorSummary from(ErrorEvent e) {
        return new ErrorSummary(e.getId(), e.getEnvironment().name(), e.getFingerprint(), e.getErrorType(), e.getMessage(),
                e.getOccurredAt(), e.getReceivedAt(), e.getRequestId(), e.getTraceId(), e.getServerInstance());
    }
}
