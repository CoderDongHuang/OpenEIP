# OCR Test and Benchmark Plan

| Layer | Evidence required |
|---|---|
| Unit | identity/token validation, MIME/container checks, byte/pixel/frame limits, malformed images, glyph recognition |
| Integration | FastAPI success and stable 400/401/413/415/500 envelopes |
| Contract | OpenAPI operation plus OCR result JSON Schema validation |
| Regression | Existing health endpoint and all Java/Frontend/Foundation checks remain compatible |
| Benchmark | fixed high-contrast corpus, warmup plus measured P50/P95/P99 and images/second |
| Security | decompression bomb, malformed container, untrusted recognized text, secret/log disclosure, dependencies |

The module gate is total Python instruction coverage at least 80%, benchmark P99 below 100 ms on the
recorded machine, no HIGH/CRITICAL dependency or repository finding, synchronized API/schema docs, and
no change to existing Java APIs, migrations, SDKs, or public Plugin SPI.

