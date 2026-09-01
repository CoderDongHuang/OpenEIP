# Governance Management Workspace

> Version: 0.7 | Issue: #99 | Status: Accepted for architecture review

The Governance workspace is an administrative view over the Java `/api/v2/governance` API. It uses the existing
OpenEIP shell, compact operational typography, visible focus, keyboard navigation, reduced motion, and stable
table dimensions. It never calls Python, MySQL, Kafka, or provider endpoints directly.

## 1. Surfaces

| Surface | Workflow | Required states and controls |
|---|---|---|
| Tenant and membership | inspect tenant policy, review members, assign roles, suspend tenant | server-derived scope, role matrix, revision conflict, suspend confirmation |
| Audit explorer | filter actions/resources/outcomes, inspect chain evidence, verify range | cursor pagination, bounded summary, verification result, integrity warning |
| Model registry | register policy metadata, review capability, enable/suspend/deprecate | secret reference only, capability list, pricing snapshot, lifecycle confirmation |
| Prompt registry | create version, submit review, attach evaluation, publish or rollback | immutable version/digest, review gate, evaluation evidence, rollback reason |
| Usage and budgets | inspect usage/cost, create budget, view decisions and threshold alerts | currency/unit totals, window status, quota rejection, idempotent retry state |
| Trace explorer | inspect bounded request/trace links across modules | safe IDs, duration/outcome, missing-span warning, no content payload |

## 2. Navigation and permissions

The navigation exposes only surfaces allowed by the authenticated membership. Tenant scope is displayed as
server-provided context metadata and is not an editable selector in mutation requests. Permission checks are
repeated by Java for every command.

| Capability | Viewer | Operator | Governance Admin | Platform Admin |
|---|---:|---:|---:|---:|
| Read tenant/audit/trace metadata | yes | yes | yes | audited system scope |
| Verify audit chain | no | yes | yes | yes |
| Manage memberships and budgets | no | no | yes | yes |
| Register or publish model/Prompt | no | no | yes | yes |
| Suspend tenant/provider/model | no | no | yes | audited system scope |
| Export governance evidence | no | scoped | yes | audited system scope |

## 3. Interaction and data rules

- Mutations use `If-Match` and a unique `Idempotency-Key`; conflicts preserve local edits and offer refresh/compare.
- Published model and Prompt versions show an immutable banner. The UI renders IDs and digests, not secret values
  or unrestricted Prompt content in audit/trace inspectors.
- Dangerous operations use explicit text and confirmation dialogs. Controls remain disabled only for the submitted
  command and render the authoritative server response after reconnect.
- Tables use server cursor pagination, stable columns, announced sorting/filtering, and fixed status/icon columns.
- Error states distinguish unauthorized, not-found, stale revision, quota/budget denial, validation failure,
  network retry, and audit integrity failure.

## 4. Layouts

Desktop uses a three-region administrative workspace: navigation rail, selected resource table/timeline, and a
bounded inspector. Narrow layouts stack these regions while keeping the current resource state and primary action
visible. Audit and trace inspectors omit unknown fields rather than interpolating them as HTML.

The audit inspector shows action, resource reference, outcome, request/trace IDs, policy/schema versions, time,
hash status, and an allowlisted summary. The usage inspector shows units, cost, pricing snapshot, budget window,
and source references. Neither view renders credentials, private reasoning, raw provider payloads, raw Tool values,
or full Prompt content from audit/trace data.

## 5. Accessibility and compatibility

All status signals have text in addition to color, focus order follows navigation, dialogs trap focus, and tables
announce loading and empty states. The workspace consumes additive v2 contracts and does not change the existing
v1 Agent, Workflow, Knowledge, Connector, or Chat routes. Playwright coverage must include desktop/mobile widths,
tenant boundary errors, stale revision, publication gate, budget denial, and audit verification failure.
