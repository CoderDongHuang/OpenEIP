# Knowledge Retrieval Workspace

> Version: v0.3 | Issue: #65

The existing Knowledge page remains the operational surface. Each selected base exposes a compact
search row above its attached documents. Users choose Hybrid, Full text, or Vector mode; Hybrid is the
default. Results show source filename, bounded excerpt, page attribution, character range, chunk ID,
and deterministic score. Search never exposes vectors or hidden prompts.

Chat citations use the same provenance contract. A citation tag displays page and score; its popover
shows the bounded source excerpt and exact range. Desktop and mobile use the same information hierarchy,
and result regions have stable height/overflow behavior.
