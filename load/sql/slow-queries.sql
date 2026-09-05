-- docker-compose 의 MySQL 은 slow_query_log=1, long_query_time=0.05, log_output=TABLE 로 기동된다.
SELECT start_time, query_time, lock_time, rows_sent, rows_examined, LEFT(CONVERT(sql_text USING utf8mb4), 200) AS sql_text
FROM mysql.slow_log ORDER BY start_time DESC LIMIT 50;
-- 초기화: TRUNCATE mysql.slow_log;
