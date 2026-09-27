# Interrupted Build Recovery Manifest

Recovery date: 2026-09-27

RECOVERY_DISPOSITION: RECOVERED

Durable local checkpoint before publish continuation:
- local branch: recovery/interrupted-build-20260927
- local checkpoint: f4b0dcab9c5734f0145ac8e482caaed0431745d7
- prior durable local checkpoint: cb83308
- exact pre-checkpoint archive: cosmosis-image-studio.pre-recovery-20260927T134643Z.tar.gz
- archive SHA-256: c8f9a8436c9a1e2d3b227b49140fff9193513589843ba1ee89cdd0a5a49a57da

Recovered exact interrupted files:
- src/main/kotlin/studio/cosmosis/prompt/PromptExchange.kt
- src/main/kotlin/studio/cosmosis/provider/OpenAiProvider.kt
- src/main/kotlin/studio/cosmosis/storage/SqliteStore.kt
- src/main/kotlin/studio/cosmosis/ui/ControlDock.kt
- src/main/kotlin/studio/cosmosis/ui/StudioController.kt

Verification before checkpoint:
- ./scripts/core-smoke.sh : PASS / CORE_SMOKE_PASS
- ./scripts/static-audit.sh : PASS
- ./scripts/kotlinc-stub-check.sh : PASS / KOTLINC_STUB_CHECK_PASS

This remote recovery marker was intentionally created before further implementation so the resumed work has a durable external checkpoint. The complete source tree will replace/extend this marker during the final publish pass.
