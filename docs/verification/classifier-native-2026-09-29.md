# Image Classifier Native Verification — 2026-09-29

## Result

**PASS — Linux x86_64 / TensorFlow Java 0.4.1 CPU runtime**

## Code and execution provenance

- Main implementation: `f4ed10a24c3e0accf9d0890100632abaa17a1ac2`
- Tested commit: `27832a15602dba0f2013242d8f1f2b74b8c42270`
- Isolated branch: `verify/classifier-native-20260929`
- Workflow: `.github/workflows/classifier-native.yml`
- [Run 36640311752](https://github.com/es-3581100/cosmosis-image-studio/actions/runs/36640311752)
- [Job 109650773799](https://github.com/es-3581100/cosmosis-image-studio/actions/runs/36640311752/job/109650773799)
- Ubuntu 24.04 x86_64; Temurin Java 21.0.12+1; Gradle 9.8.0
- Only the branch-specific push trigger differs from main. That trigger is not promoted to main.

## Runtime/model identity

- ORML commit: `bb6333e62b17a9a0fc12ef889bc3642428e6f4f4`
- ORX lineage: `0a24780d8446f998b9f13b61a328b97d91878500`
- TensorFlow Java: `0.4.1`, native classifier `linux-x86_64`
- Model path: `orml-image-classifier/src/main/resources/tfmodels/v3-large-minimalistic_224_1.0_float.pb`
- Git blob SHA-1: `2e03a7f49b2bf46dc04349d8af9b669d2fca484e`
- Size: 15,923,156 bytes
- Input: `input`
- Classification output: `MobilenetV3/Predictions/Softmax`
- Embedding output: `MobilenetV3/Logits/Conv2d_1c_1x1/BiasAdd`

## Evidence

The successful job built the opt-in TensorFlow distribution and tests, fetched and verified the model's size and Git object hash, and generated the deterministic 224×224 input. Protocol-v1 `--describe` reported `image-embedding` with `available:true` and no discovery errors. Real TensorFlow inference produced JSON admitted by the runner; the smoke checked five top entries, nonzero dimensions, matching embedding length, and `labelsResolved:false`.

The workflow exported `COSMOSIS_ORML_CLASSIFIER_RUNNER` before executing `acceptanceSmoke`. The existing environment-gated acceptance branch invoked `StudioController.ormlImageEmbedding()`, required a real metadata file with classification and embedding fields, and completed successfully.

Selected timestamped job-log evidence:

```text
2026-09-29T22:34:48.7319739Z OK pinned MobileNetV3 embedding generated
2026-09-29T22:34:48.7324299Z META backend=mobilenetv3-classifier-tensorflow-pinned
2026-09-29T22:34:48.7326544Z META classificationDimension=1001
2026-09-29T22:34:48.7328780Z META embeddingDimension=1001
2026-09-29T22:34:48.7330491Z META labelsResolved=false
2026-09-29T22:34:48.7333174Z META model=v3-large-minimalistic_224_1.0_float
2026-09-29T22:34:48.7335362Z META modelGitBlobSha1=2e03a7f49b2bf46dc04349d8af9b669d2fca484e
2026-09-29T22:34:48.7337123Z META protocolVersion=1
2026-09-29T22:34:48.7339333Z META upstreamCommit=bb6333e62b17a9a0fc12ef889bc3642428e6f4f4
2026-09-29T22:34:48.7635085Z CLASSIFIER_NATIVE_JSON_OK 1001 1001
2026-09-29T22:34:48.7663517Z CLASSIFIER_NATIVE_SMOKE_OK /tmp/cosmosis-classifier-output.json
2026-09-29T22:35:17.3248913Z ACCEPTANCE_SMOKE_PASS project=/tmp/cosmosis-acceptance-1772380259778681998/project versions=9
```

## Exact claim and limitations

Verified: the pinned classifier/embedding backend, readiness, real inference, generated JSON artifact admission, and desktop embedding metadata acceptance on Linux x86_64 with TensorFlow Java 0.4.1. This is headless controller acceptance, not a new UI rendering test.

The observed classification and embedding dimensions are both 1001. Label names are not bundled. This smoke establishes execution and admission, not classification accuracy or semantic retrieval quality. macOS, Windows, ARM, and GPU execution remain unverified. Ordinary CI remains fail-closed and does not gain native inference. FALSR verification is separate.
