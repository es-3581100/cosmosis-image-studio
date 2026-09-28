# Interrupted Build Recovery Manifest

Recovery date: 2026-09-28

## Recovery disposition

RECOVERED — the interrupted Cosmosis Image Studio source/config/test tree has been reconstructed from durable artifacts and verified by dependency-resolved CI.

Five historical generated `test-results/*.log` files from the interrupted archive tail were not recreated byte-for-byte; they are non-source verification artifacts and are superseded by the GitHub Actions evidence recorded below.

## Authority ledger

- Repository: `es-3581100/cosmosis-image-studio`
- Target branch from the interrupted publication: `main`
- Pre-recovery remote `main` HEAD: `17ab03ea5e885f8c4deb67ce8cd6d804606270fe`
- Recovery-tooling `main` HEAD before source promotion: `2f58ef2cf82fe38dd6a59bfdd1cfed57aeab839f`
- Recovery branch: `recovery/partial-source`
- Initial exact archive-prefix checkpoint: `9e22620166ab7b25912d170caa15498feaf58730`
- Verified reconstructed source checkpoint: `3c330a182e99929b812502949255caa91ea8a706`
- Verified source tree SHA: `d96d4d61365d34185eebd1383edd95a295016fa3`
- Green CI run: `36479332195`
- Green CI job: `109120818275`

The recovery branch is an intentional orphan branch and has no common ancestor with `main`. Promotion must therefore create a new commit using the verified recovery tree with current `main` as its parent, then fast-forward `main`. Do not force-push.

## Recovery sources used

1. Exact GitHub `.bootstrap/part-*` objects created by the interrupted publication.
2. GitHub Actions extraction logs and the partial gzip/tar stream.
3. Durable recovery branch `recovery/partial-source`.
4. The earlier complete recovery manifest/checkpoint metadata embedded in the archive.
5. Exact recovered callers and type contracts from the archive prefix.
6. CI/compiler diagnostics used only to repair bounded reconstruction mismatches.
7. The attached recovery protocol was inspected; it contained the protocol itself and no additional implementation artifact.

No destructive Git cleanup, reset, force push, branch deletion, or history rewrite was used.

## Archive reconstruction evidence

The interrupted archive expected 117,328 base64 bytes. The durable GitHub bootstrap objects preserved 100,000 base64 bytes; the missing tail was 17,328 bytes.

Partial extraction created 73 files in the temporary recovery tree: 72 archive paths plus the generated `RECOVERY_PARTIAL.md` ledger. The archive paths included `.github/workflows/ci.yml` and all project files through `src/main/kotlin/studio/cosmosis/ui/StudioController.kt`.

The tar stream ended inside `StudioController.kt`, so that boundary file was reclassified from `RECOVERED_EXACT` to `RECOVERED_PARTIAL` even though a filesystem entry existed.

Classification at the archive boundary:

- 71 original tracked files: `RECOVERED_EXACT`
- `src/main/kotlin/studio/cosmosis/ui/StudioController.kt`: `RECOVERED_PARTIAL`
- 12 archive-tail paths: `MISSING`

The exact `.github/workflows/ci.yml` file was present in the extraction, but the first Actions push was rejected because the Actions token could not create/update workflow files. It was subsequently restored through the authorized GitHub connector.

## Reconstructed bounded tail

Meaningful missing/partial files recovered or repaired after the exact-prefix checkpoint:

- `src/main/kotlin/studio/cosmosis/ui/StudioController.kt`
- `src/main/kotlin/studio/cosmosis/ui/StudioState.kt`
- `src/main/kotlin/studio/cosmosis/workers/Director.kt`
- `src/main/kotlin/studio/cosmosis/workers/ImageCritic.kt`
- `src/main/kotlin/studio/cosmosis/workers/JobEngine.kt`
- `src/main/resources/config/model-registry.json`
- `src/test/kotlin/studio/cosmosis/CoreBehaviorTest.kt`
- `src/test/kotlin/studio/cosmosis/SqliteRecoveryTest.kt`
- `.github/workflows/ci.yml` restored from the recovered workflow artifact/contract

Historical generated logs intentionally not restored byte-for-byte:

- `test-results/core-smoke.log`
- `test-results/kotlinc-all-with-stubs.log`
- `test-results/kotlinc-jobengine.log`
- `test-results/kotlinc-ui-controller.log`
- `test-results/static-audit.log`

