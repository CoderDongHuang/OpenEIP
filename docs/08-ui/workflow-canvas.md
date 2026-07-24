# Workflow Canvas Workspace

> Version: v0.4 | Issue: #70

## User Workflow

The Workflow route opens the operational editor, not a marketing page. A compact definition/version
rail sits beside a full-height `@xyflow/react` Canvas. The top toolbar provides node insertion, save,
validate, publish, run, version history, and Canvas zoom controls. A right inspector edits the selected
node. The Runs view shows ordered status, approvals, cancellation, and retry actions.

## Canvas Behavior

- Insert node types from the compact toolbar and position them by drag.
- Connect bounded named ports; the server performs authoritative edge and graph validation.
- Start is unique, End is required, and arbitrary graph cycles are rejected. Loop configuration is
  server-bounded to 100 iterations.
- Dirty, saving, saved, validating, and conflict states are explicit without shifting the toolbar.
- Published versions are immutable. Restore copies a version into the editable draft.
- A server revision conflict preserves the local graph and requires an explicit refresh before retry.

## Responsive and Accessible Layout

Desktop uses stable left rail, Canvas, and inspector tracks. On narrow screens the tracks stack without
shrinking Canvas controls below their stable touch targets.
Icon buttons have tooltips and accessible names. Nodes, ports, edges, menus, forms, dialogs, focus order,
selection, and approval actions are keyboard operable. Status is never color-only and reduced-motion is
respected.

## Safety and Failure States

The UI renders only known node schemas and escapes all remote labels/output. Secrets are selected by
reference and never redisplayed. Publish shows server validation grouped by node. Trigger secrets are
shown exactly once. Cancel and retry commands require confirmation and refresh authoritative state after
completion. The public API supports resumable SSE; the authenticated browser workspace uses bounded
three-second polling because native `EventSource` cannot attach the existing Bearer header.

## Visual Direction

The workspace follows the existing quiet enterprise shell: white/neutral work surface, dark navigation,
green primary commands, restrained status colors, 6 px or smaller radii, and dense scanning-oriented
typography. Node type icons and port colors distinguish categories without turning the Canvas into a
decorative card grid.
