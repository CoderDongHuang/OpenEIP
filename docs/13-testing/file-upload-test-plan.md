# File Upload Test Plan

| Layer | Required evidence |
|---|---|
| Unit | name/media validation, max bytes, SHA-256, safe storage path, compensation, ownership |
| Integration | JWT-authenticated multipart upload/list/details/content/delete and standard errors |
| MySQL contract | forward migration, constraints/indexes, repository queries, rollback |
| API contract | all OpenAPI paths/methods and file streaming response |
| Event contract | valid and invalid `document.file.uploaded` envelopes against JSON Schema |
| Benchmark | 1 MiB local-storage upload, warmup plus measured P50/P95/P99 |
| Security | traversal, control filename, MIME mismatch, oversized/empty input, IDOR, header injection |

The module gate is instruction coverage >= 80%, benchmark P99 < 250 ms on the recorded machine, no
blocking static-analysis finding, and no HIGH/CRITICAL repository or runtime vulnerability.

