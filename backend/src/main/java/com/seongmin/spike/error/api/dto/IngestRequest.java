package com.seongmin.spike.error.api.dto;

import com.seongmin.spike.error.domain.Environment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

public record IngestRequest(
        @Size(max = 36) String eventId,
        @NotNull Environment environment,
        @NotBlank @Size(max = 255) String errorType,
        @Size(max = 64) String fingerprint,
        @Size(max = 2000) String message,
        String stackTrace,
        Instant occurredAt,
        @Size(max = 64) String requestId,
        @Size(max = 64) String traceId,
        @Size(max = 100) String serverInstance,
        Map<String, Object> metadata) {}
