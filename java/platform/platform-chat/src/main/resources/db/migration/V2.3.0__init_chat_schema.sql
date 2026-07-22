CREATE TABLE chat_sessions (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    knowledge_base_id VARCHAR(36) NOT NULL,
    title VARCHAR(120) NOT NULL,
    next_message_index BIGINT NOT NULL DEFAULT 0,
    active_request_id VARCHAR(36) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_chat_session_owner_updated (tenant_id, owner_id, updated_at),
    CONSTRAINT fk_chat_session_owner FOREIGN KEY (owner_id)
        REFERENCES auth_users (id),
    CONSTRAINT fk_chat_session_base FOREIGN KEY (knowledge_base_id)
        REFERENCES knowledge_bases (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_messages (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    message_index BIGINT NOT NULL,
    message_role VARCHAR(16) NOT NULL,
    content VARCHAR(8000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_message_order (tenant_id, session_id, message_index),
    KEY idx_chat_message_owner (tenant_id, owner_id, session_id),
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id)
        REFERENCES chat_sessions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
