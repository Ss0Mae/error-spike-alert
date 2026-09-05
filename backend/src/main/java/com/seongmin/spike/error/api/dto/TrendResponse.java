package com.seongmin.spike.error.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TrendResponse(List<Bucket> buckets, Map<String, Long> recent, List<TopFingerprint> topFingerprints) {
    public record Bucket(Instant ts, long count) {}
    public record TopFingerprint(String fingerprint, String errorType, long count) {}
}
