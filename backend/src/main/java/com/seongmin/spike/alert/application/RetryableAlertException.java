package com.seongmin.spike.alert.application;

/** 5xx / 429 / 408 / 타임아웃 / IOException — 잠시 후 다시 보내면 성공할 수 있는 실패. */
public class RetryableAlertException extends RuntimeException {
    public RetryableAlertException(String message) { super(message); }
}
