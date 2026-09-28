# Architecture

## Authority flow

```text
Human intent
  → OPENRNDR instrument field + native control dock
  → PromptLibrary / AgentDirective / ImageToPrompt / MaskDocument / reference set
  → WorkflowMode + PromptCompiler
  → CapabilityValidator + ProviderRegistry
  → bounded JobEngine
  → provider adapter
  → immutable generated asset
  → VersionGraph + GenerationRecord
  → HTML report / export
```

## Boundaries

- `ui/`: OPENRNDR owns visual canvas, zoom/pan, registration marks, split comparison, mask/analysis overlays, version graph and low-noise worker state. Swing owns form-heavy controls, workflow routing, prompt/reference/directive lists, appearance settings and dialogs.
- `prompt/`: DonPad-style prompt tree semantics, smart keywords, immutable revisions, upstream-copy provenance and exchange format.
- `analysis/`: dependency-light visual analysis/OCR fallback. Results are persisted as derived metadata and may be rendered as toggleable technical overlays.
- `mask/`: independent raster mask object with brush/erase, undo/redo, invert and feather.
- `provider/`: canonical requests, capability validation, model registry and OpenAI/Gemini/LiteLLM adapters.
- `workers/`: bounded Director plan + durable generation queue. Canonical requests/budgets are persisted before execution; failed/interrupted work can be explicitly resumed. No unbounded recursive generations.
- `storage/`: filesystem blob store + SQLite metadata. Originals and references are copied immutably; prompt revisions, directives, active reference selection and project appearance/workflow settings are local.
- `lineage/`: branching version graph independent of chronological UI ordering.
- `docs/`: deterministic runtime hyper-index; agents retrieve small context packets by intent.
- `export/`: self-contained sanitized HTML artifacts.

## Persistence

A project owns `project.db`, original/generated/mask/preview/export blobs, derived analysis metadata and docs. A process restart changes persisted RUNNING jobs to INTERRUPTED; it does not automatically spend money retrying them. New-format jobs contain enough canonical request state to allow an explicit user resume through the same capability-validation and result-admission path. Older jobs without that persisted request remain non-resumable.


## OPENRNDR / ORX selection rule

The supplied semantic knowledge tree is used as an architecture map: core drawing/rendering and interaction remain native OPENRNDR responsibilities, while ORX image-fit/compositor/fx, animation, text/SVG and shader modules are optional expansion points. A semantic relationship is not dependency authority. Add a module only when a concrete editor feature needs it and its current version, license and test boundary have been verified.
