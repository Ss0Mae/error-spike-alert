# 실험 E 요약 (중앙값 ± 표준편차, n=error500-0.5:3, rate429-0.5:1, slow-1:1, timeout-0.5:1)

| 지표 | error500-0.5 | rate429-0.5 | slow-1 | timeout-0.5 |
|---|---:|---:|---:|---:|
| alertStatusDistribution.FAILED | 1423 ± 18.4 | 1867 ± 0.00 | 1844 ± 0.00 | 1892 ± 0.00 |
| alertStatusDistribution.PENDING | — | 128 ± 0.00 | 144 ± 0.00 | 108 ± 0.00 |
| alertStatusDistribution.SENT | 577 ± 18.4 | 5.00 ± 0.00 | 12.0 ± 0.00 | — |
| appDelta.alert_retry_total | 854 ± 23.2 | 178 ± 0.00 | 0.00 ± 0.00 | 138 ± 0.00 |
| appDelta.alerts_detected_total | 3000 ± 23.3 | 2921 ± 0.00 | 2921 ± 0.00 | 2908 ± 0.00 |
| appDelta.alerts_failed_total | 1951 ± 15.2 | 2575 ± 0.00 | 2493 ± 0.00 | 2573 ± 0.00 |
| appDelta.alerts_sent_total | 1020 ± 21.9 | 208 ± 0.00 | 280 ± 0.00 | 158 ± 0.00 |
| appDelta.alerts_suppressed_total | 1.00 ± 0.47 | 0.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 |
| appDelta.async_executor_queue_size | 0.00 ± 0.00 | 130 ± 0.00 | 140 ± 0.00 | 169 ± 0.00 |
| derived.avgRetriesPerAlert | 0.28 ± 0.00 | 0.06 ± 0.00 | 0.00 ± 0.00 | 0.05 ± 0.00 |
| derived.duplicates | 0.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 |
| derived.finalFailed | 1951 ± 15.2 | 2575 ± 0.00 | 2493 ± 0.00 | 2573 ± 0.00 |
| derived.finalSuccessRate | 34.0 ± 0.54 | 7.10 ± 0.00 | 9.60 ± 0.00 | 5.40 ± 0.00 |
| derived.successRateBeforeRetry | 51.1 ± 0.40 | 47.7 ± 0.00 | 0.00 ± 0.00 | 49.1 ± 0.00 |
| docker.spike-mysql.cpu_avg_pct | 11.5 ± 0.89 | 11.5 ± 0.00 | 7.34 ± 0.00 | 8.43 ± 0.00 |
| docker.spike-mysql.cpu_max_pct | 20.4 ± 2.66 | 18.4 ± 0.00 | 10.8 ± 0.00 | 23.0 ± 0.00 |
| docker.spike-redis.cpu_avg_pct | 0.78 ± 0.06 | 0.77 ± 0.00 | 0.64 ± 0.00 | 0.76 ± 0.00 |
| docker.spike-redis.cpu_max_pct | 1.72 ± 0.08 | 1.56 ± 0.00 | 1.04 ± 0.00 | 1.52 ± 0.00 |
| k6.droppedIterations | — | — | — | 13.0 ± 0.00 |
| k6.durationMs.avg | 8.50 ± 1.39 | 8.92 ± 0.00 | 6.53 ± 0.00 | 22.3 ± 0.00 |
| k6.durationMs.max | 176 ± 128 | 72.2 ± 0.00 | 68.6 ± 0.00 | 1132 ± 0.00 |
| k6.durationMs.p50 | 7.90 ± 0.63 | 8.26 ± 0.00 | 6.43 ± 0.00 | 10.6 ± 0.00 |
| k6.durationMs.p95 | 13.3 ± 1.08 | 16.5 ± 0.00 | 9.42 ± 0.00 | 24.1 ± 0.00 |
| k6.durationMs.p99 | 22.3 ± 24.2 | 22.4 ± 0.00 | 13.1 ± 0.00 | 591 ± 0.00 |
| k6.durationMs{expected_response:true}.avg | 8.50 ± 1.39 | 8.92 ± 0.00 | 6.53 ± 0.00 | 22.3 ± 0.00 |
| k6.durationMs{expected_response:true}.max | 176 ± 128 | 72.2 ± 0.00 | 68.6 ± 0.00 | 1132 ± 0.00 |
| k6.durationMs{expected_response:true}.p50 | 7.90 ± 0.63 | 8.26 ± 0.00 | 6.43 ± 0.00 | 10.6 ± 0.00 |
| k6.durationMs{expected_response:true}.p95 | 13.3 ± 1.08 | 16.5 ± 0.00 | 9.42 ± 0.00 | 24.1 ± 0.00 |
| k6.durationMs{expected_response:true}.p99 | 22.3 ± 24.2 | 22.4 ± 0.00 | 13.1 ± 0.00 | 591 ± 0.00 |
| k6.durationMs{phase:flood}.avg | 8.92 ± 1.44 | 9.37 ± 0.00 | 6.88 ± 0.00 | 23.2 ± 0.00 |
| k6.durationMs{phase:flood}.max | 176 ± 128 | 72.2 ± 0.00 | 68.6 ± 0.00 | 1132 ± 0.00 |
| k6.durationMs{phase:flood}.p50 | 8.12 ± 0.65 | 8.44 ± 0.00 | 6.63 ± 0.00 | 10.9 ± 0.00 |
| k6.durationMs{phase:flood}.p95 | 13.6 ± 1.04 | 16.7 ± 0.00 | 9.56 ± 0.00 | 24.7 ± 0.00 |
| k6.durationMs{phase:flood}.p99 | 23.0 ± 23.9 | 22.7 ± 0.00 | 13.8 ± 0.00 | 614 ± 0.00 |
| k6.durationMs{phase:probe}.avg | 4.39 ± 0.64 | 4.53 ± 0.00 | 3.18 ± 0.00 | 14.3 ± 0.00 |
| k6.durationMs{phase:probe}.max | 44.9 ± 38.5 | 49.8 ± 0.00 | 9.61 ± 0.00 | 689 ± 0.00 |
| k6.durationMs{phase:probe}.p50 | 3.99 ± 0.36 | 3.95 ± 0.00 | 2.94 ± 0.00 | 5.40 ± 0.00 |
| k6.durationMs{phase:probe}.p95 | 6.38 ± 0.98 | 7.06 ± 0.00 | 4.66 ± 0.00 | 15.6 ± 0.00 |
| k6.durationMs{phase:probe}.p99 | 13.9 ± 4.55 | 14.7 ± 0.00 | 5.79 ± 0.00 | 337 ± 0.00 |
| k6.failedRate | 0.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 |
| k6.requests | 3307 ± 0.82 | 3307 ± 0.00 | 3308 ± 0.00 | 3294 ± 0.00 |
| k6.rps | 44.0 ± 0.06 | 44.1 ± 0.00 | 44.1 ± 0.00 | 43.9 ± 0.00 |
| webhook.byOutcome.close | 0.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 |
| webhook.byOutcome.error500 | 1005 ± 24.1 | 0.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 |
| webhook.byOutcome.ok | 1020 ± 21.9 | 208 ± 0.00 | 0.00 ± 0.00 | 158 ± 0.00 |
| webhook.byOutcome.rate429 | 0.00 ± 0.00 | 228 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 |
| webhook.byOutcome.slow | 0.00 ± 0.00 | 0.00 ± 0.00 | 288 ± 0.00 | 0.00 ± 0.00 |
| webhook.byOutcome.timeout | 0.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 | 164 ± 0.00 |
| webhook.dispatchDelayMs.avg | 11278 ± 89.0 | 38472 ± 0.00 | — | 35319 ± 0.00 |
| webhook.dispatchDelayMs.max | 15957 ± 606 | 62815 ± 0.00 | — | 69592 ± 0.00 |
| webhook.dispatchDelayMs.p50 | 11986 ± 254 | 41843 ± 0.00 | — | 36949 ± 0.00 |
| webhook.dispatchDelayMs.p95 | 14383 ± 534 | 59646 ± 0.00 | — | 65453 ± 0.00 |
| webhook.dispatchDelayMs.p99 | 15276 ± 544 | 61312 ± 0.00 | — | 68894 ± 0.00 |
| webhook.duplicates | 0.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 |
| webhook.failed | 1005 ± 24.1 | 228 ± 0.00 | 288 ± 0.00 | 164 ± 0.00 |
| webhook.ok | 1020 ± 21.9 | 208 ± 0.00 | 0.00 ± 0.00 | 158 ± 0.00 |
| webhook.total | 2029 ± 42.9 | 436 ± 0.00 | 288 ± 0.00 | 322 ± 0.00 |
| webhook.unique | 1020 ± 21.9 | 208 ± 0.00 | 0.00 ± 0.00 | 158 ± 0.00 |
