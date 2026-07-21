# File Upload Database Design

## Ownership

`platform-document` exclusively owns `document_files`. `owner_id` refers to the Auth user identifier
but deliberately has no physical cross-module foreign key. Access always includes `tenant_id` and,
unless admin, `owner_id`.

## Table

| Column | Type | Constraint | Purpose |
|---|---|---|---|
| `id` | CHAR(36) | PK | Server-generated file UUID |
| `tenant_id` | VARCHAR(64) | NOT NULL | Tenant boundary; `default` in v0.2 |
| `owner_id` | CHAR(36) | NOT NULL | Authenticated uploader |
| `original_name` | VARCHAR(255) | NOT NULL | Sanitized display name |
| `object_key` | VARCHAR(255) | NOT NULL, unique | Server-generated storage address |
| `content_type` | VARCHAR(127) | NOT NULL | Validated declared media type |
| `size_bytes` | BIGINT | NOT NULL | Stored byte count |
| `sha256` | CHAR(64) | NOT NULL | Lowercase content digest |
| `status` | VARCHAR(32) | NOT NULL | `READY` in this module |
| `created_at` | TIMESTAMP(6) | NOT NULL | UTC creation time |
| `updated_at` | TIMESTAMP(6) | NOT NULL | UTC update time |
| `deleted_at` | TIMESTAMP(6) | NULL | Reserved soft-delete timestamp |

Indexes: `uk_document_files_object_key`, `idx_document_files_tenant_owner_created`, and
`idx_document_files_tenant_status`. The forward migration is
`V2.1.0__init_document_file_schema.sql`; rollback is `U2.1.0__init_document_file_schema.sql`.

