-- 에러 급증 감지 시스템 DDL (MySQL 8). JPA ddl-auto=validate 로 검증된다.
CREATE TABLE IF NOT EXISTS projects (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    api_key     VARCHAR(64)  NOT NULL,
    created_at  DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_projects_api_key (api_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS error_events (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    project_id      BIGINT        NOT NULL,
    environment     VARCHAR(16)   NOT NULL,
    fingerprint     VARCHAR(64)   NOT NULL,
    error_type      VARCHAR(255)  NOT NULL,
    message         VARCHAR(2000) NULL,
    stack_trace     MEDIUMTEXT    NULL,
    occurred_at     DATETIME(3)   NOT NULL,
    received_at     DATETIME(3)   NOT NULL,
    event_id        VARCHAR(36)   NOT NULL,
    request_id      VARCHAR(64)   NULL,
    trace_id        VARCHAR(64)   NULL,
    server_instance VARCHAR(100)  NULL,
    metadata        JSON          NULL,
    created_at      DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ee_project_event (project_id, event_id),
    KEY idx_ee_project_received (project_id, received_at),
    KEY idx_ee_project_env_received (project_id, environment, received_at),
    KEY idx_ee_project_env_fp_received (project_id, environment, fingerprint, received_at),
    KEY idx_ee_request (request_id),
    CONSTRAINT fk_ee_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS alert_policies (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    project_id         BIGINT       NOT NULL,
    environment        VARCHAR(16)  NOT NULL,
    scope              VARCHAR(16)  NOT NULL,
    target_fingerprint VARCHAR(64)  NULL,
    window_seconds     INT          NOT NULL,
    threshold          INT          NOT NULL,
    cooldown_seconds   INT          NOT NULL,
    channel            VARCHAR(16)  NOT NULL,
    webhook_url        VARCHAR(500) NOT NULL,
    enabled            TINYINT(1)   NOT NULL DEFAULT 1,
    created_at         DATETIME(3)  NOT NULL,
    updated_at         DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ap_project (project_id, enabled),
    CONSTRAINT fk_ap_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS alert_histories (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    alert_policy_id   BIGINT       NOT NULL,
    project_id        BIGINT       NOT NULL,
    fingerprint       VARCHAR(64)  NULL,
    detected_count    INT          NOT NULL,
    detected_at       DATETIME(3)  NOT NULL,
    window_started_at DATETIME(3)  NOT NULL,
    window_ended_at   DATETIME(3)  NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    attempt_count     INT          NOT NULL DEFAULT 0,
    sent_at           DATETIME(3)  NULL,
    failure_reason    VARCHAR(500) NULL,
    dedup_key         VARCHAR(160) NOT NULL,
    detection_path    VARCHAR(16)  NOT NULL,
    trigger_event_id  BIGINT       NULL,
    created_at        DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ah_dedup (dedup_key),
    KEY idx_ah_policy_detected (alert_policy_id, detected_at),
    KEY idx_ah_project_detected (project_id, detected_at),
    CONSTRAINT fk_ah_policy FOREIGN KEY (alert_policy_id) REFERENCES alert_policies (id),
    CONSTRAINT fk_ah_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
