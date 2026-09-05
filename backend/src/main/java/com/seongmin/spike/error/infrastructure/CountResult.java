package com.seongmin.spike.error.infrastructure;

/** count < 0 이면 감지 생략(SKIPPED). */
public record CountResult(long count, DetectionPath path) {
    public static CountResult skipped() { return new CountResult(-1, DetectionPath.NONE); }
    public boolean isSkipped() { return count < 0; }
}
