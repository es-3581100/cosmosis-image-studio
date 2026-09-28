# Interrupted Build Recovery Manifest

Recovery date: 2026-09-28

## Current recovery disposition

PARTIALLY_RECOVERED — the latest interrupted Cosmosis archive has been recovered exactly through a durable GitHub branch, with the remaining gap bounded to seven meaningful source/config/test files plus five regenerable test logs.

## Current authority

- Repository: `es-3581100/cosmosis-image-studio`
- Main branch pre-recovery HEAD: `2f58ef2cf82fe38dd6a59bfdd1cfed57aeab839f`
- Exact archive-prefix branch: `recovery/partial-source`
- Exact archive-prefix checkpoint: `9e22620166ab7b25912d170caa15498feaf58730`
- Source archive expected base64 bytes: 117328
- Exact uploaded/recovered base64 bytes: 100000
- Missing archive tail: 17328 bytes
- Partial extraction workflow run: `36475132796` — SUCCESS
- Main was not replaced during partial extraction.

## Recovery sources used

1. Exact GitHub `.bootstrap/part-*` objects from the interrupted publication.
2. GitHub Actions extraction logs from runs `36474969717` and `36475132796`.
3. Durable recovery branch `recovery/partial-source`.
4. Older complete local recovery archives/worktree for baseline versions of files in the missing archive tail.
5. Exact previously recorded diffs/test outputs for later Cosmosis changes.

## Exact recovered inventory

The archive-prefix extraction recovered 72 of the 84 original tracked files exactly. It includes all tracked files through:

`src/main/kotlin/studio/cosmosis/ui/StudioController.kt`

The recovery branch also contains `RECOVERY_PARTIAL.md`, which is recovery metadata rather than an original archive file.

## Missing original tracked files

Meaningful source/config/test files requiring evidence-driven reconstruction:

1. `src/main/kotlin/studio/cosmosis/ui/StudioState.kt`
2. `src/main/kotlin/studio/cosmosis/workers/Director.kt`
3. `src/main/kotlin/studio/cosmosis/workers/ImageCritic.kt`
4. `src/main/kotlin/studio/cosmosis/workers/JobEngine.kt`
5. `src/main/resources/config/model-registry.json`
6. `src/test/kotlin/studio/cosmosis/CoreBehaviorTest.kt`
7. `src/test/kotlin/studio/cosmosis/SqliteRecoveryTest.kt`

Regenerable verification logs, not source authority:

- `test-results/core-smoke.log`
- `test-results/kotlinc-all-with-stubs.log`
- `test-results/kotlinc-jobengine.log`
- `test-results/kotlinc-ui-controller.log`
- `test-results/static-audit.log`

## Conflicts

None identified among the exact recovered prefix files.

The seven missing meaningful files are classified `RECOVERED_PARTIAL` until rebuilt from stronger surviving evidence and verified.

## Recovery rules

- Do not rebuild the project from design prose.
- Do not replace exact archive-prefix files unless verification demonstrates a defect.
- Reconstruct only the seven bounded meaningful missing files.
- Regenerate test logs only after source recovery.
- Keep repaired work on the recovery branch until dependency-resolved verification passes.
- Replace `main` only with a coherent verified tree and preserve main history.

## Next smallest action

Inspect exact recovered callers/types on `recovery/partial-source`, reconstruct the seven missing meaningful files from older exact artifacts plus recorded later diffs, then run deterministic core/static/stub checks and dependency-resolved GitHub CI.
