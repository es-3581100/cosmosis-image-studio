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

An in-process adapter is reported `READY` only when it is actually present on the runtime classpath. Duplicate providers for one capability are rejected. File-producing adapters must return concrete files that exist before Cosmosis accepts the result.

For isolated native runtimes, this repository now includes the separate `orml-runner` application:

```bash
./gradlew :orml-runner:test
./gradlew :orml-runner:installDist
orml-runner/build/install/orml-runner/bin/orml-runner --describe
```

A configured executable is not considered `READY` merely because the file exists. Cosmosis runs the protocol-v1 `--describe` handshake and requires that the exact requested capability report `available: true`. The runner then independently validates mask/image/JSON output artifacts, and the desktop boundary validates them again after execution.

Without an admitted adapter/backend, the existing deterministic local saliency mask remains available; Cosmosis does not claim that fallback is ORML inference.

### Pinned U2Net backend

The repository also contains an isolated `orml-runner-u2net` distribution for `smart-subject-mask`. It is grounded in ORML commit `bb6333e62b17a9a0fc12ef889bc3642428e6f4f4`, the upstream U2Net graph/tensor names, the model SHA-256, and the matching ORX TensorFlow Java 0.4.1 lineage.

Ordinary CI intentionally does not run native inference. It verifies the backend compiles, packages, and remains fail-closed when the model/native runtime are absent.

```bash
./gradlew :orml-runner-u2net:test
./gradlew :orml-runner-u2net:installDist
./gradlew :orml-runner-u2net:installDist -PcosmosisU2NetTensorFlow=true
```

Fetch the pinned model explicitly:

```bash
MODEL_PATH="$(bash scripts/fetch-u2net-model.sh)"
export COSMOSIS_ORML_U2NET_MODEL="$MODEL_PATH"
```

Then, only when native testing is intentionally enabled:

```bash
COSMOSIS_U2NET_NATIVE_TESTS=1 bash scripts/u2net-native-smoke.sh input.png
```

Native verification has passed on the GitHub Linux x86_64 runner with TensorFlow Java 0.4.1. Workflow run `36612354676` verified the pinned model hash, protocol-v1 READY handshake, real U2Net inference, valid mask output, and Cosmosis desktop `acceptanceSmoke` with `COSMOSIS_ORML_U2NET_RUNNER` configured. This evidence is recorded in `docs/verification/u2net-native-2026-09-29.md`.

This is a platform-scoped verification claim: macOS and Windows native classifiers remain unverified until the same native workflow is run successfully there.


### Pinned BodyPix backend

The optional `orml-runner-bodypix` distribution implements only the workstation's `person-body-mask` surface. It is grounded in the MobileNet BodyPix graph from the same pinned ORML commit, model `bodypix-mobilenet-1.0`, model SHA-256 `c64d6f3252217f9bd0ba790ac2a6ac8b45fc002767c97379cb2e8a3ce7317b56`, and TensorFlow Java 0.4.1 lineage.

```bash
./gradlew :orml-runner-bodypix:test
./gradlew :orml-runner-bodypix:installDist
./gradlew :orml-runner-bodypix:installDist -PcosmosisBodyPixTensorFlow=true
MODEL_PATH="$(bash scripts/fetch-bodypix-model.sh)"
export COSMOSIS_ORML_BODYPIX_MODEL="$MODEL_PATH"
```

The default segmentation threshold is `0.7` and the default internal resolution is `0.5`; both can be supplied as runner options. Ordinary CI proves the backend stays fail-closed without its pinned model/native runtime. Real inference is opt-in:

```bash
COSMOSIS_BODYPIX_NATIVE_TESTS=1 bash scripts/bodypix-native-smoke.sh input.png
```

Native BodyPix execution is verified on the GitHub Linux x86_64 runner with TensorFlow Java 0.4.1. Workflow run `36614548554` verified the pinned model hash, protocol-v1 READY handshake, real `float_segments` inference, valid same-size person mask output, and Cosmosis desktop `ormlPersonMask()` admission through `acceptanceSmoke`.

