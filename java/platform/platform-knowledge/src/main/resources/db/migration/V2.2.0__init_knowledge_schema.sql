CREATE TABLE knowledge_bases (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_base_owner_name (tenant_id, owner_id, name),
    KEY idx_knowledge_base_tenant_updated (tenant_id, updated_at),
    KEY idx_knowledge_base_tenant_owner (tenant_id, owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_base_members (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    knowledge_base_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    member_role VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_member (tenant_id, knowledge_base_id, user_id),
    KEY idx_knowledge_member_user (tenant_id, user_id, knowledge_base_id),
    CONSTRAINT fk_knowledge_member_base FOREIGN KEY (knowledge_base_id)
        REFERENCES knowledge_bases (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_base_documents (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    knowledge_base_id VARCHAR(36) NOT NULL,
    document_id VARCHAR(36) NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    failure_code VARCHAR(64) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_document (tenant_id, knowledge_base_id, document_id),
    KEY idx_knowledge_document_status (tenant_id, processing_status, updated_at),
    KEY idx_knowledge_document_source (tenant_id, document_id),
    CONSTRAINT fk_knowledge_document_base FOREIGN KEY (knowledge_base_id)
        REFERENCES knowledge_bases (id) ON DELETE CASCADE,
    CONSTRAINT fk_knowledge_document_file FOREIGN KEY (document_id)
        REFERENCES document_files (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_processed_events (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    resource_key VARCHAR(160) NOT NULL,
    payload_fingerprint VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_event (tenant_id, event_id),
    KEY idx_knowledge_event_resource (tenant_id, resource_key, processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
