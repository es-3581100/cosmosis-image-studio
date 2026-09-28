# Cosmosis Image Studio

Local-first AI-native image generation and editing workstation built around Kotlin, OPENRNDR, SQLite, non-destructive lineage and capability-driven provider adapters.

## Build

Requirements: JDK 21 and Gradle 9.8+. The repository intentionally does not vendor the Gradle wrapper JAR; `./gradlew` delegates to an installed Gradle. GitHub CI provisions Gradle 9.8.0 automatically.

```bash
./gradlew test
./gradlew acceptanceSmoke
./gradlew run
```

`acceptanceSmoke` is offline and uses the deterministic `local-preview-v1` provider. It exercises project creation, image import, local analysis, prompt persistence, smart mask creation, generation/edit workers, branching lineage, Agent Build + critic output, restart recovery, diagnostics, and HTML report export without provider credentials or paid calls.

Opt-in paid/network smoke checks:

```bash
COSMOSIS_LIVE_PROVIDER_TESTS=1 ./gradlew liveProviderSmoke
```

## Secrets

Use environment variables: `OPENAI_API_KEY`, `GEMINI_API_KEY`/`GOOGLE_API_KEY`, and `LITELLM_API_KEY`. LiteLLM uses `LITELLM_BASE_URL`. Secrets are never written to project data by design.

## Offline verification

The dependency-light domain can be exercised without Gradle/network:

```bash
./scripts/core-smoke.sh
```

For a full local verification pass, run `./scripts/core-smoke.sh`, `./scripts/static-audit.sh`, and `./gradlew test acceptanceSmoke`. See `CAPABILITY_MATRIX.md` for implemented versus still-unverified native/runtime/provider capabilities.
