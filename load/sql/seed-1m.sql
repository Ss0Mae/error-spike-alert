-- seed 1000000 rows into error_events (project 1, 4 envs, 200 fingerprints, last 24h, 1KB stack every 10th row)
SET SESSION cte_max_recursion_depth = 1000000;
SET SESSION unique_checks = 0;
SET SESSION foreign_key_checks = 0;
INSERT INTO error_events (project_id, environment, fingerprint, error_type, message, stack_trace, occurred_at, received_at, event_id, request_id, trace_id, server_instance, metadata, created_at)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 250000)
SELECT 1,
       ELT(n % 4 + 1, 'LOCAL', 'DEV', 'STAGING', 'PRODUCTION'),
       CONCAT('seed-fp-', n % 200),
       ELT(n % 5 + 1, 'java.lang.NullPointerException', 'java.lang.IllegalStateException', 'org.springframework.dao.DataAccessException', 'java.net.SocketTimeoutException', 'java.lang.IllegalArgumentException'),
       CONCAT('seed message ', n % 50),
       IF(n % 10 = 0, REPEAT(CONCAT('\tat com.example.seed.Service.method(Service.java:', n % 300, ')\n'), 20), NULL),
       ts - INTERVAL 50000 MICROSECOND,
       ts,
       CONCAT('seed-', n),
       CONCAT('req-', n % 100000),
       CONCAT('trace-', n % 100000),
       CONCAT('api-', n % 8),
       NULL,
       ts
FROM (SELECT n, UTC_TIMESTAMP(3) - INTERVAL (n % 86400) SECOND - INTERVAL (n % 1000) MICROSECOND AS ts FROM seq) s;
INSERT INTO error_events (project_id, environment, fingerprint, error_type, message, stack_trace, occurred_at, received_at, event_id, request_id, trace_id, server_instance, metadata, created_at)
WITH RECURSIVE seq AS (SELECT 250001 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 500000)
SELECT 1,
       ELT(n % 4 + 1, 'LOCAL', 'DEV', 'STAGING', 'PRODUCTION'),
       CONCAT('seed-fp-', n % 200),
       ELT(n % 5 + 1, 'java.lang.NullPointerException', 'java.lang.IllegalStateException', 'org.springframework.dao.DataAccessException', 'java.net.SocketTimeoutException', 'java.lang.IllegalArgumentException'),
       CONCAT('seed message ', n % 50),
       IF(n % 10 = 0, REPEAT(CONCAT('\tat com.example.seed.Service.method(Service.java:', n % 300, ')\n'), 20), NULL),
       ts - INTERVAL 50000 MICROSECOND,
       ts,
       CONCAT('seed-', n),
       CONCAT('req-', n % 100000),
       CONCAT('trace-', n % 100000),
       CONCAT('api-', n % 8),
       NULL,
       ts
FROM (SELECT n, UTC_TIMESTAMP(3) - INTERVAL (n % 86400) SECOND - INTERVAL (n % 1000) MICROSECOND AS ts FROM seq) s;
INSERT INTO error_events (project_id, environment, fingerprint, error_type, message, stack_trace, occurred_at, received_at, event_id, request_id, trace_id, server_instance, metadata, created_at)
WITH RECURSIVE seq AS (SELECT 500001 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 750000)
SELECT 1,
       ELT(n % 4 + 1, 'LOCAL', 'DEV', 'STAGING', 'PRODUCTION'),
       CONCAT('seed-fp-', n % 200),
       ELT(n % 5 + 1, 'java.lang.NullPointerException', 'java.lang.IllegalStateException', 'org.springframework.dao.DataAccessException', 'java.net.SocketTimeoutException', 'java.lang.IllegalArgumentException'),
       CONCAT('seed message ', n % 50),
       IF(n % 10 = 0, REPEAT(CONCAT('\tat com.example.seed.Service.method(Service.java:', n % 300, ')\n'), 20), NULL),
       ts - INTERVAL 50000 MICROSECOND,
       ts,
       CONCAT('seed-', n),
       CONCAT('req-', n % 100000),
       CONCAT('trace-', n % 100000),
       CONCAT('api-', n % 8),
       NULL,
       ts
FROM (SELECT n, UTC_TIMESTAMP(3) - INTERVAL (n % 86400) SECOND - INTERVAL (n % 1000) MICROSECOND AS ts FROM seq) s;
INSERT INTO error_events (project_id, environment, fingerprint, error_type, message, stack_trace, occurred_at, received_at, event_id, request_id, trace_id, server_instance, metadata, created_at)
WITH RECURSIVE seq AS (SELECT 750001 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 1000000)
SELECT 1,
       ELT(n % 4 + 1, 'LOCAL', 'DEV', 'STAGING', 'PRODUCTION'),
       CONCAT('seed-fp-', n % 200),
       ELT(n % 5 + 1, 'java.lang.NullPointerException', 'java.lang.IllegalStateException', 'org.springframework.dao.DataAccessException', 'java.net.SocketTimeoutException', 'java.lang.IllegalArgumentException'),
       CONCAT('seed message ', n % 50),
       IF(n % 10 = 0, REPEAT(CONCAT('\tat com.example.seed.Service.method(Service.java:', n % 300, ')\n'), 20), NULL),
       ts - INTERVAL 50000 MICROSECOND,
       ts,
       CONCAT('seed-', n),
       CONCAT('req-', n % 100000),
       CONCAT('trace-', n % 100000),
       CONCAT('api-', n % 8),
       NULL,
       ts
FROM (SELECT n, UTC_TIMESTAMP(3) - INTERVAL (n % 86400) SECOND - INTERVAL (n % 1000) MICROSECOND AS ts FROM seq) s;
SET SESSION unique_checks = 1;
SET SESSION foreign_key_checks = 1;
ANALYZE TABLE error_events;
SELECT COUNT(*) AS seeded FROM error_events;
