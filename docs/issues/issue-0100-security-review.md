# Security Review: v0.9 Performance Baseline

## Review Status

Pending independent security review. Local tests cover private-network opt-in,
credential/fragment rejection, redirect blocking, bounded reads, timeout and
transport failures. They do not replace repository dependency, container, IaC,
secret, or external-target review.

## Required Decision

The reviewer must confirm that benchmark targets are explicitly supplied,
private addresses require opt-in, response data is never persisted, and the
tool cannot be represented as production HA/capacity evidence. Findings and
approval must be linked before the release gate can pass.
