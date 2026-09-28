# Source Audit — Cosmosis Image Studio

Audit date: 2026-09-27

This project reimplements workflow concepts from references; no implementation source from the reference applications has been copied into this repository.

| Reference | Studied for | Decision |
|---|---|---|
| markfulton/NanoBananaEditor | reference images, masks, multi-turn editing, comparison, branching/history, persistence | **Reference only.** AGPL-3.0 application code is not imported. Reimplement interaction patterns independently. |
| freestylefly/awesome-gpt-image-2 | prompt categories, recipes, structured prompt patterns | **Schema/reference only by default.** Do not bulk-copy community prompt text without per-entry provenance/license review. |
| cocktailpeanut/image-to-prompt | object regions, OCR boxes, batch image analysis, structured output | **Reference only.** Implement IMAGE → STRUCTURE → PROMPT independently. |
| lusouldepth-ai/image-2-reverse-prompt | style DNA vs replaceable subject, composition/camera/light/color/material/type decomposition | **Concept adaptation.** MIT reference; built-in recipe is newly written and attributed. |
| clockless-org/html-anything | self-contained HTML knowledge/artifact pattern | **Concept adaptation.** Runtime docs use stable IDs + machine-readable manifests. |
| openrndr/orml | optional local perception capability set | **Optional runtime boundary.** No ORML source copied. Runtime class detection fails closed if modules are absent. |
| Uploaded DonPad Workflow Redesign v5 | prompt-tree semantics, read-only upstream libraries, copy-to-local provenance, revisions, hyper-index pattern | **Behavioral reference.** Browser/IndexedDB implementation is not copied; semantics are rebuilt in Kotlin/SQLite. |

## Source-control rule

Any future imported prompt/template must record `source`, `sourceUrl`, `sourceVersion`, and `sourceLicense` when known. Upstream entries are immutable in the editor; editing requires a derived local copy with a fresh ID.
