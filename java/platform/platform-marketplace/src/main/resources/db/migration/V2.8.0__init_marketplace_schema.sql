CREATE TABLE marketplace_packages (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    package_type VARCHAR(16) NOT NULL,
    slug VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description VARCHAR(2048) NOT NULL,
    publisher VARCHAR(128) NOT NULL,
    state VARCHAR(16) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_marketplace_package_slug (tenant_id, slug),
    KEY idx_marketplace_package_public (package_type, state, updated_at),
    CONSTRAINT ck_marketplace_package_type CHECK (package_type IN ('PLUGIN', 'CONNECTOR', 'AGENT')),
    CONSTRAINT ck_marketplace_package_state CHECK (state IN ('DRAFT', 'REVIEWED', 'PUBLISHED', 'SUSPENDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE marketplace_package_versions (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    package_id VARCHAR(36) NOT NULL,
    version VARCHAR(32) NOT NULL,
    artifact_uri VARCHAR(1024) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    runtime VARCHAR(64) NOT NULL,
    manifest_json TEXT NOT NULL,
    state VARCHAR(16) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    review_note VARCHAR(512) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    reviewed_at TIMESTAMP(6) NULL,
    published_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_marketplace_package_version (tenant_id, package_id, version),
    KEY idx_marketplace_version_public (package_id, state, version),
    CONSTRAINT fk_marketplace_version_package FOREIGN KEY (tenant_id, package_id)
        REFERENCES marketplace_packages (tenant_id, id),
    CONSTRAINT ck_marketplace_version_state CHECK (state IN ('DRAFT', 'REVIEWED', 'PUBLISHED', 'SUSPENDED')),
    CONSTRAINT ck_marketplace_version_sha CHECK (sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
