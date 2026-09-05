// load/sql/seed-1m.sql 생성기. 사용: ROWS=1000000 node load/seed.js && docker exec -i spike-mysql mysql -uroot -proot spike < load/sql/seed-1m.sql
// ponytail: 수백 MB 짜리 다중 행 INSERT 파일 대신 서버 측 재귀 CTE 한 방으로 만든다(1M 행 ≈ 수십 초). 행 단위 커스텀이 필요하면 그때 파일 생성 방식으로.
const fs = require('fs');
const path = require('path');
const ROWS = Number(process.env.ROWS || 1_000_000);
const BATCH = Number(process.env.BATCH || 250_000);   // 한 INSERT 당 행 수 (undo 로그·락 시간 제한)
const sql = [`-- seed ${ROWS} rows into error_events (project 1, 4 envs, 200 fingerprints, last 24h, 1KB stack every 10th row)`,
  'SET SESSION cte_max_recursion_depth = 1000000;', 'SET SESSION unique_checks = 0;', 'SET SESSION foreign_key_checks = 0;', ];
for (let start = 0; start < ROWS; start += BATCH) {
  const n = Math.min(BATCH, ROWS - start);
  sql.push(`INSERT INTO error_events (project_id, environment, fingerprint, error_type, message, stack_trace, occurred_at, received_at, event_id, request_id, trace_id, server_instance, metadata, created_at)
WITH RECURSIVE seq AS (SELECT ${start + 1} AS n UNION ALL SELECT n + 1 FROM seq WHERE n < ${start + n})
SELECT 1,
       ELT(n % 4 + 1, 'LOCAL', 'DEV', 'STAGING', 'PRODUCTION'),
       CONCAT('seed-fp-', n % 200),
       ELT(n % 5 + 1, 'java.lang.NullPointerException', 'java.lang.IllegalStateException', 'org.springframework.dao.DataAccessException', 'java.net.SocketTimeoutException', 'java.lang.IllegalArgumentException'),
       CONCAT('seed message ', n % 50),
       IF(n % 10 = 0, REPEAT(CONCAT('\\tat com.example.seed.Service.method(Service.java:', n % 300, ')\\n'), 20), NULL),
       ts - INTERVAL 50000 MICROSECOND,
       ts,
       CONCAT('seed-', n),
       CONCAT('req-', n % 100000),
       CONCAT('trace-', n % 100000),
       CONCAT('api-', n % 8),
       NULL,
       ts
FROM (SELECT n, UTC_TIMESTAMP(3) - INTERVAL (n % 86400) SECOND - INTERVAL (n % 1000) MICROSECOND AS ts FROM seq) s;`);
}
sql.push('SET SESSION unique_checks = 1;', 'SET SESSION foreign_key_checks = 1;', 'ANALYZE TABLE error_events;', 'SELECT COUNT(*) AS seeded FROM error_events;');
const out = path.join(__dirname, 'sql', 'seed-1m.sql');
fs.writeFileSync(out, sql.join('\n') + '\n');
console.log(`wrote ${out} (${ROWS} rows in ${Math.ceil(ROWS / BATCH)} batches)`);
