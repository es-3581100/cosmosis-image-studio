# Capability Matrix

| Capability | State in this build | Evidence / boundary |
|---|---|---|
| Offworld OPENRNDR workspace | Implemented | `ui/OpenrndrWorkspace.kt`; runtime compile awaits dependency resolution on this host |
| Local projects + immutable imports | Implemented | `ProjectStore`, SQLite schema |
| Prompt trees/provenance/search/revisions | Implemented | `PromptLibrary`, `PromptExchange`; core smoke PASS |
| Smart keywords | Implemented deterministic layer | explicit/imported tags are preserved; regex/frequency tags are separate |
| Masks | Implemented | brush/erase, undo/redo, invert, feather, PNG persistence; smoke PASS |
| Version graph | Implemented | branching DAG, no history flattening; smoke PASS |
| Local image→prompt | Implemented baseline | palette/luma/edge/orientation + editable structured prompt; smoke PASS |
| OpenAI Images | Implemented adapter | paid live call not run here |
| OpenAI Responses image workflow | Implemented adapter path | paid live call not run here |
| Gemini image generation/edit | Implemented adapter | Interactions API generation/editing, server-side turn chaining, optional Google Search grounding + thinking controls; paid live smoke remains opt-in |
| LiteLLM/custom OpenAI-compatible | Implemented adapter | live gateway test not run here |
| Durable worker queue | Implemented | persisted state + bounded retry/timeout/spend + cancellation result discard; dependency-light and stub verification PASS |
| ORML discovery | Implemented | U2Net, BodyPix, classifier, super-resolution capability descriptors + docs; runtime probes do not initialize model classes |
| ORML adapter SPI | Implemented | `ServiceLoader` discovery, duplicate-provider rejection, adapter diagnostics, concrete-output validation, and fail-closed execution |
| ORML native inference | **Not verified/complete** | No ORML model adapter is bundled. A separately installed, audited adapter must expose the SPI before Cosmosis reports `READY`; real U2Net/BodyPix/classifier/upscaler inference remains a workstation verification task |
| Agent Build | Implemented | visible Director plan, explicit budget confirmation, indexed local knowledge context, bounded generation, critic annotation, lineage/report persistence; local acceptance path implemented |
| HTML hyper-index/runtime lookup | Implemented | intent search/context packet; natural-language intent smoke PASS |
| Project HTML report | Implemented | CSP self-contained, secret redaction; smoke PASS |
| Full desktop dependency build | CI configured / local host blocked | `.github/workflows/ci.yml` provisions JDK 21 + Gradle 9.8 and runs tests + offline acceptance; this container still has no dependency network |
| Live provider smoke | Opt-in only | `liveProviderSmoke`; not run without explicit credentials/env gate |
