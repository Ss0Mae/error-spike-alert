package com.seongmin.spike.alert.application;

/** 나머지 4xx — 다시 보내도 같은 결과. */
public class NonRetryableAlertException extends RuntimeException {
    public NonRetryableAlertException(String message) { super(message); }
}
