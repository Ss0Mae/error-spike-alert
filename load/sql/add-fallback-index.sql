-- 실험 B 종료 후 복원 (schema.sql 과 동일 정의)
ALTER TABLE error_events
  ADD INDEX idx_ee_project_received (project_id, received_at),
  ADD INDEX idx_ee_project_env_received (project_id, environment, received_at),
  ADD INDEX idx_ee_project_env_fp_received (project_id, environment, fingerprint, received_at);
