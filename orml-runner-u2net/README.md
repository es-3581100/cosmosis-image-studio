# Cosmosis pinned U2Net backend

This optional distribution is the first concrete model backend for the protocol-v1 `orml-runner`.

It implements `smart-subject-mask` using the U2Net graph contract and pinned model used by upstream `openrndr/orml`.

## Provenance pin

```text
upstream repo:   https://github.com/openrndr/orml
upstream commit: bb6333e62b17a9a0fc12ef889bc3642428e6f4f4
model:           u2netp-320x320-float32-1.0
model URL:       https://mlmodels.openrndr.org/u2netp-320x320-float32-1.0.pb
SHA-256:         79e8757f7c5342c4f2b0dab4c66c4bc128b077c9541db5c6a5b363ff75c8b54a
input tensor:    inputs
output tensor:   functional_1/tf_op_layer_Sigmoid_6/Sigmoid_6
```

The implementation does not call upstream `fetchORMLModel()`. Model acquisition is explicit and the SHA-256 is checked before ServiceLoader admits the backend.

## Why TensorFlow-only

Upstream ORML U2Net moves pixels through OPENRNDR GPU buffers. The Cosmosis runner is intentionally headless/isolated, so this backend reproduces the same preprocessing and TensorFlow graph contract with Java `BufferedImage` instead of requiring an OPENGL window/context.

Preprocessing follows the upstream source: resize to 320×320, RGB float channels, scale by 2.0, then offset by -1.0.

Upstream ORML also performs vertical flips when moving between OPENRNDR GPU coordinates and TensorFlow and flips the matte back on output. This backend stays entirely in conventional top-down CPU image coordinates, so those two compensating GPU-orientation transforms are intentionally absent. Native verification remains authoritative for behavioral equivalence.

The sigmoid matte is converted to an 8-bit grayscale PNG and resized back to the source dimensions. The runner independently verifies that the mask is decodable and exactly matches source dimensions.

## Runtime prerequisites

The backend is dependency-light at compile time. The matched upstream ORX lineage uses TensorFlow Java **0.4.1**. Build an opt-in native distribution with `-PcosmosisU2NetTensorFlow=true`; this adds `org.tensorflow:tensorflow-core-api:0.4.1` plus the matching x86_64 native classifier for Linux, macOS, or Windows.

Readiness also requires:

```bash
export COSMOSIS_ORML_U2NET_MODEL=/absolute/path/u2netp-320x320-float32-1.0.pb
```

`TensorFlow.version()` is invoked during backend admission. Missing Java classes, missing native linkage, a missing model, or a model hash mismatch makes the capability unavailable instead of READY.

## Build

```bash
./gradlew :orml-runner-u2net:test
./gradlew :orml-runner-u2net:installDist
./gradlew :orml-runner-u2net:installDist -PcosmosisU2NetTensorFlow=true
```

Without the model/native runtime the generated runner is expected to report `smart-subject-mask` as unavailable and include a sanitized discovery error. Ordinary CI verifies that fail-closed state; the native runtime is not silently bundled into the desktop application.

## Native verification

A native smoke is intentionally separate from ordinary offline CI.

After installing a compatible TensorFlow runtime into the distribution classpath and setting `COSMOSIS_ORML_U2NET_MODEL`:

```bash
COSMOSIS_U2NET_NATIVE_TESTS=1 bash scripts/u2net-native-smoke.sh input.png
```

Do not mark native U2Net as verified until that smoke produces a same-size PNG mask and the desktop accepts it through the normal ORML process-admission path.