This evidence is recorded in `docs/verification/bodypix-native-2026-09-29.md`. The claim is platform-scoped: macOS, Windows, and GPU BodyPix variants remain unverified.


### Pinned Image Classifier / Embedding backend

The optional `orml-runner-classifier` distribution implements `image-embedding` using the MobileNetV3 graph bundled by pinned ORML commit `bb6333e62b17a9a0fc12ef889bc3642428e6f4f4`.

Its model is pinned by Git object identity rather than a guessed external checksum:

```text
model path:      orml-image-classifier/src/main/resources/tfmodels/v3-large-minimalistic_224_1.0_float.pb
Git blob SHA-1:  2e03a7f49b2bf46dc04349d8af9b669d2fca484e
size:            15,923,156 bytes
TensorFlow Java: 0.4.1
```

```bash
./gradlew :orml-runner-classifier:test
./gradlew :orml-runner-classifier:installDist
./gradlew :orml-runner-classifier:installDist -PcosmosisClassifierTensorFlow=true
MODEL_PATH="$(bash scripts/fetch-classifier-model.sh)"
export COSMOSIS_ORML_CLASSIFIER_MODEL="$MODEL_PATH"
```

The backend emits full embedding values plus top ImageNet class **indices/scores** as generated metadata. It intentionally does not copy the upstream 1000-label name table into Cosmosis, and generated class metadata never overwrites user-authored tags.

Real inference is opt-in:

```bash
COSMOSIS_CLASSIFIER_NATIVE_TESTS=1 bash scripts/classifier-native-smoke.sh input.png
```

Native classifier execution is verified on Linux x86_64 with TensorFlow Java 0.4.1. [Workflow run 36640311752](https://github.com/es-3581100/cosmosis-image-studio/actions/runs/36640311752) verified the pinned model identity, readiness, real classification/embedding JSON, runner admission, and desktop `ormlImageEmbedding()` through `acceptanceSmoke`. See [the verification checkpoint](docs/verification/classifier-native-2026-09-29.md). Ordinary CI remains packaging/fail-closed only; macOS, Windows, and GPU execution remain unverified.


### Pinned FALSR-A Super Resolution backend

The optional `orml-runner-super-resolution` distribution implements `super-resolution` from the FALSR-A graph pinned by ORML:

```text
model:            FALSR-A-1.0
model SHA-256:    639cd2ea510990fa58855a7a15bd1ea0d8756b6e6ffe7a980d0427f61f2fb4a1
TensorFlow Java:  0.4.1
graph scale:      2× per octave
```

The backend independently reproduces ORML's Y/Pb/Pr preprocessing and nearest-neighbor 2× chroma preparation without requiring an OPENGL context.

```bash
./gradlew :orml-runner-super-resolution:test
./gradlew :orml-runner-super-resolution:installDist
./gradlew :orml-runner-super-resolution:installDist -PcosmosisSuperResolutionTensorFlow=true
MODEL_PATH="$(bash scripts/fetch-super-resolution-model.sh)"
export COSMOSIS_ORML_SUPER_RESOLUTION_MODEL="$MODEL_PATH"
```

The normal editor path uses one octave (2×). The runner permits up to three octaves while enforcing both an 8192-pixel side bound and a 16,777,216-pixel neural output budget.

Real inference is opt-in:

```bash
COSMOSIS_SUPER_RESOLUTION_NATIVE_TESTS=1 bash scripts/super-resolution-native-smoke.sh input.png
```

Ordinary CI proves packaging and fail-closed readiness only. Native FALSR execution is not marked VERIFIED until the manual workflow produces a valid 2× PNG and Cosmosis admits it through `ormlSuperResolution()` as an UPSCALE child version.


## Offline verification

The dependency-light domain can be exercised without Gradle/network:

```bash
./scripts/core-smoke.sh
```

For a full local verification pass, run `./scripts/core-smoke.sh`, `./scripts/static-audit.sh`, and `./gradlew test acceptanceSmoke`. See `CAPABILITY_MATRIX.md` for implemented versus still-unverified native/runtime/provider capabilities.
