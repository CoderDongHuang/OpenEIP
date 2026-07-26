# Agent Event Contract v2

Topic family: `openeip.agent.run.v2`, `openeip.agent.memory.v2`, `openeip.agent.mcp.v2`, and
`openeip.agent.evaluation.v2`. Delivery is at least once; consumers deduplicate by `eventId`. Producers write
the transactional outbox with the domain transition.

The envelope requires `eventId`, `eventType`, `schemaVersion`, `occurredAt`, `tenantId`, `aggregateId`,
`aggregateVersion`, `correlationId`, `causationId`, and `data`. `schemaVersion` starts at `2.0`. Unknown additive
fields are ignored; breaking changes require a new major topic/schema.

Run event types are `run.queued`, `run.state.changed`, `plan.published`, `step.started`, `step.completed`,
`step.failed`, `tool.approval.requested`, `tool.started`, `tool.completed`, `handoff.requested`,
`handoff.accepted`, `worker.started`, `worker.completed`, `run.paused`, `run.resumed`, `run.cancel.requested`,
and terminal outcomes. Memory events cover entry accepted/quarantined/tombstoned/purge completed. MCP events
cover registration, discovery completed/failed, capability drift, and mapping suspended. Evaluation events cover
run started/completed and gate passed/failed.

Data contains only IDs, exact versions/digests, status, reason/error codes, sequence, durations, counts, risk
class, and bounded redacted summaries. It never contains prompts, chain-of-thought, credentials, auth headers,
raw Tool arguments/results, Memory content, MCP payloads, SQL text, document text, or evaluation fixture content.
Dead-letter handling follows the SDD: three failed deliveries then `<topic>.dlq`, retaining the safe envelope.
