# Single-window Math-by-Design UI checkpoint — 2026-10-09

## Scope

This checkpoint changes the default COSMOSIS desktop contract from two persistent top-level windows to one persistent OPENRNDR workstation.

Base main before this work:

```text
ffaaa6d212078657a3eaa5266c3d6a7de525471b
```

Implementation branch:

```text
ui/math-by-design-single-window-20261009
```

Pull request: #17.

## Design-reference provenance

Math-by-Design was used as a design-system reference, not as a page/template to copy.

Exact source blobs consulted from `es-3581100/math-by-design`:

| Source | Git blob SHA |
|---|---|
| `math-by-design.html` | `93a39464e6ef3937bab65e271b9f530b7a62be14` |
| `phi-flow-dev-ref.html` | `fac339005dd93327dfbec95f8061a6f7411727e3` |
| `ui-build-ref-master-v2.html` | `cbda4b54cf7f9c9152e9db07dc393160e8a30f2c` |

The applied rules were:

- hierarchy before decoration;
- developer-tool / inspector surfaces route to quiet dark;
- one primary attention target rather than competing controls;
- stable spatial roles for navigation;
- tonal depth before effects;
- one geometry language;
- interaction is a temporary handoff to content, not decorative motion;
- narrow-screen usability outranks preserving a desktop composition literally.

Existing COSMOSIS Offworld authority remains primary for material/color identity:

- background `#10100E`;
- foreground `#FFFFE3`;
- hard corners (`radius=0`);
- functional green/red status color;
- existing 5/8/13/21/34 spacing vocabulary.

## Architecture change

Before this checkpoint, `App.kt` always created a top-level Swing `ControlDock`, then `OpenrndrWorkspace.kt` created a second top-level OPENRNDR window and attempted to tile beside the dock.

Default startup now:

1. creates `StudioState` and `StudioController`;
2. does **not** create `ControlDock`;
3. launches the OPENRNDR workstation as the only persistent application window.

The old dock is retained as an explicit advanced/legacy escape hatch:

- click `ADV ↗` in the workstation; or
- launch with `COSMOSIS_LEGACY_CONTROL_DOCK=1`.

When the legacy environment gate is used, Swing construction remains on the Swing event-dispatch thread.

Transient OS/Swing dialogs such as file choosers, error dialogs, and the bounded prompt editor are not treated as persistent workspaces.

## Attention hierarchy

The first-pass shell maps the Math-by-Design hierarchy into existing COSMOSIS geometry without replacing the proven image renderer:

1. **Primary field — image stage.** The central image/canvas owns the largest area.
2. **Primary action — RUN.** The header `RUN` control is the only filled ivory action.
3. **Workflow state — left rail.** Generate/Edit/Mask/Remix/Analyze/Upscale remain in one stable location; active state is visually stronger.
4. **Context — right inspector.** Prompt/reference/workflow/mask/analysis/job information remains secondary.
5. **History — bottom lineage.** Version geometry stays available without competing with the current image.
6. **Advanced tooling — opt-in.** Prompt-library/directive/provider-detail surfaces remain available through the legacy advanced dock while they are migrated deliberately.

Top-level single-window actions added:

```text
NEW  OPEN  IMPORT  REF+  PROMPT  RUN  ADV ↗
```

Keyboard shortcuts remain available, including Generate/Edit/Mask workflow changes and Ctrl+Enter execution.

## Color-space defect exposed by the single-window conversion

The first single-window graphical run exposed a defect that the old Swing surface had masked.

The OPENRNDR theme bridge constructed byte-derived theme values with the default `ColorRGBa` linear interpretation. OPENRNDR then converted those values during drawing/presentation. In the Xvfb/GLFW screenshot, intended `#10100E` pixels appeared around `(71,71,66)` instead of warm black.

The previous two-window smoke could still pass because the Swing `ControlDock` contributed correctly encoded dark pixels to the desktop-wide screenshot.

The fix makes the AWT→OPENRNDR boundary explicitly `Linearity.SRGB`, uses `ColorRGBa.fromHex` for the surface token, and derives translucent ivory strokes through `FG.opacify(...)`.

The smoke threshold was **not** weakened.

## Verification history

### Run 38005280926 — expected failure that exposed color-space drift

The runtime reached frame 20 and proved the structural single-window state:

```text
layout.mode=single-window
dock.showing=false
workspace=1560x960
```

but correctly failed the Offworld pixel gate:

```text
warmDarkPixels=0
error=Offworld warm-dark surface not visible enough (count=0)
```

Screenshot SHA-256:

```text
69f4ffad8cc277bc801f9ab0e0b8a0faa3b4670474b12eb78c4eae30d7a97bbb
```

### Run 38005546497 — corrected implementation PASS

Full workflow conclusion: **success**.

The graphical smoke reported:

```text
UI_SMOKE_PASS
layout.mode=single-window
frame=20
workspace=1560x960
desktop=2600x1200
dock.showing=false
theme.background=#10100E
theme.foreground=#FFFFE3
theme.radius=0
sampled=346800
warmDarkPixels=149824
ivoryPixels=274
```

Screenshot SHA-256:

```text
c9d69af12a68cc3efb42b9bf45acc6af5a4f097b6f6f93d4bdef1874a1b6905c
```

The same run also passed:

- deterministic core smoke;
- static provenance / Offworld audit;
- dependency-resolved tests;
- `acceptanceSmoke`;
- provider transport + capability contract;
- ORML runner distribution smoke;
- U2Net/BodyPix/classifier/super-resolution helper/distribution checks.

No live or paid provider calls were part of this UI verification.

## CI invariant

The desktop smoke is required to assert all of the following:

```text
UI_SMOKE_PASS
layout.mode=single-window
dock.showing=false
theme.background=#10100E
theme.foreground=#FFFFE3
theme.radius=0
```

A future change that reintroduces the persistent default `ControlDock` should therefore fail CI.

## Remaining boundary

This is intentionally a bounded first UI pass, not a full deletion of Swing.

Still behind the explicit advanced dock:

- full prompt-library CRUD and tree browsing;
- directive CRUD;
- detailed provider/model capability controls;
- appearance/ORML settings panels;
- some transform dialogs and specialist workflows.

Those should be migrated only when they can preserve the same attention hierarchy instead of recreating the old control wall inside the canvas.

The earlier long-running SDL `RenderTargetGL3.bind()` heap/OOM observation is **not** claimed fixed by this checkpoint. The normal UI smoke proves startup/rendering, not a multi-hour soak.
