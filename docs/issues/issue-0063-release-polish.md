# Issue #63: v0.2 Product and Release Polish

## Scope

Bring the published v0.1 Foundation and v0.2 MVP to a coherent follow-up pre-release level. The work
fixes v0.2 authorization and recovery defects, aligns release records, and completes the operational
workspace experience. It does not implement v0.3+ production providers, Milvus, Kafka deployment,
Workflow, Connector, high availability, or backup/restore.

## Acceptance

- Release status, README, Roadmap, UI design, API contract, and implementation agree.
- Knowledge processing requires OWNER/EDITOR and supports explicit retry or in-memory vector rebuild.
- PDF is identified as stored-only before a user attempts v0.2 processing.
- Overview exposes processing health; Chat can restore an interrupted question.
- Every user can inspect current identity; admin controls remain permission-gated.
- Desktop and mobile layouts have readable, accessible, stable operational states without horizontal
  table scrolling on small screens.
- Java, frontend, Python, documentation, Compose smoke, dependency audit, and HIGH/CRITICAL security
  gates pass before review.

## OEP Classification

This is a defect and release-hardening change. The additive retry endpoint is documented in the
existing Knowledge Sub-SDD and OpenAPI v1 contract. It does not alter persistence, event schemas, SDK,
or SPI, so a new RFC or ADR is not required.
