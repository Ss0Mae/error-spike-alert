package com.seongmin.spike.alert.api.dto;

import com.seongmin.spike.alert.domain.PolicyScope;
import com.seongmin.spike.error.domain.Environment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 모든 필드 선택. null 이면 유지. */
public record PolicyPatchRequest(
        Environment environment,
        PolicyScope scope,
        @Size(max = 64) String targetFingerprint,
        @Min(1) @Max(3600) Integer windowSeconds,
        @Min(1) Integer threshold,
        @Min(0) @Max(86400) Integer cooldownSeconds,
        @Pattern(regexp = "WEBHOOK") String channel,
        @Size(max = 500) String webhookUrl,
        Boolean enabled) {}
