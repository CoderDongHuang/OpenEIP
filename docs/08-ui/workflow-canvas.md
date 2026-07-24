# Workflow Canvas Workspace

> Version: v0.4 | Issue: #70

## User Workflow

The Workflow route opens the operational editor, not a marketing page. A compact definition/version
rail sits beside a full-height `@xyflow/react` Canvas. The top toolbar provides save, validate, publish,
run, undo/redo, zoom-to-fit, and execution view controls. A right inspector edits the selected node or
edge. A bottom execution panel shows ordered node status, approvals, retry actions, and sanitized output.

## Canvas Behavior

- Drag node types from a searchable palette or insert them from a keyboard menu.
- Connect only compatible typed ports; rejected edges leave the graph unchanged and announce the error.
- Start/End are unique. Loop back-edges can only target the loop body port and require a maximum count.
- Dirty, saving, saved, validating, and conflict states are explicit without shifting the toolbar.
- Published versions are read-only. Restore creates a draft and never edits history.
- Undo/redo is local to the current draft and resets after a server version conflict.

## Responsive and Accessible Layout

Desktop uses stable left rail, Canvas, inspector, and resizable execution panel tracks. On narrow screens,
the Canvas remains the primary surface while palette, inspector, and execution history open as drawers.
Icon buttons have tooltips and accessible names. Nodes, ports, edges, menus, forms, dialogs, focus order,
selection, and approval actions are keyboard operable. Status is never color-only and reduced-motion is
respected.

## Safety and Failure States

The UI renders only known node schemas and escapes all remote labels/output. Secrets are selected by
reference and never redisplayed. Publish shows server validation grouped by node. Trigger secrets are
shown exactly once. Approval and retry commands require confirmation, disable during submission, and
refresh authoritative state after conflicts. SSE reconnect resumes from the last sequence; polling is a
bounded fallback.

## Visual Direction

The workspace follows the existing quiet enterprise shell: white/neutral work surface, dark navigation,
green primary commands, restrained status colors, 6 px or smaller radii, and dense scanning-oriented
typography. Node type icons and port colors distinguish categories without turning the Canvas into a
decorative card grid.
