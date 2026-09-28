# Interrupted Build Recovery Manifest

Recovery date: 2026-09-27

## Recovery disposition

RECOVERED — a coherent source tree, tests, documentation, build metadata, and verification logs survived in `/mnt/data/offworld-image-studio`.

## Last known authority

- Phase/task: Modern local-first AI image editor / Offworld Image Studio, interrupted during implementation hardening.
- Baseline SHA: UNAVAILABLE — no `.git` directory or surviving repository metadata was present in the recovered workspace.
- Original working branch: UNAVAILABLE.
- Pre-recovery remote HEAD: UNAVAILABLE — no remote metadata survived.
- Recovery checkpoint SHA: `6b37604e949c7704aab2918ea1517540ff3af420`.
- Recovery branch: `recovery/interrupted-build-20260927`.
- Last reported implementation state: Kotlin/OpenRNDR workstation architecture, local project/prompt/mask/lineage/provider/worker/doc systems implemented; dependency-light smoke suite passing; full dependency build and live provider/ORML-native tests still unverified.
- Interruption: conversational execution stream ended before a final packaged handoff.

## Recovery sources used

1. Exact surviving local filesystem tree under `/mnt/data/offworld-image-studio`.
2. Preserved test logs under `test-results/`.
3. Existing architecture/capability/license/source audit documents.
4. Current-turn rerun of `scripts/core-smoke.sh` and `scripts/static-audit.sh`.

No alternate branch refs, reflog, stash, orphaned Git objects, patches, archives, or peer worktrees existed in `/mnt/data` because no surviving Git repository was present.

## Exact pre-checkpoint snapshot

- Archive: `/mnt/data/offworld-image-studio.pre-recovery-checkpoint.20260927T124126Z.tar.gz`
- SHA-256: `aa019e4e2234dd989f0e3d24daad35928f35484a4fbddb50f925f39351a7edc3`

This archive was created before adding this recovery manifest or initializing Git.

## Recovery inventory

- 37 Kotlin production source files.
- 2 Kotlin/JUnit test files.
- 7 per-capability/provider agent HTML documents plus master agent/user indexes.
- Build/configuration files, source/license/capability audits, README, AGENTS, changelog and NOTICE.
- Preserved core/static/Kotlin compile verification logs.

Classification of the coherent recovered tree: `RECOVERED_EXACT`.

Known absent recovery classes: no Git metadata, no remote refs, no orphan object identities, no prior commit SHA.

## Verification

Passing:

```text
./scripts/core-smoke.sh
  PASS prompt revisions + provenance
  PASS prompt exchange
  PASS lineage branching
  PASS mask undo redo invert feather persistence
  PASS local image analysis + image-to-prompt
  PASS capability validation
  PASS provider wire translation
  PASS model registry parse + migration
  PASS secret redaction
  PASS ORML capability registry
  PASS HTML export secret sanitation
  PASS agent index parsing
  CORE_SMOKE_PASS

./scripts/static-audit.sh
  PASS agent HTML manifests: 7
  PASS master hyper-index Offworld/reduced-motion markers
  PASS secret literal scan
  PASS Offworld anti-pattern scan
```

Environment:

```text
Java: OpenJDK 21.0.11
kotlinc: 1.9.0
system Gradle: absent
```

Environment-classified failure:

```text
./gradlew test
exit 127
Gradle is not installed; the recovered source bundle intentionally does not vendor the wrapper JAR.
```

`git fsck --full` passes and the recovery bundle verifies as complete history.

## Known incomplete / unverified work

- Full dependency-resolved Gradle desktop build has not been executed on this host.
- OPENRNDR runtime launch and visual acceptance have not been exercised on this host.
- Native ORML inference is intentionally fail-closed/unbundled and remains unverified.
- Paid/network provider smoke tests have not been run.
- Gemini adapter hardening toward the current preferred API surface remains listed in `CAPABILITY_MATRIX.md`.
- Bulk premade community prompt import remains intentionally deferred pending per-source/per-entry provenance review.

## Recovery rule from this checkpoint

Preserve this commit before any redesign or dependency changes. Future repairs should be separate commits so recovered work and post-recovery work remain distinguishable.

## Next smallest action

Restore/verify a standard Gradle wrapper and attempt a dependency-resolved `./gradlew test`, classifying any failure as environment/dependency/implementation rather than rewriting code from the original design prompt.

---

## Recovery continuation — 2026-09-27T13:46Z

A second interruption was recovered without reconstructing source from prose.

Authority on re-entry:

- Durable local HEAD before recovery: `cb83308` (`feat(cosmosis): continue recovered image studio vertical slice`).
- Surviving branch: `recovery/interrupted-build-20260927`.
- Surviving uncommitted exact files: 5.
- `git fsck --full --no-reflogs`: PASS.
- Exact pre-checkpoint archive: `/mnt/data/cosmosis-image-studio.pre-recovery-20260927T134643Z.tar.gz`.
- Archive SHA-256: `c8f9a8436c9a1e2d3b227b49140fff9193513589843ba1ee89cdd0a5a49a57da`.

Recovered exact uncommitted files:

- `src/main/kotlin/studio/cosmosis/prompt/PromptExchange.kt`
- `src/main/kotlin/studio/cosmosis/provider/OpenAiProvider.kt`
- `src/main/kotlin/studio/cosmosis/storage/SqliteStore.kt`
- `src/main/kotlin/studio/cosmosis/ui/ControlDock.kt`
- `src/main/kotlin/studio/cosmosis/ui/StudioController.kt`

Verification before checkpoint:

```text
./scripts/core-smoke.sh            PASS / 14 checks / CORE_SMOKE_PASS
./scripts/static-audit.sh          PASS
./scripts/kotlinc-stub-check.sh    PASS / KOTLINC_STUB_CHECK_PASS
```

Recovered implementation represented by this checkpoint includes structured prompt exchange/persistence continuation and the beginning of executable Agent Build orchestration. It is preserved before further feature work.
