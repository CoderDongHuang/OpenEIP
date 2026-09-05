CREATE TABLE governance_tenants (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    slug VARCHAR(128) NOT NULL,
    state VARCHAR(16) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_tenant_scope (tenant_id, id),
    UNIQUE KEY uk_governance_tenant_slug (slug),
    CONSTRAINT ck_governance_tenant_state CHECK (state IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_organizations (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(128) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_organization_scope (tenant_id, id),
    UNIQUE KEY uk_governance_organization_name (tenant_id, name),
    CONSTRAINT fk_governance_organization_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_memberships (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    organization_id VARCHAR(36) NULL,
    principal_id VARCHAR(36) NOT NULL,
    roles_json TEXT NOT NULL,
    state VARCHAR(16) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_membership_scope (tenant_id, id),
    UNIQUE KEY uk_governance_membership_principal (tenant_id, principal_id),
    KEY idx_governance_membership_lookup (tenant_id, principal_id, state),
    CONSTRAINT fk_governance_membership_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id),
    CONSTRAINT fk_governance_membership_organization FOREIGN KEY (tenant_id, organization_id)
        REFERENCES governance_organizations (tenant_id, id),
    CONSTRAINT ck_governance_membership_state CHECK (state IN ('ACTIVE', 'SUSPENDED', 'REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_quota_policies (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(128) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    token_limit BIGINT NULL,
    cost_limit DECIMAL(20, 6) NULL,
    request_limit BIGINT NULL,
    concurrency_limit INT NULL,
    window_type VARCHAR(16) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_quota_scope (tenant_id, id),
    UNIQUE KEY uk_governance_quota_name (tenant_id, name),
    KEY idx_governance_quota_window (tenant_id, window_type, revision),
    CONSTRAINT fk_governance_quota_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id),
    CONSTRAINT ck_governance_quota_window CHECK (window_type IN ('DAILY', 'WEEKLY', 'MONTHLY', 'EXECUTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_quota_reservations (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    quota_policy_id VARCHAR(36) NOT NULL,
    execution_id VARCHAR(36) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    window_type VARCHAR(16) NOT NULL,
    window_start TIMESTAMP(6) NOT NULL,
    window_end TIMESTAMP(6) NOT NULL,
    requested_token_units BIGINT NOT NULL,
    requested_cost_amount DECIMAL(20, 6) NOT NULL,
    requested_request_units INT NOT NULL DEFAULT 1,
    requested_concurrency_units INT NOT NULL,
    observed_token_units BIGINT NOT NULL,
    observed_cost_amount DECIMAL(20, 6) NOT NULL,
    observed_request_units BIGINT NOT NULL,
    observed_concurrency_units INT NOT NULL,
    decision VARCHAR(16) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    released_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_quota_reservation_scope (tenant_id, id),
    UNIQUE KEY uk_governance_quota_reservation_idempotency
        (tenant_id, quota_policy_id, idempotency_key),
    KEY idx_governance_quota_reservation_window (tenant_id, quota_policy_id, window_start, decision, expires_at),
    KEY idx_governance_quota_reservation_execution (tenant_id, quota_policy_id, execution_id, window_start, decision),
    CONSTRAINT fk_governance_quota_reservation_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id),
    CONSTRAINT fk_governance_quota_reservation_policy FOREIGN KEY (tenant_id, quota_policy_id)
        REFERENCES governance_quota_policies (tenant_id, id),
    CONSTRAINT ck_governance_quota_reservation_window
        CHECK (window_type IN ('DAILY', 'WEEKLY', 'MONTHLY', 'EXECUTION') AND window_start < window_end),
    CONSTRAINT ck_governance_quota_reservation_requested
        CHECK (requested_token_units >= 0 AND requested_cost_amount >= 0
            AND requested_request_units = 1
            AND requested_concurrency_units IN (0, 1)),
    CONSTRAINT ck_governance_quota_reservation_observed
        CHECK (observed_token_units >= 0 AND observed_cost_amount >= 0
            AND observed_request_units >= 0 AND observed_concurrency_units >= 0),
    CONSTRAINT ck_governance_quota_reservation_decision CHECK (decision IN ('ALLOW', 'DENY')),
    CONSTRAINT ck_governance_quota_reservation_lease CHECK (expires_at > created_at),
    CONSTRAINT ck_governance_quota_reservation_release
        CHECK (released_at IS NULL OR (decision = 'ALLOW' AND released_at >= created_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_audit_records (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    principal_id VARCHAR(36) NOT NULL,
    action VARCHAR(96) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(32) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    previous_hash CHAR(64) NULL,
    record_hash CHAR(64) NOT NULL,
    summary_json TEXT NOT NULL,
    retention_deadline TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_audit_event (tenant_id, event_id),
    UNIQUE KEY uk_governance_audit_scope (tenant_id, id),
    KEY idx_governance_audit_list (tenant_id, occurred_at, id),
    KEY idx_governance_audit_retention (tenant_id, retention_deadline, id),
    CONSTRAINT fk_governance_audit_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id),
    CONSTRAINT ck_governance_audit_outcome CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILURE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_outbox (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(96) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    delivered_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_outbox_event (tenant_id, event_id),
    KEY idx_governance_outbox_pending (tenant_id, status, created_at),
    CONSTRAINT fk_governance_outbox_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id),
    CONSTRAINT ck_governance_outbox_status CHECK (status IN ('PENDING', 'DELIVERED', 'QUARANTINED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_providers (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(128) NOT NULL,
    endpoint_policy_json TEXT NOT NULL,
    secret_ref VARCHAR(256) NOT NULL,
    capabilities_json TEXT NOT NULL,
    state VARCHAR(16) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_provider_scope (tenant_id, id),
    UNIQUE KEY uk_governance_provider_name (tenant_id, name),
    CONSTRAINT fk_governance_provider_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id),
    CONSTRAINT ck_governance_provider_state CHECK (state IN ('DRAFT', 'ENABLED', 'SUSPENDED', 'DEPRECATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_models (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    provider_id VARCHAR(36) NOT NULL,
    name VARCHAR(128) NOT NULL,
    state VARCHAR(16) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_model_scope (tenant_id, id),
    UNIQUE KEY uk_governance_model_name (tenant_id, name),
    CONSTRAINT fk_governance_model_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id),
    CONSTRAINT fk_governance_model_provider FOREIGN KEY (tenant_id, provider_id)
        REFERENCES governance_providers (tenant_id, id),
    CONSTRAINT ck_governance_model_state CHECK (state IN ('DRAFT', 'REVIEWED', 'ENABLED', 'SUSPENDED', 'DEPRECATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_model_versions (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    model_id VARCHAR(36) NOT NULL,
    version_number INT NOT NULL,
    content_digest CHAR(71) NOT NULL,
    capabilities_json TEXT NOT NULL,
    routing_labels_json TEXT NOT NULL,
    pricing_snapshot_id VARCHAR(36) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_model_version_scope (tenant_id, id),
    UNIQUE KEY uk_governance_model_version (tenant_id, model_id, version_number),
    KEY idx_governance_model_version_digest (tenant_id, content_digest),
    CONSTRAINT fk_governance_model_version_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id),
    CONSTRAINT fk_governance_model_version_model FOREIGN KEY (tenant_id, model_id)
        REFERENCES governance_models (tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_prompts (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(128) NOT NULL,
    purpose VARCHAR(128) NOT NULL,
    active_publication_id VARCHAR(36) NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_prompt_scope (tenant_id, id),
    UNIQUE KEY uk_governance_prompt_name (tenant_id, name, purpose),
    CONSTRAINT fk_governance_prompt_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_prompt_versions (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    prompt_id VARCHAR(36) NOT NULL,
    version_number INT NOT NULL,
    content_ciphertext LONGTEXT NOT NULL,
    content_digest CHAR(71) NOT NULL,
    compatibility_version VARCHAR(64) NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_prompt_version_scope (tenant_id, id),
    UNIQUE KEY uk_governance_prompt_version (tenant_id, prompt_id, version_number),
    KEY idx_governance_prompt_version_digest (tenant_id, content_digest),
    CONSTRAINT fk_governance_prompt_version_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id),
    CONSTRAINT fk_governance_prompt_version_prompt FOREIGN KEY (tenant_id, prompt_id)
        REFERENCES governance_prompts (tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_prompt_reviews (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    prompt_version_id VARCHAR(36) NOT NULL,
    reviewer_id VARCHAR(36) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reason VARCHAR(512) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    evaluation_run_id VARCHAR(36) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_prompt_review_scope (tenant_id, id),
    CONSTRAINT fk_governance_prompt_review_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id),
    CONSTRAINT fk_governance_prompt_review_version FOREIGN KEY (tenant_id, prompt_version_id)
        REFERENCES governance_prompt_versions (tenant_id, id),
    CONSTRAINT ck_governance_prompt_review_decision CHECK (decision IN ('APPROVE', 'REJECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_prompt_publications (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    prompt_id VARCHAR(36) NOT NULL,
    prompt_version_id VARCHAR(36) NOT NULL,
    content_digest CHAR(71) NOT NULL,
    publication_reason VARCHAR(512) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_prompt_publication_scope (tenant_id, id),
    KEY idx_governance_prompt_publication_active (tenant_id, prompt_id, active, created_at),
    CONSTRAINT fk_governance_prompt_publication_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id),
    CONSTRAINT fk_governance_prompt_publication_prompt FOREIGN KEY (tenant_id, prompt_id)
        REFERENCES governance_prompts (tenant_id, id),
    CONSTRAINT fk_governance_prompt_publication_version FOREIGN KEY (tenant_id, prompt_version_id)
        REFERENCES governance_prompt_versions (tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_pricing_snapshots (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    provider_id VARCHAR(36) NOT NULL,
    model_id VARCHAR(36) NOT NULL,
    version VARCHAR(64) NOT NULL,
    input_unit_price DECIMAL(20, 10) NOT NULL,
    output_unit_price DECIMAL(20, 10) NOT NULL,
    currency CHAR(3) NOT NULL,
    rounding_mode VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_pricing_scope (tenant_id, id),
    UNIQUE KEY uk_governance_pricing_version (tenant_id, provider_id, model_id, version),
    CONSTRAINT fk_governance_pricing_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id),
    CONSTRAINT fk_governance_pricing_provider FOREIGN KEY (tenant_id, provider_id)
        REFERENCES governance_providers (tenant_id, id),
    CONSTRAINT fk_governance_pricing_model FOREIGN KEY (tenant_id, model_id)
        REFERENCES governance_models (tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_usage_records (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    execution_id VARCHAR(36) NOT NULL,
    provider_request_id VARCHAR(128) NOT NULL,
    usage_revision BIGINT NOT NULL,
    pricing_snapshot_id VARCHAR(36) NOT NULL,
    unit_type VARCHAR(32) NOT NULL,
    input_units BIGINT NOT NULL DEFAULT 0,
    output_units BIGINT NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL,
    rounding_mode VARCHAR(32) NOT NULL,
    calculated_amount DECIMAL(20, 6) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(32) NOT NULL,
    source_ref VARCHAR(256) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_usage_scope (tenant_id, id),
    UNIQUE KEY uk_governance_usage_idempotency
        (tenant_id, execution_id, provider_request_id, usage_revision),
    KEY idx_governance_usage_list (tenant_id, execution_id, created_at),
    CONSTRAINT fk_governance_usage_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id),
    CONSTRAINT fk_governance_usage_pricing FOREIGN KEY (tenant_id, pricing_snapshot_id)
        REFERENCES governance_pricing_snapshots (tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_budgets (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(128) NOT NULL,
    currency CHAR(3) NOT NULL,
    limit_amount DECIMAL(20, 6) NOT NULL,
    window_type VARCHAR(16) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_budget_scope (tenant_id, id),
    UNIQUE KEY uk_governance_budget_name (tenant_id, name),
    CONSTRAINT fk_governance_budget_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id),
    CONSTRAINT ck_governance_budget_window CHECK (window_type IN ('DAILY', 'WEEKLY', 'MONTHLY', 'EXECUTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_budget_decisions (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    budget_id VARCHAR(36) NOT NULL,
    execution_id VARCHAR(36) NOT NULL,
    decision_type VARCHAR(16) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    observed_amount DECIMAL(20, 6) NOT NULL,
    reserved_amount DECIMAL(20, 6) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_budget_decision_scope (tenant_id, id),
    KEY idx_governance_budget_decision_execution (tenant_id, execution_id, created_at),
    CONSTRAINT fk_governance_budget_decision_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id),
    CONSTRAINT fk_governance_budget_decision_budget FOREIGN KEY (tenant_id, budget_id)
        REFERENCES governance_budgets (tenant_id, id),
    CONSTRAINT ck_governance_budget_decision_type CHECK (decision_type IN ('START', 'CHECKPOINT')),
    CONSTRAINT ck_governance_budget_decision_result CHECK (decision IN ('ALLOW', 'DENY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_alerts (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    budget_id VARCHAR(36) NOT NULL,
    window_start TIMESTAMP(6) NOT NULL,
    threshold DECIMAL(8, 5) NOT NULL,
    crossing_revision BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_alert_scope (tenant_id, id),
    UNIQUE KEY uk_governance_alert_idempotency
        (tenant_id, budget_id, window_start, threshold, crossing_revision),
    CONSTRAINT fk_governance_alert_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id),
    CONSTRAINT fk_governance_alert_budget FOREIGN KEY (tenant_id, budget_id)
        REFERENCES governance_budgets (tenant_id, id),
    CONSTRAINT ck_governance_alert_status CHECK (status IN ('PENDING', 'SENT', 'ACKNOWLEDGED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_trace_links (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    trace_id VARCHAR(32) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    execution_id VARCHAR(36) NULL,
    module VARCHAR(32) NOT NULL,
    operation VARCHAR(96) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    duration_ms BIGINT NULL,
    safe_attributes_json TEXT NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_trace_scope (tenant_id, id),
    KEY idx_governance_trace_lookup (tenant_id, trace_id, occurred_at, id),
    KEY idx_governance_trace_request (tenant_id, request_id, occurred_at),
    CONSTRAINT fk_governance_trace_tenant FOREIGN KEY (tenant_id)
        REFERENCES governance_tenants (id),
    CONSTRAINT ck_governance_trace_outcome CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILURE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
