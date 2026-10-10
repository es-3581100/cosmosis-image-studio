# Friendly image-editor UI checkpoint — 2026-10-09

## Purpose

PR #18 changes the default COSMOSIS interaction model from a technical workstation shell to a user-facing image editor.

Base main before this work:

```text
b25fe31da483848b7a9b273f0e4fb0fb1b8ad300
```

Implementation branch:

```text
ui/nanobanana-friendly-workflow-20261009
```

## Interaction reference

The public `markfulton/NanoBananaEditor` repository was used as an interaction reference, not as a source-code/template dependency.

Exact public blobs consulted:

| Source | Git blob SHA | Relevant interaction idea |
|---|---|---|
| `src/App.tsx` | `c5f0ebb365918557e84a4a115639d8d7082e9718` | composer / canvas / history shell |
| `src/components/Composer.tsx` | `b03b1369103c7d921d02b2b0b25582625dc719c3` | Generate/Edit/Mask primary modes; prompt + references + model in one composer |
| `src/components/Canvas.tsx` | `e8ebeaae9f5fb74a6dfa2f2ad164338aada03cb2` | canvas-centric editing and compact status/tool surfaces |
| `src/components/HistoryPanel.tsx` | `1e1f7ee113dd2fd28cac9984d1ebd4641ecba86d` | visual output history with direct re-selection |

NanoBananaEditor is AGPL-3.0. COSMOSIS does **not** copy those components, code, visual styling, assets, class names, or implementation details. The generalized interaction pattern was reimplemented independently in Kotlin/OPENRNDR.

Math-by-Design and the existing Offworld theme remain the visual/design authority for COSMOSIS.

## Default user flow

The default UI now teaches the application through the work itself:

```text
Generate / Edit / Mask
        ↓
direct prompt composer
        ↓
optional reference images
        ↓
provider + model
        ↓
large primary action
        ↓
canvas result
        ↓
visual History
```

### Left composer

The composer contains:

- Generate / Edit / Mask mode tabs;
- direct in-window prompt editing;
- source-image cueing for Edit/Mask;
- reference-image thumbnails + add-reference action;
- visible but secondary provider/model controls;
- Analyze / Remix / Upscale quick tools;
- one large contextual primary action.

The primary action reads `Generate image`, `Apply edit`, `Apply masked edit`, or `Create remix` depending on mode.

### Center canvas

The canvas remains the dominant surface and keeps:

- drag/drop import;
- pan/zoom;
- Fit;
- Compare;
- mask visibility;
- Export;
- analysis overlays;
- split compare.

A no-project state now gives explicit Create/Open project actions instead of relying on implicit file-menu knowledge.

### Right visual history

The previous version graph is no longer the first history representation shown to ordinary users.

The default History panel displays version image thumbnails. Clicking a thumbnail selects that exact non-destructive version. The selected version exposes Edit / Compare / Export actions.

The underlying branching version graph and lineage semantics are unchanged.

## Advanced/legacy boundary

The prior technical workspace is preserved behind:

```bash
COSMOSIS_LEGACY_WORKSPACE=1 ./gradlew :run
```

The default friendly editor still exposes an **Advanced** button that opens the existing ControlDock on demand.

This keeps specialist features available while preventing them from competing with the common image-editing loop.

## Verification

PR #18 implementation run:

```text
38008238098
```

Result: **SUCCESS**.

The run passed:

- deterministic core smoke;
- static provenance + Offworld audit;
- dependency-resolved unit/ORML tests;
- `acceptanceSmoke`;
- offline provider transport + capability contract;
- ORML runner distribution smoke;
- desktop graphical runtime smoke;
- U2Net / BodyPix / classifier / super-resolution distribution checks.

Graphical report:

```text
UI_SMOKE_PASS
layout.mode=single-window
layout.family=friendly-editor
layout.primaryFlow=generate-edit-mask
composer.directPrompt=true
history.visual=true
dock.showing=false
workspace=1600x980
composer.width=326
history.width=286
theme.background=#10100E
theme.foreground=#FFFFE3
theme.radius=0
warmDarkPixels=165010
ivoryPixels=2196
```

Screenshot SHA-256:

```text
1aa315c794f99bea4e8c8b3c973f49ab75843c6bca40b79a0c12629153c561ca
```

UI smoke artifact:

```text
cosmosis-ui-smoke
artifact id 11652266808
artifact digest sha256:0d0b9e72134a74512177f7e95452be26f9794dad4e00c9f4ca0dc86271b0d417
```

No paid/live provider call was used by this verification.

## CI contract

The default desktop smoke must assert:

```text
UI_SMOKE_PASS
layout.mode=single-window
layout.family=friendly-editor
layout.primaryFlow=generate-edit-mask
composer.directPrompt=true
history.visual=true
dock.showing=false
theme.background=#10100E
theme.foreground=#FFFFE3
theme.radius=0
```

This intentionally makes a regression back to an opaque engineering-first default fail CI.

## Known boundaries

The graphical smoke verifies startup/rendering/layout markers, not every pointer/keyboard interaction.

Direct prompt editing compiles in the real OPENRNDR runtime and is part of the default event path, but human usability should still be validated on the target laptop after merge.

The current Mask default offers an Auto mask action; the full brush/erase workflow still lives in the specialist controls and remains a candidate for the next friendly-UI migration.

The earlier multi-hour SDL / `RenderTargetGL3.bind()` OOM observation is not claimed fixed by this UI checkpoint.
