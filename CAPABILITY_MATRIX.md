# Capability Matrix

| Capability | State in this build | Evidence / boundary |
|---|---|---|
| Offworld OPENRNDR workspace | Implemented / CI compiled | `ui/OpenrndrWorkspace.kt`; canvas, split compare, pan/zoom, lineage instrument, mask + analysis overlays, workflow/reference inspector |
| Local projects + immutable imports | Implemented | `ProjectStore`, SQLite schema; source and reference imports are immutable project assets |
| Prompt trees/provenance/search/revisions | Implemented | `PromptLibrary`, `PromptExchange`; core smoke PASS |
| Smart keywords | Implemented deterministic layer | explicit/imported tags are preserved; regex/frequency tags are separate |
| Masks | Implemented | brush/erase, undo/redo, invert, feather, source-asset lineage, PNG persistence, configurable overlay color/opacity/visibility |
| Version graph | Implemented | branching DAG, no history flattening; open/compare/rename/favorite; local rotate/flip/crop/resize/upscale child versions |
| Local image→prompt + analysis overlays | Implemented | palette/luma/edge/orientation + OCR/saliency regions; editable structured prompt; derived JSON metadata; toggleable technical canvas overlays |
| Workflow presets | Implemented | Quick Generate, Precision Generate, Edit Existing, Mask Edit, Reference Remix, Style Transfer, Background Replace, Subject Preserve, Text Poster, Image-to-Prompt, Upscale, Agent Build |
| Multi-reference workspace | Implemented | immutable reference assets, persistent active reference set, provider capability validation, explicit reference workflows only |
| Agent directives | Implemented | project-local inspectable CRUD, enabled directives are recorded and injected into Agent Build context |
| Appearance/accessibility | Implemented | authoritative dark Offworld, reduced-motion flag, motion level, UI density, configurable functional mask overlay; settings persist per project |
| Current-image export | Implemented | explicit non-destructive export/copy from selected version |
| OpenAI Images | Implemented adapter | paid live call not run here |
| OpenAI Responses image workflow | Implemented adapter path | paid live call not run here |
| Gemini image generation/edit | Implemented adapter | Interactions API generation/editing, server-side turn chaining, optional Google Search grounding + thinking controls; paid live smoke remains opt-in |
| LiteLLM/custom OpenAI-compatible | Implemented adapter | live gateway test not run here |
| Durable worker queue | Implemented | canonical request/reference/mask/budget persistence, bounded retry/timeout/spend, cancellation result discard, crash-to-INTERRUPTED recovery, explicit failed/interrupted resume |
| ORML discovery | Implemented | U2Net, BodyPix, classifier, super-resolution capability descriptors + docs; runtime probes do not initialize model classes |
| ORML adapter/runtime integration | Implemented | `ServiceLoader` adapters plus isolated executable runners; duplicate/invalid provider rejection, bounded timeout, redacted runner output, concrete-output validation, project enable/disable, fail-closed execution |
| ORML runner distribution | Implemented / backend-pluggable | separate `orml-runner` Gradle application; protocol-v1 CLI, ServiceLoader backend SPI, `--describe` readiness handshake, mask/image/JSON artifact validation, installable distribution, contract tests |
| ORML editor semantics | Implemented / runtime-dependent | U2Net → smart subject mask, BodyPix → person mask, classifier → derived embedding metadata, super-resolution → UPSCALE child version; Agent Build can select BodyPix for person/clothing intents when READY |
| Pinned U2Net backend | Verified on Linux x86_64 | `orml-runner-u2net`; native workflow run `36612354676` built TensorFlow Java 0.4.1 + linux-x86_64 runtime, hash-verified the pinned model, reported `smart-subject-mask` READY, produced a real mask, and passed desktop `acceptanceSmoke` with the configured runner |
| Pinned BodyPix backend | Verified on Linux x86_64 | `orml-runner-bodypix`; native workflow run `36614548554` built TensorFlow Java 0.4.1 + linux-x86_64 runtime, hash-verified the MobileNet model, reported `person-body-mask` READY, produced a real mask, and passed desktop `acceptanceSmoke` through `ormlPersonMask()` |
| Pinned Image Classifier backend | Implemented / native smoke pending | `orml-runner-classifier`; pins ORML commit, bundled MobileNetV3 model by Git blob identity, TensorFlow Java 0.4.1 lineage, 224×224 RGB preprocessing, full embedding JSON, and top generated ImageNet class indices/scores. Ordinary CI proves fail-closed readiness without native prerequisites |
| Pinned Super Resolution backend | Implemented / native smoke pending | `orml-runner-super-resolution`; pins ORML FALSR-A model SHA-256, TensorFlow Java 0.4.1 lineage, Y/Pb/Pr preprocessing, 2× graph contract, bounded recursive octaves, and non-destructive UPSCALE lineage admission. Ordinary CI proves fail-closed readiness without native prerequisites |
| ORML native model execution | Partially verified | U2Net subject-mask and BodyPix person-mask execution are verified on Linux x86_64 / TensorFlow Java 0.4.1; classifier and super-resolution backends are implemented but await manual native verification; macOS/Windows/GPU variants remain separately unverified |
| Agent Build | Implemented | visible Director plan, generations/retries/parallelism/timeout/spend/fallback controls, inspectable project directives, indexed local knowledge, critic annotations, lineage/report persistence |
| HTML hyper-index/runtime lookup | Implemented | intent search/context packet; natural-language intent smoke PASS |
| Project HTML report | Implemented | CSP self-contained, secret redaction; smoke PASS |
| Full dependency-resolved build | Verified in PR CI | JDK 21 + Gradle 9.8; repeated green `test acceptanceSmoke` runs on workstation-completion branch |
| Provider spend hard ceiling | Implemented / fail-closed | local preview estimates exactly `$0`; remote routes refuse a hard spend ceiling when total request cost cannot be defensibly known before execution |
| Live provider smoke | Opt-in only | `liveProviderSmoke`; not run without explicit credentials/env gate |