Current GitHub Actions logs are the replacement verification evidence.

## Repair history after checkpoint

Recovery did not mix in redesign. Repairs were bounded to evidence-backed compatibility issues exposed by CI:

1. Restored the controller contract expected by exact recovered `ControlDock.kt`: `cancelActiveJobs(): Int`.
2. Replaced one ambiguous mutable-list `+=` with `.add()` in the reconstructed controller.
3. Aligned the reconstructed lineage test with the exact recovered `VersionGraphLayout.layout()` API and its direct `List<VersionGraphPoint>` return type.

No production architecture, provider routing model, persistence schema, UI design direction, or unrelated subsystem was refactored during these repairs.

## Verification

### Deterministic core smoke

GitHub Actions run `36479332195`:

```text
PASS secret redaction
CORE_SMOKE_PASS
```

The core smoke includes prompt/provenance, image analysis, local preview generation/editing, provider spend estimation, capability validation, provider wire translation, model-registry migration, redaction, ORML fail-closed behavior, bounded Director planning, and agent-index parsing.

### Static / provenance / Offworld audit

GitHub Actions run `36479332195`:

```text
PASS agent HTML manifests: 7
PASS master hyper-index Offworld/reduced-motion markers
PASS secret literal scan
PASS Offworld anti-pattern scan
```

### Dependency-resolved Gradle + JUnit + acceptance

Command:

```text
gradle --no-daemon test acceptanceSmoke --stacktrace
```

Result in run `36479332195`:

```text
ACCEPTANCE_SMOKE_PASS ... versions=7
> Task :test
BUILD SUCCESSFUL
```

This verifies the dependency-resolved Kotlin build, JUnit suite, SQLite-backed project reopen/recovery, local deterministic generation/edit pipeline, branching lineage, smart mask path, Agent Build + critic report, diagnostics, and HTML export without paid provider calls.

### Earlier failures and classification

- CI run `36476504237`: implementation compile failure; recovered exact UI expected `cancelActiveJobs(): Int`, reconstructed controller returned `Unit`; one reconstructed mutable-list expression also failed compilation.
- CI runs `36476788761`, `36477164912`, and `36479074753`: production compilation and `acceptanceSmoke` passed; remaining failures were confined to the reconstructed JUnit lineage-layout call shape.
- These failures were repaired as separate recovery commits and are superseded by green run `36479332195`.

## Conflicts

None remain in the authoritative recovered source tree.

The orphan recovery branch vs. `main` ancestry difference is structural, not a content conflict. Promotion must preserve both histories by creating a main-parented commit from the verified recovery tree.

## Known incomplete / unverified work

- Paid/network OpenAI, Gemini, LiteLLM, and custom-provider smoke tests remain opt-in and were not executed during recovery.
- Native ORML inference remains intentionally fail-closed/unbundled unless an adapter is configured.
- Runtime desktop visual acceptance on a real graphical workstation is not covered by headless CI.
- Historical `test-results/*.log` files were not byte-for-byte recovered; current CI logs supersede them.

## Next smallest action

Create a promotion commit whose tree is the verified recovery tree and whose parent is current `main`, fast-forward `main` to that commit with `force=false`, then verify the resulting `main` CI run before resuming normal implementation.


---

## Promotion completion

Recovery promotion completed on 2026-09-28.

- Promotion commit: `900bd20303a0dfdf02d83538fa08359fd9906f7e`
- Promotion tree: `fc3e90146d651f319fd292732cb23bd25d9408f9`
- First parent (pre-promotion `main`): `2f58ef2cf82fe38dd6a59bfdd1cfed57aeab839f`
- Second parent (verified recovery history): `2ce8ddf31789acf805d61e54267fb01a43a0c6f3`
- Ref update: fast-forward with `force=false`
- Main verification run: `36480100741`
- Main verification result: SUCCESS
- Core smoke: PASS
- Static provenance / secret / Offworld audit: PASS
- Dependency-resolved `test acceptanceSmoke`: PASS
- Temporary `.bootstrap` payloads are absent from the promoted tree.
- Temporary recovery-extractor workflow is absent; only `.github/workflows/ci.yml` remains.

Recovery is complete. Normal implementation may resume from `main`. The next bounded verification action outside CI is an optional real graphical-workstation launch/visual acceptance pass; paid provider smoke tests remain explicit opt-in work.
