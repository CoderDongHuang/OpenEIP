# Knowledge Base Test Plan

> Issue: #47 | Gate: coverage >= 80%, transition P99 < 50 ms

| Layer | Coverage |
|---|---|
| Unit | validation, roles, state transitions, replay and collision handling |
| API | CRUD, attach/list/status/delete, pagination, anonymous and IDOR cases |
| Integration | H2 transaction rollback and concurrent uniqueness behavior |
| Contract | OpenAPI/Event Schema, MySQL migration constraints, rollback |
| Benchmark | warmup plus 1,000 persisted state transitions; P50/P95/P99 JSON evidence |
| Security | tenant/member isolation, state bypass, forged/replayed event, bounded diagnostics |

An embedded Kafka integration test verifies a valid parsed event reaches the transactional handler and
an invalid event is attempted three times before appearing on the same-partition `.DLQ` topic.
