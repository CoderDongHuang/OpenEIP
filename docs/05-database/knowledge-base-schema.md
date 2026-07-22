# Knowledge Base Database Schema

> Migration: `V2.2.0__init_knowledge_schema.sql`
> Rollback: `U2.2.0__init_knowledge_schema.sql`

## Tables

| Table | Purpose | Key constraints |
|---|---|---|
| `knowledge_bases` | Base metadata | tenant/owner/name unique; soft delete |
| `knowledge_base_members` | Access grants | base/user unique; role enum |
| `knowledge_base_documents` | File association and pipeline state | base/document unique; state enum |
| `knowledge_processed_events` | Durable consumer idempotency | tenant/event unique |

All identifiers are UUID strings. All ownership and lookup indexes begin with `tenant_id`; foreign keys
use `ON DELETE CASCADE`. Deleting a base is a soft delete at the API layer so audit identity remains
stable. The rollback drops only these four tables in dependency order.

`knowledge_base_documents.failure_code` is limited to 64 characters and stores a stable diagnostic
code. It must not contain source text, exception messages, URLs, or secrets. `retry_count` is bounded
from 0 to 3 by application validation.
