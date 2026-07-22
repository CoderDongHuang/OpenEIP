# Security Review: Issue #44 File Upload

> Status: Passed
> Last updated: 2026-07-22

| Threat | Control | Verification | Result |
|---|---|---|---|
| Path traversal / symlink escape | Generated UUID keys, strict key regex, normalized root containment, no-follow checks | Unit tests reject traversal/invalid keys and exercise open/delete containment | Passed |
| Existing-object corruption | `CREATE_NEW`; cleanup only after this invocation successfully created the target | Regression test proved duplicate write preserves the original bytes | Passed |
| Filename/header injection | Reject separators/control characters; RFC 5987 `Content-Disposition`; `nosniff` | Policy and MockMvc tests include quotes, traversal and control characters | Passed |
| MIME confusion | Allowlisted suffix/media pair; downstream parsers still inspect content | Unit/integration tests reject mismatch and executable media | Passed |
| Resource exhaustion | Spring request cap plus storage-stream hard byte cap; no whole-file application buffer | Empty, declared oversize and stream oversize tests; partial object removed | Passed |
| IDOR / cross-user access | Repository query includes tenant and owner before data is returned; admin is explicit authority | H2 lifecycle and service tests verify other owners receive 404 | Passed |
| Partial persistence | Object first, `saveAndFlush` second, DB failure compensation; delete preserves metadata on storage error | Mockito failure injection covers DB and storage failure combinations | Passed |
| Secret/content disclosure | Event/API omit object key, owner and raw bytes; unexpected errors return stable messages | Controller contract asserts object key absent; repository scan includes secret scanner | Passed |
| Vulnerable dependencies | Lock audits plus unpacked Boot JAR `rootfs` scan | Trivy 0.72.0 found 0 HIGH/CRITICAL in repository and 75 runtime JARs | Passed |

## Residual Risk

- The local filesystem adapter is single-node and not a production HA object store.
- A process crash between object write and metadata commit can leave an unreferenced object; production
  storage needs reconciliation after a grace period.
- Antivirus/content disarm, multipart/resumable upload, quotas and distributed rate limits are not part
  of #44 and must be completed before accepting untrusted public internet uploads.
- Website audit reports 18 moderate transitive `uuid` findings through Docusaurus dev tooling with no
  upstream fix. The enforced HIGH/CRITICAL gate passes; this moderate advisory remains tracked.

## Conclusion

No blocking upload, authorization, object-path, consistency, secret or HIGH/CRITICAL dependency risk
was found within the #44 MVP boundary.

