-- fallback COUNT 실행 계획·실제 시간·examined rows. 인덱스 drop 전후로 각각 실행해 비교한다.
-- 사용: docker exec -i spike-mysql mysql -uroot -proot spike < load/sql/explain-fallback.sql
SET @fp = (SELECT fingerprint FROM error_events WHERE project_id = 1 AND environment = 'PRODUCTION' ORDER BY id DESC LIMIT 1);
SET @since = UTC_TIMESTAMP(3) - INTERVAL 60 SECOND;
SELECT @fp AS fingerprint, @since AS since_utc;

EXPLAIN
SELECT COUNT(*) FROM error_events
 WHERE project_id = 1 AND environment = 'PRODUCTION' AND fingerprint = @fp AND received_at >= @since\G

EXPLAIN ANALYZE
SELECT COUNT(*) FROM error_events
 WHERE project_id = 1 AND environment = 'PRODUCTION' AND fingerprint = @fp AND received_at >= @since\G

FLUSH STATUS;
SELECT /*+ MAX_EXECUTION_TIME(1000) */ COUNT(*) AS cnt FROM error_events
 WHERE project_id = 1 AND environment = 'PRODUCTION' AND fingerprint = @fp AND received_at >= @since;
SHOW SESSION STATUS WHERE Variable_name IN ('Handler_read_key', 'Handler_read_next', 'Handler_read_rnd_next', 'Innodb_rows_read');

-- 두 번째 블록: 시드 데이터(seed-fp-*, 최근 24h 균등 분포)로 examined rows 차이를 크게 보이게 한다. 1M 행 기준 (env, fp) 당 ≈ 1,250 행.
SET @since24 = UTC_TIMESTAMP(3) - INTERVAL 1 DAY;
EXPLAIN ANALYZE
SELECT COUNT(*) FROM error_events
 WHERE project_id = 1 AND environment = 'PRODUCTION' AND fingerprint = 'seed-fp-3' AND received_at >= @since24\G
FLUSH STATUS;
SELECT COUNT(*) AS cnt_24h FROM error_events
 WHERE project_id = 1 AND environment = 'PRODUCTION' AND fingerprint = 'seed-fp-3' AND received_at >= @since24;
SHOW SESSION STATUS WHERE Variable_name IN ('Handler_read_key', 'Handler_read_next', 'Handler_read_rnd_next', 'Innodb_rows_read');
