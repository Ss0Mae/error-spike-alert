package com.seongmin.spike.alert.api.dto;

import com.seongmin.spike.alert.domain.PolicyScope;
import com.seongmin.spike.error.domain.Environment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PolicyRequest(
        @NotNull Long projectId,
        @NotNull Environment environment,
        @NotNull PolicyScope scope,
        @Size(max = 64) String targetFingerprint,
        @NotNull @Min(1) @Max(3600) Integer windowSeconds,
        @NotNull @Min(1) Integer threshold,
        @NotNull @Min(0) @Max(86400) Integer cooldownSeconds,
        @NotBlank @Pattern(regexp = "WEBHOOK") String channel,
        @NotBlank @Size(max = 500) String webhookUrl,
        Boolean enabled) {}
