INSERT IGNORE INTO projects (id, name, api_key, created_at)
VALUES (1, 'demo-service', 'demo-api-key', UTC_TIMESTAMP(3));

INSERT IGNORE INTO alert_policies
  (id, project_id, environment, scope, target_fingerprint, window_seconds, threshold, cooldown_seconds, channel, webhook_url, enabled, created_at, updated_at)
VALUES
  (1, 1, 'PRODUCTION', 'PER_FINGERPRINT', NULL, 60, 20, 300, 'WEBHOOK', 'http://localhost:8091/webhook', 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
