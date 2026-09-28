# Architecture

## Authority flow

```text
Human intent
  → OPENRNDR instrument field + native control dock
  → PromptLibrary / ImageToPrompt / MaskDocument
  → PromptCompiler
  → CapabilityValidator + ProviderRegistry
  → bounded JobEngine
  → provider adapter
  → immutable generated asset
  → VersionGraph + GenerationRecord
  → HTML report / export
```

## Boundaries

- `ui/`: OPENRNDR owns visual canvas, zoom/pan, registration marks, mask overlay, version strip and worker state. Swing owns form-heavy controls and dialogs.
- `prompt/`: DonPad-style prompt tree semantics, smart keywords, immutable revisions, upstream-copy provenance and exchange format.
- `analysis/`: dependency-light local visual analysis fallback. Results are derived metadata.
- `mask/`: independent raster mask object with brush/erase, undo/redo, invert and feather.
- `provider/`: canonical requests, capability validation, model registry and OpenAI/Gemini/LiteLLM adapters.
- `workers/`: bounded Director plan + durable generation queue. No unbounded recursive generations.
- `storage/`: filesystem blob store + SQLite metadata. Originals are copied immutably.
- `lineage/`: branching version graph independent of chronological UI ordering.
- `docs/`: deterministic runtime hyper-index; agents retrieve small context packets by intent.
- `export/`: self-contained sanitized HTML artifacts.

## Persistence

A project owns `project.db`, original/generated/mask/preview/export blobs, metadata and docs. A process restart changes persisted RUNNING jobs to INTERRUPTED; it does not automatically spend money retrying them.
