# OPENRNDR / Swing Graphical Runtime Verification — 2026-09-29

## Result

**PASS — Linux x86_64 / Xvfb / OPENRNDR GLFW / Mesa llvmpipe**

This checkpoint verifies that Cosmosis launches the real two-window desktop workstation in a graphical environment and that the Swing control surface and OPENRNDR workspace retain the dark Offworld system together.

## Code and execution provenance

- Base main commit before the UI repair: `69dbebeb171b76735457b10787a57431d41a59ce`
- Tested branch head: `2b9f19d8a1d50a510be7eea6a9bf3875bd68cc0a`
- Pull request: #14 — `fix/offworld-control-surface-20260929`
- Workflow: `.github/workflows/ci.yml`
- Workflow run: https://github.com/es-3581100/cosmosis-image-studio/actions/runs/36644212766
- Job: `109663278756`
- Artifact: `cosmosis-ui-smoke`, artifact id `11067931296`
- Artifact digest: `sha256:75244da92d9ada82d1cc4535a2c7dda9215c8ed7b435deb3a77b387d0c638a82`

## Runtime identity

The smoke ran on the normal Linux GitHub runner with JDK 21 and Gradle 9.8.0. The UI process reported:

```text
Application backend: GLFW
OpenGL vendor: Mesa
OpenGL renderer: llvmpipe (LLVM 20.1.2, 256 bits)
OpenGL version: 4.5 (Core Profile) Mesa 25.2.8-0ubuntu0.24.04.2
```

The virtual desktop was `2600×1200×24`. The Swing ControlDock was positioned at `30,70` with size `880×920`. OPENRNDR used the documented window-position configuration path to place the `1480×900` workspace beside the dock instead of obscuring it.

## Regression repaired

The prior graphical smoke proved process startup but allowed a visual regression: Swing widgets were constructed before the Offworld UI defaults were installed, so large text and selection surfaces rendered with bright native styling.

The repair now:

- installs the Swing Offworld defaults before ControlDock field/component construction;
- reapplies dark, hard-corner styling through the actual component tree;
- preserves ordinary Swing controls for text entry, lists, trees, tabs and dialogs rather than rebuilding them as GPU widgets;
- tiles the OPENRNDR workspace beside the control dock when the display has enough width;
- makes graphical CI fail when the dock is not predominantly Offworld-dark or when bright neutral native chrome exceeds the bounded threshold.

## Captured evidence

The resulting `report.txt` contained:

```text
UI_SMOKE_PASS
frame=20
workspace=1480x900
desktop=2600x1200
dock.showing=true
theme.background=#10100E
theme.foreground=#FFFFE3
theme.radius=0
sampled=346800
warmDarkPixels=84376
ivoryPixels=709
dock.bounds=30,70,880,920
dock.sampled=90258
dock.darkPixels=83564
dock.brightNeutralPixels=643
screenshot.sha256=01fe574c87dd9d2bcaef8db16548a313c343ad7e486573fdaaadbd35a3ddd174
```

The dock dark-surface ratio was approximately **92.6%** (`83,564 / 90,258`). Bright-neutral samples were approximately **0.7%** (`643 / 90,258`), below the smoke ceiling of 3%.

The captured screenshot shows the ControlDock and OPENRNDR workspace simultaneously, with no large white text/control surfaces and no window overlap.

## What this verifies

Verified on this platform:

- OPENRNDR application startup through GLFW;
- software OpenGL rendering through Mesa llvmpipe;
- coexistence of the Swing control surface and OPENRNDR workspace;
- wide-display tiling of the two application windows;
- Offworld dark control-surface initialization before Swing widget construction;
- hard-corner theme authority (`radius=0`);
- screenshot production and deterministic runtime report creation;
- automated rejection of the specific bright-native-control regression.

## Limits

This is a **graphical runtime and structural visual-regression smoke**, not a human usability study or pixel-perfect design certification. Xvfb/llvmpipe is not a physical desktop compositor or GPU. The checkpoint does not establish macOS/Windows rendering parity, HiDPI/multi-monitor behavior, all native file-picker/modal appearance, accessibility-tool integration, or subjective typography quality.

Those boundaries remain separate from the now-verified Linux graphical workstation path.
