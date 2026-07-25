CREATE TABLE connector_instances (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    connector_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    config_json JSON NOT NULL,
    credential_ref VARCHAR(255) NULL,
    last_health_at TIMESTAMP(6) NULL,
    last_error VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_connector_owner_name (tenant_id, owner_id, name),
    KEY idx_connector_tenant_status (tenant_id, status, updated_at),
    CONSTRAINT ck_connector_type CHECK (connector_type IN (
        'MYSQL','POSTGRESQL','ORACLE','SAP','REDIS','KAFKA','GITHUB','GITLAB',
        'FEISHU','WECOM','JIRA','CONFLUENCE','MINIO','OSS','EMAIL','WEBHOOK'
    )),
    CONSTRAINT ck_connector_status CHECK (status IN ('ACTIVE','PAUSED','ERROR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE connector_operations (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    connector_id VARCHAR(36) NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    operation_type VARCHAR(24) NOT NULL,
    status VARCHAR(16) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    result_json JSON NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_connector_operation_idempotency (
        tenant_id, connector_id, operation_type, idempotency_key
    ),
    KEY idx_connector_operation_connector (tenant_id, connector_id, started_at),
    KEY idx_connector_operation_correlation (tenant_id, correlation_id),
    CONSTRAINT fk_connector_operation_instance FOREIGN KEY (connector_id)
        REFERENCES connector_instances (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE connector_webhook_deliveries (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    connector_id VARCHAR(36) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    headers_json JSON NOT NULL,
    payload_json JSON NOT NULL,
    signature_valid BOOLEAN NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_connector_webhook_event (tenant_id, connector_id, event_id),
    KEY idx_connector_webhook_received (tenant_id, connector_id, received_at),
    CONSTRAINT fk_connector_webhook_instance FOREIGN KEY (connector_id)
        REFERENCES connector_instances (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
