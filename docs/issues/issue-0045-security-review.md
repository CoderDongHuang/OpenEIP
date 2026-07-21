# Security Review: Issue #45 OCR

> Status: Passed
> Last updated: 2026-07-22

| Threat | Control | Verification | Result |
|---|---|---|---|
| Unauthenticated internal call | Non-empty configured token, constant-time comparison, fail closed when absent | FastAPI tests cover wrong and missing server configuration | Passed |
| Identity/header spoofing | Canonical non-nil tenant/user UUIDs; bounded request ID allowlist; identity never accepted from body | API tests cover malformed/nil identity and unsafe request ID normalization | Passed |
| MIME/container confusion | PNG/JPEG allowlist plus decoder-reported format equality | Decoder/API mismatch and unsupported media tests | Passed |
| Decompression bomb/resource exhaustion | Streamed 5 MiB cap, 10k dimension cap, 20M pixel cap, one frame, Pillow bomb warning/error mapping | Byte, decoded-pixel, APNG and simulated decompression-bomb tests | Passed |
| Malformed/native decoder input | Full `load()` before Provider call; stable errors omit decoder internals; truncated loading disabled | Empty, malformed, transparent and valid PNG/JPEG tests | Passed |
| OCR hallucination | Bounded documented alphabet; confidence threshold emits `?` instead of silently inventing unsupported glyphs | Exact PNG/JPEG corpus and low-confidence regression tests | Passed |
| Prompt injection propagation | OCR result is explicitly untrusted document data; API has no system/developer Prompt field | Sub-SDD, PRD and architecture review enforce downstream data boundary | Passed |
| Secret/content disclosure | Result excludes token, tenant/user identity and image bytes; error request ID is normalized | Response assertions and Trivy secret scan | Passed |
| Vulnerable runtime | Python 3.12.13 slim digest; repository and complete container scans | Trivy 0.72 found 0 HIGH/CRITICAL after replacing the old Debian 12.9 base image | Passed |

## Remediated Finding

The first runtime scan found 41 fixable HIGH/CRITICAL Debian findings in the previously pinned
`python:3.12.8-slim` image. The base was upgraded to the immutable Python 3.12.13 slim digest (Debian
13.6); the identical Trivy policy then reported zero OS and Python-package findings. CI now builds and
scans the Python image so this class of regression blocks future PRs.

## Residual Risk

- The static internal token is an MVP single-environment control. Multi-host production deployment
  requires rotation and workload identity or mTLS.
- Nginx currently routes the `/ai/` prefix to Python. The token makes OCR fail closed, but production
  ingress should additionally deny direct public access to internal execution paths.
- The deterministic Provider is deliberately not a production OCR accuracy model. PaddleOCR or another
  adapter needs language/layout quality, model provenance, adversarial image and GPU resource testing.
- Pillow remains a native image-decoding boundary and must stay patched; byte/pixel/frame limits reduce
  but cannot eliminate future decoder vulnerabilities.
- Docusaurus development tooling retains 18 transitive moderate `uuid` advisories with no upstream fix;
  the enforced HIGH/CRITICAL policy passes.

## Conclusion

No blocking authentication, identity, image resource, content disclosure, prompt-boundary, secret, or
HIGH/CRITICAL runtime risk remains inside the #45 MVP scope.

