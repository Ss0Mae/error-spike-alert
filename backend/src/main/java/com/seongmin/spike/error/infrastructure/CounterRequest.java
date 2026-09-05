package com.seongmin.spike.error.infrastructure;

import com.seongmin.spike.error.domain.Environment;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** fingerprint == null 이면 ALL_ERRORS 범위. */
public record CounterRequest(long policyId, long projectId, Environment environment, String fingerprint,
                             int windowSeconds, Instant at) {
    public String fpKey() { return fingerprint == null ? "*" : fingerprint; }
    public String cacheKey() { return policyId + ":" + fpKey(); }
    /** 윈도우 = [truncSec(at) - W + 1, truncSec(at)] (초 단위 정수, Redis 버킷과 동일 정의). */
    public Instant windowStart() { return at.truncatedTo(ChronoUnit.SECONDS).minusSeconds(windowSeconds - 1L); }
}
