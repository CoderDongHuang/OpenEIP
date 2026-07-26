# Agent Platform Management UI

> Version: v0.6 draft | Issue: #78

The Agent route opens a dense operational workspace with a stable left definition/version rail and a main
editor or run view. It follows the existing shell, restrained colors, compact typography, 6 px or smaller
radii, keyboard navigation, visible focus, reduced motion, and non-color-only status.

## Six management surfaces

| Surface                    | Primary workflow                                                                                       | Required controls and states                                                                                                      |
| -------------------------- | ------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| Agent definitions/versions | create draft, compare, validate, evaluate, publish, copy old version to draft, archive                 | immutable published banner, dirty/saving/conflict, validation grouped by section, publish blocked with gate evidence              |
| Run timeline               | inspect plan/steps/Workers/Handoffs/Tools, pause/resume/cancel/retry                                   | ordered stable sequence, live/poll reconnect, attempt history, budget meters, approval expiry, partial failure, sanitized details |
| Tool permissions           | browse versions, bind grant, constrain resources/arguments, set approval/expiry, revoke                | risk swatches, schema diff, effective-permission preview, stale Tool version, grant conflict, destructive confirmation            |
| Memory management          | filter purpose/source/sensitivity/state, inspect provenance/lineage, quarantine/delete/export metadata | content redaction, retention deadline, purge progress, denied content, bulk selection limits, destructive confirmation            |
| MCP Server management      | register endpoint/auth reference, test policy, discover, review drift, map capability to Tool, disable | no secret redisplay, egress/auth result, quarantined discovery, schema diff, drift suspension, session health                     |
| Evaluation Dashboard       | select suite/candidate/baseline, run, compare metrics/cases, inspect failures, record gate             | success/quality/latency/safety tabs, confidence/sample/flake labels, running/cancelled/failed, deterministic blocker banner       |

Every surface implements loading, empty, success, validation error, unauthorized, not found, stale revision,
network failure, and retry. Tables use server cursor pagination, stable widths, sorting/filtering announced to
assistive technology, and no tenant selector in request bodies. Dangerous actions use explicit text plus a
confirmation dialog; icon-only familiar controls have tooltips and accessible labels.

## Run detail layout

Desktop uses an unframed three-track layout: run/Worker tree, chronological timeline, and selected-event
inspector. Narrow layouts stack tree, timeline, and inspector while keeping status/actions visible. Timeline
rows reserve fixed icon/time/status columns so streaming updates do not shift layout. The inspector renders
known safe fields only; unknown fields are omitted, never interpolated as HTML.

The UI shows public plan objectives and sanitized summaries. It never requests or labels hidden reasoning,
raw prompts, secrets, unrestricted Tool arguments/results, Memory content without a separate content grant,
or MCP auth material. Authorization failures refresh effective permissions instead of optimistic replay.

## Concurrency and commands

Draft updates send `If-Match`; a conflict preserves local edits and offers compare/refresh. Publish, run,
cancel, resume, retry, discover, delete, and evaluation commands send unique idempotency keys and render the
authoritative returned state. Buttons disable only for the submitted command, retain stable dimensions, and
do not infer completion from connection loss. Cancellation distinguishes requested from confirmed terminal.
