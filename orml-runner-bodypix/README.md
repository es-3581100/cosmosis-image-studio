# Cosmosis pinned BodyPix backend

This optional distribution implements the protocol-v1 `person-body-mask` capability using the MobileNet BodyPix graph contract grounded in upstream `openrndr/orml`.

## Provenance pin

```text
upstream repo:   https://github.com/openrndr/orml
upstream commit: bb6333e62b17a9a0fc12ef889bc3642428e6f4f4
model:           bodypix-mobilenet-1.0
model URL:       https://mlmodels.openrndr.org/bodypix-mobilenet-1.0.pb
SHA-256:         c64d6f3252217f9bd0ba790ac2a6ac8b45fc002767c97379cb2e8a3ce7317b56
input tensor:    sub_2
output tensor:   float_segments
TensorFlow:      0.4.1 lineage via ORX commit 0a24780d8446f998b9f13b61a328b97d91878500
```

## Person-mask semantics

The backend uses the MobileNet normalization path from upstream ORML:

```text
RGB [0,1] -> multiply by 2 -> subtract 1
```

Only the `float_segments` probability surface is consumed. Pose heatmaps, offsets, displacements and 24-part decoding are intentionally not imported because Cosmosis exposes this backend only as `person-body-mask`.

Cosmosis defaults to a segmentation threshold of `0.7`, matching the BodyPix reference API. The threshold can be overridden through the runner option `threshold=<0..1>`.

The default internal resolution is `0.5`, with the longest inference side bounded to `1025` pixels. Both are explicit runner options and affect local compute cost only; the final binary PNG mask is resized back to the exact source dimensions and revalidated by the generic runner.

## Runtime prerequisites

Build the opt-in TensorFlow distribution with:

```bash
./gradlew :orml-runner-bodypix:installDist -PcosmosisBodyPixTensorFlow=true
```

Set the pinned model explicitly:

```bash
export COSMOSIS_ORML_BODYPIX_MODEL=/absolute/path/bodypix-mobilenet-1.0.pb
```

Missing model/runtime classes, native linkage failure or a model hash mismatch makes the backend unavailable instead of READY.

## Native verification

Ordinary CI compiles/tests/packages this backend and expects it to stay fail-closed without native prerequisites. Real inference remains opt-in:

```bash
COSMOSIS_BODYPIX_NATIVE_TESTS=1 bash scripts/bodypix-native-smoke.sh input.png
```

Do not mark BodyPix native inference VERIFIED until the real-model smoke and Cosmosis desktop `person-body-mask` admission path both pass.
