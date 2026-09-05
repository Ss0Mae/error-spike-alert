-- 실험 B db-noindex: received_at 범위 인덱스 3개를 모두 제거해 "시간 범위 인덱스가 없는 DB COUNT" 를 재현한다.
-- (fallback 전용 인덱스만 지우면 옵티마이저가 (project_id, environment, received_at) 로 우회해 full scan 이 되지 않는다 — 임시 MySQL 에서 확인.)
ALTER TABLE error_events
  DROP INDEX idx_ee_project_env_fp_received,
  DROP INDEX idx_ee_project_env_received,
  DROP INDEX idx_ee_project_received;
