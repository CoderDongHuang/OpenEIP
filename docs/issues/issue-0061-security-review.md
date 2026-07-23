# Security Review: Issue #61 Operational Frontend Workspace

> Status: Passed | Last updated: 2026-07-22

| Threat | Control | Verification | Status |
|---|---|---|---|
| Internal credential exposure | Browser calls only same-origin Java APIs; deployment injects the Python token into Java and Python | Built assets contain neither the internal token variable nor test token; browser traffic uses public paths | Passed |
| Resource authorization bypass | Java derives the user from JWT and rechecks file and knowledge-base access before reading content | Service, controller, MySQL contract, and browser tests cover user and admin boundaries | Passed |
| Stored or reflected script injection | React renders external values as text; no raw HTML API is used | Source review and real document/citation rendering through Compose | Passed |
| Destructive action confusion | File, knowledge-base, attachment, and account-status actions require explicit confirmation | Desktop and mobile browser workflows | Passed |
| Prompt/tool argument disclosure | Agent UI receives only sanitized lifecycle events; the allowlist and step bound are explicit | SSE parser tests and browser timeline verification | Passed |
| Upstream response poisoning | Java validates response status, envelope, identity, source type, digest, chunk IDs, counts, and bounds | Local HTTP contract tests cover text, OCR, batching, malformed response, unsupported input, and missing token | Passed |
| Secret or deployment misconfiguration | Source is archived from Git-visible files and scanned outside the slow Windows bind mount | Trivy 0.72.0 secret/misconfiguration scan: zero HIGH/CRITICAL | Passed |
| Vulnerable runtime dependency | Runtime images and application dependencies are scanned independently | Trivy Java, Python, Frontend, and Gateway scans: zero HIGH/CRITICAL; npm frontend and pip audits: zero | Passed |

## Residual Risk

- The synchronous ingestion command is intentionally limited to the single-node deterministic MVP.
  Production providers, retries, cancellation, and horizontal execution require an asynchronous job
  architecture and a new review.
- Tokens remain in browser session storage. A later production UI should adopt an HttpOnly refresh
  cookie and a stricter CSP before handling higher-risk deployments.
- The Docusaurus build dependency tree reports 18 moderate `uuid` findings through the development
  server chain with no upstream fix. It is not part of the application runtime image and does not
  violate the HIGH/CRITICAL release gate; it remains tracked for upstream remediation.

## Decision

No blocking security finding remains in Issue #61 scope. The frontend does not gain internal service
authority, and the new processing command fails closed at the Java boundary.
