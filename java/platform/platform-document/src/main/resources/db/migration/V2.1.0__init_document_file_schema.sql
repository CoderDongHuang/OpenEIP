CREATE TABLE document_files (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    content_type VARCHAR(127) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6) NULL,
    CONSTRAINT pk_document_files PRIMARY KEY (id),
    CONSTRAINT uk_document_files_object_key UNIQUE (object_key),
    CONSTRAINT chk_document_files_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_document_files_tenant_owner_created
    ON document_files (tenant_id, owner_id, created_at);
CREATE INDEX idx_document_files_tenant_status
    ON document_files (tenant_id, status);
