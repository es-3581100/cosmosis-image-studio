# Changelog

## 0.1.0 — 2026-09-27

- Established Offworld Kotlin/OPENRNDR workstation architecture.
- Added project filesystem + SQLite schema, restart-safe job recovery, immutable assets and branching versions.
- Added DonPad-derived prompt tree domain with provenance, revisions, search, smart keywords and prompt exchange.
- Added non-destructive raster masks and Swing mask editor.
- Added baseline local image analysis and IMAGE → STRUCTURE → PROMPT modes.
- Added capability-driven OpenAI, Gemini, LiteLLM and custom OpenAI-compatible adapters.
- Added bounded coroutine worker queue and Director plan.
- Added ORML capability boundary for U2Net, BodyPix, image classifier and super-resolution.
- Added deterministic HTML agent hyper-index and sanitized project reports.
- Added dependency-free core smoke harness; fixed redaction and intent-tokenization defects found by it.
- Made Agent Build executable: visible Director plan, explicit finite budgets, indexed local knowledge retrieval, bounded generation, critic report, and lineage metadata.
- Hardened cancellation so late provider results from cancelled jobs are discarded rather than admitted to lineage.
- Added GitHub CI for dependency-light audit plus dependency-resolved JUnit and offline acceptance smoke.
