package com.seongmin.spike.alert.application;

import com.seongmin.spike.alert.domain.AlertPolicy;
import com.seongmin.spike.error.infrastructure.DetectionPath;

public record Evaluation(long policyId, long count, int threshold, Result result, DetectionPath path, Long alertId) {
    public enum Result { TRIGGERED, NOT_TRIGGERED, SUPPRESSED, SKIPPED }

    public static Evaluation skipped(AlertPolicy p) { return new Evaluation(p.getId(), -1, p.getThreshold(), Result.SKIPPED, DetectionPath.NONE, null); }
    public static Evaluation notTriggered(AlertPolicy p, long count, DetectionPath path) { return new Evaluation(p.getId(), count, p.getThreshold(), Result.NOT_TRIGGERED, path, null); }
    public static Evaluation suppressed(AlertPolicy p, long count, DetectionPath path) { return new Evaluation(p.getId(), count, p.getThreshold(), Result.SUPPRESSED, path, null); }
    public static Evaluation triggered(AlertPolicy p, long count, DetectionPath path, long alertId) { return new Evaluation(p.getId(), count, p.getThreshold(), Result.TRIGGERED, path, alertId); }
}
