# Cosmosis pinned FALSR-A Super Resolution backend

This optional distribution implements the protocol-v1 `super-resolution` capability using the FALSR-A graph pinned by upstream `openrndr/orml`.

## Pinned provenance

```text
ORML commit:      bb6333e62b17a9a0fc12ef889bc3642428e6f4f4
ORML source:      orml-super-resolution/src/main/kotlin/ImageUpscaler.kt
model:            FALSR-A-1.0
model URL:        https://mlmodels.openrndr.org/FALSR-A-1.0.pb
model SHA-256:    639cd2ea510990fa58855a7a15bd1ea0d8756b6e6ffe7a980d0427f61f2fb4a1
TensorFlow Java:  0.4.1
ORX lineage:      0a24780d8446f998b9f13b61a328b97d91878500
```

The model is not downloaded during backend discovery. `COSMOSIS_ORML_SUPER_RESOLUTION_MODEL` must point to the exact SHA-256-pinned graph.

## Graph / color contract

The backend independently reproduces ORML's headless FALSR preprocessing:

- luminance: `Y = 0.2126R + 0.7152G + 0.0722B`;
- `Pb = 0.5(B-Y)/(1-0.0722)`;
- `Pr = 0.5(R-Y)/(1-0.2126)`;
- the two chroma channels are nearest-neighbor upscaled 2× before inference;
- graph inputs are `input_image_evaluate_y` and `input_image_evaluate_pbpr`;
- graph output is `test_sr_evaluator_i1_b0_g/target`.

The normal editor path requests one octave (2×). The backend accepts 1–3 octaves but also enforces an 8192-pixel side limit and a 16,777,216-pixel neural output budget.

## Build

```bash
./gradlew :orml-runner-super-resolution:test
./gradlew :orml-runner-super-resolution:installDist
./gradlew :orml-runner-super-resolution:installDist -PcosmosisSuperResolutionTensorFlow=true
```

The opt-in runtime adds TensorFlow Java 0.4.1 plus the matched x86_64 native classifier.

## Model acquisition

```bash
MODEL_PATH="$(bash scripts/fetch-super-resolution-model.sh)"
export COSMOSIS_ORML_SUPER_RESOLUTION_MODEL="$MODEL_PATH"
```

## Native verification

Ordinary CI proves compile/package behavior and the fail-closed unavailable state.

Real FALSR inference is opt-in:

```bash
COSMOSIS_SUPER_RESOLUTION_NATIVE_TESTS=1 bash scripts/super-resolution-native-smoke.sh input.png
```

Native verified on Linux x86_64 / TensorFlow Java 0.4.1: workflow run `36640856920`, job `109652521802`, produced a valid 2× PNG and passed desktop `ormlSuperResolution()` non-destructive UPSCALE admission. See [evidence and limitations](../docs/verification/super-resolution-native-2026-09-29.md). Other platforms, GPU, recursive native octaves, maximum-size workloads, and perceptual quality remain outside this verification.
