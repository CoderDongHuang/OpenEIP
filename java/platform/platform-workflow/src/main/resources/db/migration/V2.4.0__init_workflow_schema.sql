CREATE TABLE workflow_definitions (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    draft_revision BIGINT NOT NULL DEFAULT 0,
    published_version INT NULL,
    draft_graph_json LONGTEXT NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_owner_name (tenant_id, owner_id, name),
    KEY idx_workflow_tenant_updated (tenant_id, updated_at),
    KEY idx_workflow_tenant_status (tenant_id, status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_members (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    workflow_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    member_role VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_member (tenant_id, workflow_id, user_id),
    KEY idx_workflow_member_user (tenant_id, user_id, workflow_id),
    CONSTRAINT fk_workflow_member_definition FOREIGN KEY (workflow_id)
        REFERENCES workflow_definitions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_versions (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    workflow_id VARCHAR(36) NOT NULL,
    version_number INT NOT NULL,
    graph_sha256 VARCHAR(64) NOT NULL,
    graph_json LONGTEXT NOT NULL,
    published_by VARCHAR(36) NOT NULL,
    published_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_version (tenant_id, workflow_id, version_number),
    KEY idx_workflow_version_hash (tenant_id, workflow_id, graph_sha256),
    CONSTRAINT fk_workflow_version_definition FOREIGN KEY (workflow_id)
        REFERENCES workflow_definitions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_triggers (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    workflow_id VARCHAR(36) NOT NULL,
    trigger_type VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    config_json TEXT NOT NULL,
    secret_hash VARCHAR(64) NULL,
    next_fire_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_workflow_trigger_definition (tenant_id, workflow_id, created_at),
    KEY idx_workflow_trigger_due (trigger_type, enabled, next_fire_at),
    CONSTRAINT fk_workflow_trigger_definition FOREIGN KEY (workflow_id)
        REFERENCES workflow_definitions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_executions (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    workflow_id VARCHAR(36) NOT NULL,
    workflow_version INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    trigger_type VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    input_json LONGTEXT NOT NULL,
    current_sequence BIGINT NOT NULL DEFAULT 0,
    failure_code VARCHAR(16) NULL,
    resume_at TIMESTAMP(6) NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_execution_idempotency (tenant_id, workflow_id, idempotency_key),
    KEY idx_workflow_execution_list (tenant_id, workflow_id, created_at),
    KEY idx_workflow_execution_due (status, resume_at, updated_at),
    CONSTRAINT fk_workflow_execution_definition FOREIGN KEY (workflow_id)
        REFERENCES workflow_definitions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_node_executions (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    execution_id VARCHAR(36) NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    iteration_number INT NOT NULL DEFAULT 0,
    attempt_number INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    invocation_id VARCHAR(96) NOT NULL,
    failure_code VARCHAR(16) NULL,
    output_json LONGTEXT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_node_attempt (tenant_id, execution_id, node_id, iteration_number, attempt_number),
    KEY idx_workflow_node_execution (tenant_id, execution_id, created_at),
    CONSTRAINT fk_workflow_node_execution FOREIGN KEY (execution_id)
        REFERENCES workflow_executions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_approvals (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    execution_id VARCHAR(36) NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    assignees_json TEXT NOT NULL,
    decision_mode VARCHAR(8) NOT NULL,
    decided_by VARCHAR(36) NULL,
    decision VARCHAR(16) NULL,
    comment VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    decided_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_approval_node (tenant_id, execution_id, node_id),
    KEY idx_workflow_approval_status (tenant_id, status, created_at),
    CONSTRAINT fk_workflow_approval_execution FOREIGN KEY (execution_id)
        REFERENCES workflow_executions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_approval_decisions (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    approval_id VARCHAR(36) NOT NULL,
    assignee_id VARCHAR(36) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    comment VARCHAR(1000) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    decided_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_approval_assignee (tenant_id, approval_id, assignee_id),
    UNIQUE KEY uk_workflow_approval_idempotency (tenant_id, approval_id, idempotency_key),
    CONSTRAINT fk_workflow_decision_approval FOREIGN KEY (approval_id)
        REFERENCES workflow_approvals (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_events (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    execution_id VARCHAR(36) NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    node_id VARCHAR(64) NULL,
    data_json TEXT NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_event_sequence (tenant_id, execution_id, sequence_number),
    KEY idx_workflow_event_history (tenant_id, execution_id, occurred_at),
    CONSTRAINT fk_workflow_event_execution FOREIGN KEY (execution_id)
        REFERENCES workflow_executions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_outbox (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    delivered_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_outbox_event (tenant_id, event_id),
    KEY idx_workflow_outbox_pending (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_processed_events (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_fingerprint VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_processed_event (tenant_id, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
