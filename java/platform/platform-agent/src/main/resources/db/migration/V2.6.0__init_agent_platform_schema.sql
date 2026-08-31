CREATE TABLE agent_definition (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    agent_type VARCHAR(24) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    draft_json LONGTEXT NOT NULL,
    draft_revision BIGINT NOT NULL DEFAULT 0,
    published_version INT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    archived_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_definition_name (tenant_id, name),
    KEY idx_agent_definition_list (tenant_id, status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_version (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(36) NOT NULL,
    version_number INT NULL,
    snapshot_status VARCHAR(16) NOT NULL,
    source_draft_revision BIGINT NOT NULL,
    content_digest VARCHAR(64) NOT NULL,
    config_json LONGTEXT NOT NULL,
    evaluation_run_id VARCHAR(36) NULL,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    published_by VARCHAR(36) NULL,
    published_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_version_number (tenant_id, agent_id, version_number),
    KEY idx_agent_version_digest (tenant_id, content_digest),
    KEY idx_agent_version_status (tenant_id, agent_id, snapshot_status, created_at),
    CONSTRAINT fk_agent_version_definition FOREIGN KEY (agent_id) REFERENCES agent_definition (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tool_definition (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    tool_key VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_definition_key (tenant_id, tool_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tool_version (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    tool_definition_id VARCHAR(36) NOT NULL,
    semantic_version VARCHAR(32) NOT NULL,
    content_digest VARCHAR(64) NOT NULL,
    risk_class VARCHAR(16) NOT NULL,
    idempotency_mode VARCHAR(24) NOT NULL,
    operations_json TEXT NOT NULL,
    input_schema_json LONGTEXT NOT NULL,
    output_schema_json LONGTEXT NOT NULL,
    max_duration_ms INT NOT NULL,
    max_result_bytes INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_version (tenant_id, tool_definition_id, semantic_version),
    KEY idx_tool_version_digest (tenant_id, content_digest),
    CONSTRAINT fk_tool_version_definition FOREIGN KEY (tool_definition_id) REFERENCES tool_definition (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_tool_grant (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    agent_version_id VARCHAR(36) NOT NULL,
    tool_version_id VARCHAR(36) NOT NULL,
    operations_json TEXT NOT NULL,
    resource_selector_json TEXT NOT NULL,
    argument_constraints_json TEXT NOT NULL,
    approval_mode VARCHAR(16) NOT NULL,
    expires_at TIMESTAMP(6) NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_tool_grant (tenant_id, agent_version_id, tool_version_id),
    KEY idx_agent_tool_grant_active (tenant_id, agent_version_id, revoked_at, expires_at),
    CONSTRAINT fk_agent_tool_grant_agent FOREIGN KEY (agent_version_id) REFERENCES agent_version (id),
    CONSTRAINT fk_agent_tool_grant_tool FOREIGN KEY (tool_version_id) REFERENCES tool_version (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_run (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(36) NOT NULL,
    agent_version_id VARCHAR(36) NOT NULL,
    principal_id VARCHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL,
    input_digest VARCHAR(64) NOT NULL,
    resource_handles_json TEXT NOT NULL,
    budget_json TEXT NOT NULL,
    dependency_digest VARCHAR(64) NOT NULL,
    current_sequence BIGINT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 0,
    failure_code VARCHAR(32) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_agent_run_list (tenant_id, principal_id, created_at),
    KEY idx_agent_run_schedule (tenant_id, status, updated_at),
    CONSTRAINT fk_agent_run_definition FOREIGN KEY (agent_id) REFERENCES agent_definition (id),
    CONSTRAINT fk_agent_run_version FOREIGN KEY (agent_version_id) REFERENCES agent_version (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_run_dependency (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    dependency_type VARCHAR(24) NOT NULL,
    dependency_id VARCHAR(255) NOT NULL,
    dependency_version VARCHAR(64) NOT NULL,
    content_digest VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_run_dependency (tenant_id, run_id, dependency_type, dependency_id),
    CONSTRAINT fk_agent_run_dependency_run FOREIGN KEY (run_id) REFERENCES agent_run (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_step (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    step_key VARCHAR(64) NOT NULL,
    objective VARCHAR(1000) NOT NULL,
    step_type VARCHAR(24) NOT NULL,
    status VARCHAR(16) NOT NULL,
    sequence_number INT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_step_key (tenant_id, run_id, step_key),
    KEY idx_agent_step_run (tenant_id, run_id, sequence_number),
    CONSTRAINT fk_agent_step_run FOREIGN KEY (run_id) REFERENCES agent_run (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_attempt (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    step_id VARCHAR(36) NOT NULL,
    attempt_number INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_expires_at TIMESTAMP(6) NULL,
    fencing_token BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_attempt_number (tenant_id, step_id, attempt_number),
    UNIQUE KEY uk_agent_attempt_idempotency (tenant_id, step_id, idempotency_key),
    KEY idx_agent_attempt_lease (tenant_id, status, lease_expires_at),
    CONSTRAINT fk_agent_attempt_step FOREIGN KEY (step_id) REFERENCES agent_step (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_handoff (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    sender_step_id VARCHAR(36) NOT NULL,
    worker_agent_version_id VARCHAR(36) NOT NULL,
    objective VARCHAR(1000) NOT NULL,
    references_json TEXT NOT NULL,
    capability_digest VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    deadline TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    accepted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_agent_handoff_run (tenant_id, run_id, created_at),
    CONSTRAINT fk_agent_handoff_run FOREIGN KEY (run_id) REFERENCES agent_run (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_command (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    command_type VARCHAR(24) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    expected_revision BIGINT NOT NULL,
    requested_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_command_idempotency (tenant_id, run_id, idempotency_key),
    CONSTRAINT fk_agent_command_run FOREIGN KEY (run_id) REFERENCES agent_run (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_checkpoint (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    step_id VARCHAR(36) NULL,
    checkpoint_number INT NOT NULL,
    public_plan_json LONGTEXT NOT NULL,
    safe_references_json TEXT NOT NULL,
    budget_json TEXT NOT NULL,
    content_digest VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_checkpoint_number (tenant_id, run_id, checkpoint_number),
    CONSTRAINT fk_agent_checkpoint_run FOREIGN KEY (run_id) REFERENCES agent_run (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_run_event (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(36) NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    safe_payload_json TEXT NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_run_event_sequence (tenant_id, run_id, sequence_number),
    KEY idx_agent_run_event_history (tenant_id, run_id, occurred_at),
    CONSTRAINT fk_agent_run_event_run FOREIGN KEY (run_id) REFERENCES agent_run (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_outbox (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    delivered_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_outbox_event (tenant_id, event_id),
    KEY idx_agent_outbox_pending (tenant_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_memory_policy (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    purpose VARCHAR(64) NOT NULL,
    retention_seconds BIGINT NOT NULL,
    max_items INT NOT NULL,
    max_bytes BIGINT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_memory_policy_name (tenant_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_memory_entry (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    principal_id VARCHAR(36) NOT NULL,
    agent_id VARCHAR(36) NOT NULL,
    policy_id VARCHAR(36) NOT NULL,
    purpose VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(255) NOT NULL,
    sensitivity VARCHAR(16) NOT NULL,
    consent_basis VARCHAR(64) NULL,
    provenance_json TEXT NOT NULL,
    content_digest VARCHAR(64) NOT NULL,
    encrypted_content LONGBLOB NULL,
    vector_entry_id VARCHAR(64) NULL,
    state VARCHAR(16) NOT NULL,
    retention_deadline TIMESTAMP(6) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_agent_memory_filter (tenant_id, principal_id, purpose, state, retention_deadline),
    KEY idx_agent_memory_retention (tenant_id, retention_deadline, id),
    CONSTRAINT fk_agent_memory_policy FOREIGN KEY (policy_id) REFERENCES agent_memory_policy (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_memory_lineage (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    parent_entry_id VARCHAR(36) NOT NULL,
    derived_entry_id VARCHAR(36) NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_memory_lineage (tenant_id, parent_entry_id, derived_entry_id),
    CONSTRAINT fk_agent_memory_lineage_parent FOREIGN KEY (parent_entry_id) REFERENCES agent_memory_entry (id),
    CONSTRAINT fk_agent_memory_lineage_derived FOREIGN KEY (derived_entry_id) REFERENCES agent_memory_entry (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_memory_purge_job (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    memory_entry_id VARCHAR(36) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    redis_purged_at TIMESTAMP(6) NULL,
    vector_purged_at TIMESTAMP(6) NULL,
    derived_purged_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_memory_purge (tenant_id, memory_entry_id, idempotency_key),
    KEY idx_agent_memory_purge_pending (tenant_id, status, created_at),
    CONSTRAINT fk_agent_memory_purge_entry FOREIGN KEY (memory_entry_id) REFERENCES agent_memory_entry (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mcp_server (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    transport VARCHAR(24) NOT NULL,
    endpoint VARCHAR(2048) NOT NULL,
    auth_type VARCHAR(24) NOT NULL,
    credential_ref VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    disabled_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mcp_server_name (tenant_id, name),
    KEY idx_mcp_server_list (tenant_id, status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mcp_server_version (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    server_id VARCHAR(36) NOT NULL,
    version_number INT NOT NULL,
    policy_digest VARCHAR(64) NOT NULL,
    safe_config_json TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mcp_server_version (tenant_id, server_id, version_number),
    CONSTRAINT fk_mcp_server_version_server FOREIGN KEY (server_id) REFERENCES mcp_server (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mcp_capability (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    server_id VARCHAR(36) NOT NULL,
    external_name VARCHAR(255) NOT NULL,
    capability_type VARCHAR(24) NOT NULL,
    schema_digest VARCHAR(64) NOT NULL,
    schema_json LONGTEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    discovered_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mcp_capability (tenant_id, server_id, external_name),
    KEY idx_mcp_capability_status (tenant_id, server_id, status),
    CONSTRAINT fk_mcp_capability_server FOREIGN KEY (server_id) REFERENCES mcp_server (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mcp_tool_mapping (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    capability_id VARCHAR(36) NOT NULL,
    tool_version_id VARCHAR(36) NOT NULL,
    capability_digest VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mcp_tool_mapping (tenant_id, capability_id, tool_version_id),
    CONSTRAINT fk_mcp_mapping_capability FOREIGN KEY (capability_id) REFERENCES mcp_capability (id),
    CONSTRAINT fk_mcp_mapping_tool FOREIGN KEY (tool_version_id) REFERENCES tool_version (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE eval_dataset (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_eval_dataset_name (tenant_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE eval_dataset_version (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    dataset_id VARCHAR(36) NOT NULL,
    version_number INT NOT NULL,
    content_digest VARCHAR(64) NOT NULL,
    case_count INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_eval_dataset_version (tenant_id, dataset_id, version_number),
    CONSTRAINT fk_eval_dataset_version_dataset FOREIGN KEY (dataset_id) REFERENCES eval_dataset (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE eval_case (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    dataset_version_id VARCHAR(36) NOT NULL,
    case_key VARCHAR(128) NOT NULL,
    agent_type VARCHAR(24) NOT NULL,
    fixture_json LONGTEXT NOT NULL,
    assertions_json LONGTEXT NOT NULL,
    content_digest VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_eval_case_key (tenant_id, dataset_version_id, case_key),
    CONSTRAINT fk_eval_case_dataset_version FOREIGN KEY (dataset_version_id) REFERENCES eval_dataset_version (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE eval_suite (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_eval_suite_name (tenant_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE eval_suite_version (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    suite_id VARCHAR(36) NOT NULL,
    version_number INT NOT NULL,
    dataset_versions_json TEXT NOT NULL,
    gate_policy_json LONGTEXT NOT NULL,
    scorer_versions_json TEXT NOT NULL,
    content_digest VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_eval_suite_version (tenant_id, suite_id, version_number),
    CONSTRAINT fk_eval_suite_version_suite FOREIGN KEY (suite_id) REFERENCES eval_suite (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE eval_run (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    suite_version_id VARCHAR(36) NOT NULL,
    candidate_agent_version_id VARCHAR(36) NOT NULL,
    baseline_agent_version_id VARCHAR(36) NOT NULL,
    repeat_count INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    gate_status VARCHAR(16) NULL,
    environment_digest VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_eval_run_list (tenant_id, created_at),
    CONSTRAINT fk_eval_run_suite FOREIGN KEY (suite_version_id) REFERENCES eval_suite_version (id),
    CONSTRAINT fk_eval_run_candidate FOREIGN KEY (candidate_agent_version_id) REFERENCES agent_version (id),
    CONSTRAINT fk_eval_run_baseline FOREIGN KEY (baseline_agent_version_id) REFERENCES agent_version (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE eval_case_result (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    eval_run_id VARCHAR(36) NOT NULL,
    eval_case_id VARCHAR(36) NOT NULL,
    repeat_number INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    safe_result_json TEXT NOT NULL,
    duration_ms BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_eval_case_result (tenant_id, eval_run_id, eval_case_id, repeat_number),
    KEY idx_eval_case_result_run (tenant_id, eval_run_id, status),
    CONSTRAINT fk_eval_case_result_run FOREIGN KEY (eval_run_id) REFERENCES eval_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_case_result_case FOREIGN KEY (eval_case_id) REFERENCES eval_case (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE eval_metric (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    eval_run_id VARCHAR(36) NOT NULL,
    metric_key VARCHAR(128) NOT NULL,
    metric_value DECIMAL(18,6) NOT NULL,
    sample_count INT NOT NULL,
    confidence_low DECIMAL(18,6) NULL,
    confidence_high DECIMAL(18,6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_eval_metric (tenant_id, eval_run_id, metric_key),
    CONSTRAINT fk_eval_metric_run FOREIGN KEY (eval_run_id) REFERENCES eval_run (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE eval_gate_result (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    eval_run_id VARCHAR(36) NOT NULL,
    gate_key VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    actual_value DECIMAL(18,6) NULL,
    threshold_value DECIMAL(18,6) NULL,
    reason_code VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_eval_gate_result (tenant_id, eval_run_id, gate_key),
    CONSTRAINT fk_eval_gate_run FOREIGN KEY (eval_run_id) REFERENCES eval_run (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
