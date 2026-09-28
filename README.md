# Cosmosis Image Studio

Local-first AI-native image generation and editing workstation built around Kotlin, OPENRNDR, SQLite, non-destructive lineage and capability-driven provider adapters.

## Build

Requirements: JDK 21 and Gradle 9.8+. The repository intentionally does not vendor the Gradle wrapper JAR; `./gradlew` delegates to an installed Gradle. GitHub CI provisions Gradle 9.8.0 automatically.

```bash
./gradlew test
./gradlew acceptanceSmoke
./gradlew run
```

`acceptanceSmoke` is offline and uses the deterministic `local-preview-v1` provider. It exercises project creation, immutable source/reference imports, local analysis + derived overlays, prompt/directive persistence, smart masks and overlay settings, generation/edit workers, branching lineage, rotate/resize/upscale transforms, Agent Build + critic output, restart recovery, diagnostics, and HTML report export without provider credentials or paid calls.

Opt-in paid/network smoke checks:

```bash
COSMOSIS_LIVE_PROVIDER_TESTS=1 ./gradlew liveProviderSmoke
```

## Workstation workflows

The prompt workspace exposes capability-driven workflow modes rather than making provider APIs the primary mental model:

`QUICK_GENERATE`, `PRECISION_GENERATE`, `EDIT_EXISTING`, `MASK_EDIT`, `REFERENCE_REMIX`, `STYLE_TRANSFER`, `BACKGROUND_REPLACE`, `SUBJECT_PRESERVE`, `TEXT_POSTER`, `IMAGE_TO_PROMPT`, `UPSCALE`, and `AGENT_BUILD`.

Reference images are immutable project assets and are only attached to explicit reference workflows unless a caller opts in through canonical request metadata. Agent directives are project-local, inspectable, enable/disable-able records; enabled directives are included in Agent Build reports.

Interrupted or failed jobs are never auto-resumed. New jobs persist their canonical request, references, mask, metadata and budget so the user can explicitly replay a resumable job through the normal provider/result-admission path.

## OPENRNDR semantic build reference

The supplied OPENRNDR semantic knowledge tree is preserved conceptually in `docs/agent/openrndr/workstation.html`. It maps drawing/rendering, interaction, image/CV, animation, typography and GPU extension points to Cosmosis responsibilities. It is a selection guide, not a requirement to add every ORX module as a runtime dependency.

## Secrets

Use environment variables: `OPENAI_API_KEY`, `GEMINI_API_KEY`/`GOOGLE_API_KEY`, and `LITELLM_API_KEY`. LiteLLM uses `LITELLM_BASE_URL`. Secrets are never written to project data by design.

## Optional ORML adapters

Cosmosis keeps ORML model runtimes outside the core dependency graph. Optional local-ML integrations implement `studio.cosmosis.orml.OrmlAdapter` and register the implementation through Java `ServiceLoader` using:

```text
META-INF/services/studio.cosmosis.orml.OrmlAdapter
```

An adapter is reported `READY` only when it is actually present on the runtime classpath. Duplicate providers for one capability are rejected. File-producing adapters must return concrete files that exist before Cosmosis accepts the result. Without an adapter, the existing deterministic local saliency mask remains available; Cosmosis does not claim that fallback is ORML inference.

## Offline verification

The dependency-light domain can be exercised without Gradle/network:

```bash
./scripts/core-smoke.sh
```

For a full local verification pass, run `./scripts/core-smoke.sh`, `./scripts/static-audit.sh`, and `./gradlew test acceptanceSmoke`. See `CAPABILITY_MATRIX.md` for implemented versus still-unverified native/runtime/provider capabilities.
