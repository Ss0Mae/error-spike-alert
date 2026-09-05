# 실험 D 요약 (중앙값 ± 표준편차, n=cooldown-0:5, cooldown-30:5)

| 지표 | cooldown-0 | cooldown-30 |
|---|---:|---:|
| appDelta.alerts_detected_total | 5978 ± 10.5 | 2.00 ± 0.00 |
| appDelta.alerts_sent_total | 5978 ± 10.5 | 2.00 ± 0.00 |
| appDelta.alerts_suppressed_total | 4.00 ± 10.7 | 5980 ± 0.40 |
| appDelta.cooldown_contention_total | 0.00 ± 0.00 | 5980 ± 0.40 |
| docker.spike-mysql.cpu_avg_pct | 19.8 ± 6.24 | 7.25 ± 0.32 |
| docker.spike-mysql.cpu_max_pct | 35.1 ± 41.3 | 8.52 ± 0.44 |
| docker.spike-redis.cpu_avg_pct | 1.14 ± 0.23 | 1.29 ± 0.03 |
| docker.spike-redis.cpu_max_pct | 1.98 ± 0.83 | 1.72 ± 0.11 |
| k6.durationMs.avg | 3.80 ± 4.84 | 3.42 ± 0.07 |
| k6.durationMs.max | 65.3 ± 94.5 | 18.4 ± 5.86 |
| k6.durationMs.p50 | 2.79 ± 1.12 | 3.41 ± 0.03 |
| k6.durationMs.p95 | 8.10 ± 19.4 | 4.34 ± 0.27 |
| k6.durationMs.p99 | 16.3 ± 28.3 | 5.16 ± 0.50 |
| k6.durationMs{expected_response:true}.avg | 3.80 ± 4.84 | 3.42 ± 0.07 |
| k6.durationMs{expected_response:true}.max | 65.3 ± 94.5 | 18.4 ± 5.86 |
| k6.durationMs{expected_response:true}.p50 | 2.79 ± 1.12 | 3.41 ± 0.03 |
| k6.durationMs{expected_response:true}.p95 | 8.10 ± 19.4 | 4.34 ± 0.27 |
| k6.durationMs{expected_response:true}.p99 | 16.3 ± 28.3 | 5.16 ± 0.50 |
| k6.failedRate | 0.00 ± 0.00 | 0.00 ± 0.00 |
| k6.requests | 6006 ± 0.40 | 6006 ± 0.40 |
| k6.res_not_triggered.rate | 0.30 ± 0.00 | 0.30 ± 0.00 |
| k6.res_suppressed.rate | 0.10 ± 0.17 | 94.9 ± 0.01 |
| k6.res_triggered.rate | 94.8 ± 0.17 | 0.03 ± 0.00 |
| k6.rps | 95.3 ± 0.04 | 95.3 ± 0.01 |
| webhook.detectionDelayMs.avg | 7.40 ± 45.6 | 11.0 ± 0.68 |
| webhook.detectionDelayMs.max | 88.0 ± 226 | 11.0 ± 1.26 |
| webhook.detectionDelayMs.p50 | 6.00 ± 1.60 | 11.0 ± 0.80 |
| webhook.detectionDelayMs.p95 | 15.0 ± 184 | 11.0 ± 1.26 |
| webhook.detectionDelayMs.p99 | 27.0 ± 220 | 11.0 ± 1.26 |
| webhook.duplicates | 0.00 ± 0.00 | 0.00 ± 0.00 |
| webhook.ok | 5978 ± 10.5 | 2.00 ± 0.00 |
| webhook.total | 5978 ± 10.5 | 2.00 ± 0.00 |
| webhook.unique | 5978 ± 10.5 | 2.00 ± 0.00 |
