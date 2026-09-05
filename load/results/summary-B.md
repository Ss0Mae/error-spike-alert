# 실험 B 요약 (중앙값 ± 표준편차, n=db-index:5, db-noindex:5, redis:5)

| 지표 | db-index | db-noindex | redis |
|---|---:|---:|---:|
| app.database_fallback_total | 18002 ± 8486 | 47997 ± 8467 | 0.00 ± 0.00 |
| app.detection_skipped_total | 0.00 ± 0.00 | 17979 ± 8467 | 0.00 ± 0.00 |
| app.hikaricp_connections_active | 0.00 ± 0.00 | 0.00 ± 3.20 | 0.00 ± 0.40 |
| app.hikaricp_connections_pending | 0.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 |
| app.redis_counter_failure_total | 0.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 |
| docker.spike-mysql.cpu_avg_pct | 13.5 ± 28.1 | 772 ± 4.86 | 9.96 ± 6.23 |
| docker.spike-mysql.cpu_max_pct | 29.1 ± 283 | 791 ± 29.5 | 12.6 ± 40.8 |
| docker.spike-redis.cpu_avg_pct | 0.46 ± 0.06 | 0.38 ± 0.09 | 1.48 ± 0.21 |
| docker.spike-redis.cpu_max_pct | 1.19 ± 0.16 | 0.90 ± 0.44 | 2.26 ± 0.91 |
| k6.counter_path_db.avg | 1.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 |
| k6.counter_path_db.max | 1.00 ± 0.00 | 0.00 ± 0.40 | 0.00 ± 0.00 |
| k6.counter_path_db.med | 1.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 |
| k6.counter_path_db.p(50) | 1.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 |
| k6.counter_path_db.p(95) | 1.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 |
| k6.counter_path_db.p(99) | 1.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 |
| k6.droppedIterations | — | 32.0 ± 23.0 | — |
| k6.durationMs.avg | 4.97 ± 0.32 | 102 ± 6.30 | 4.97 ± 1.25 |
| k6.durationMs.max | 97.5 ± 27.8 | 1537 ± 316 | 64.7 ± 91.3 |
| k6.durationMs.p50 | 4.10 ± 0.19 | 9.07 ± 1.43 | 3.97 ± 0.44 |
| k6.durationMs.p95 | 7.97 ± 1.14 | 1009 ± 0.80 | 9.31 ± 4.08 |
| k6.durationMs.p99 | 29.3 ± 5.21 | 1028 ± 11.0 | 25.3 ± 18.3 |
| k6.durationMs{expected_response:true}.avg | 4.97 ± 0.32 | 102 ± 6.30 | 4.97 ± 1.25 |
| k6.durationMs{expected_response:true}.max | 97.5 ± 27.8 | 1537 ± 316 | 64.7 ± 91.3 |
| k6.durationMs{expected_response:true}.p50 | 4.10 ± 0.19 | 9.07 ± 1.43 | 3.97 ± 0.44 |
| k6.durationMs{expected_response:true}.p95 | 7.97 ± 1.14 | 1009 ± 0.80 | 9.31 ± 4.08 |
| k6.durationMs{expected_response:true}.p99 | 29.3 ± 5.21 | 1028 ± 11.0 | 25.3 ± 18.3 |
| k6.failedRate | 0.00 ± 0.00 | 0.00 ± 0.00 | 0.00 ± 0.00 |
| k6.requests | 6006 ± 0.40 | 6005 ± 21.3 | 6006 ± 0.40 |
| k6.rps | 100 ± 0.04 | 98.5 ± 0.39 | 100 ± 0.01 |
