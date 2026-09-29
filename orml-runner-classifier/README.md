# Cosmosis pinned Image Classifier / Embedding backend

This optional distribution implements the protocol-v1 `image-embedding` capability using the MobileNetV3 graph bundled by upstream `openrndr/orml`.

## Pinned provenance

```text
ORML commit:      bb6333e62b17a9a0fc12ef889bc3642428e6f4f4
ORML source:      orml-image-classifier/src/main/kotlin/ImageClassifier.kt
model path:       orml-image-classifier/src/main/resources/tfmodels/v3-large-minimalistic_224_1.0_float.pb
model Git blob:   2e03a7f49b2bf46dc04349d8af9b669d2fca484e
model size:       15,923,156 bytes
TensorFlow Java:  0.4.1
ORX lineage:      0a24780d8446f998b9f13b61a328b97d91878500
```

The model is not silently downloaded during backend discovery. `COSMOSIS_ORML_CLASSIFIER_MODEL` must point to the exact pinned bytes. Admission verifies both file size and the canonical Git blob SHA-1.

## Graph contract

```text
input:           input
classification:  MobilenetV3/Predictions/Softmax
embedding:       MobilenetV3/Logits/Conv2d_1c_1x1/BiasAdd
input shape:     1 × 224 × 224 × 3
```

Preprocessing follows the upstream classifier contract:

- bilinear resize to 224×224;
- RGB float channels in `0..1`;
- explicit vertical flip matching ORML's negative-height copy into its input buffer.

## Output contract

The backend writes JSON containing:

- complete embedding values;
- classification vector dimension;
- top class indices and scores;
- generated tags such as `imagenet-index:281`.

It intentionally does **not** copy ORML's 1000-label name table into Cosmosis. Numeric class IDs remain generated metadata and never overwrite user-authored tags. A separately pinned label dictionary can be added later without changing the embedding contract.

## Build

```bash
./gradlew :orml-runner-classifier:test
./gradlew :orml-runner-classifier:installDist
./gradlew :orml-runner-classifier:installDist -PcosmosisClassifierTensorFlow=true
```

The opt-in TensorFlow build adds `org.tensorflow:tensorflow-core-api:0.4.1` plus the matching x86_64 native classifier for Linux, macOS or Windows.

## Model acquisition

```bash
MODEL_PATH="$(bash scripts/fetch-classifier-model.sh)"
export COSMOSIS_ORML_CLASSIFIER_MODEL="$MODEL_PATH"
```

The helper downloads from the exact pinned GitHub commit and verifies the Git blob identity before returning the path.

## Native verification

Ordinary CI proves that the distribution compiles/packages and remains unavailable when the model/native runtime are absent.

Real inference is opt-in:

```bash
COSMOSIS_CLASSIFIER_NATIVE_TESTS=1 bash scripts/classifier-native-smoke.sh input.png
```

Do not mark this backend VERIFIED until real model inference produces valid JSON and Cosmosis admits that JSON through `ormlImageEmbedding()`.
