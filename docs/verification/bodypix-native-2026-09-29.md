# BodyPix Native Verification — 2026-09-29

## Result

**PASS — Linux x86_64 / TensorFlow Java 0.4.1**

This checkpoint verifies the pinned `person-body-mask` backend with real BodyPix MobileNet inference and the Cosmosis desktop admission path.

## Code under test

- main implementation checkpoint: `a3a1a611361425d530aa9c3f51d70986351c68b9`
- verification branch: `verify/bodypix-native`
- workflow-only trigger commit used by the successful run: `86d0f348070b5f5d500be641694ab6891d0e2885`
- workflow: `.github/workflows/bodypix-native.yml`
- workflow run: `36614548554`
- workflow job: `109564100791`

The verification branch changed only the trigger condition required to invoke the existing manual native workflow from this connected build session. It is not intended for promotion to `main`.

## Runtime pin

- ORML repository: `openrndr/orml`
- ORML commit: `bb6333e62b17a9a0fc12ef889bc3642428e6f4f4`
- architecture: MobileNet
- ORX TensorFlow lineage commit: `0a24780d8446f998b9f13b61a328b97d91878500`
- TensorFlow Java: `0.4.1`
- native classifier exercised: `linux-x86_64`
- model: `bodypix-mobilenet-1.0`
- model SHA-256: `c64d6f3252217f9bd0ba790ac2a6ac8b45fc002767c97379cb2e8a3ce7317b56`
- graph input: `sub_2`
- graph output: `float_segments`
- segmentation threshold: `0.7`
- internal resolution: `0.5`

## Evidence

The successful job completed all of these gates:

1. Built `:orml-runner-bodypix:test` and `:orml-runner-bodypix:installDist` with `-PcosmosisBodyPixTensorFlow=true`.
2. Downloaded the pinned MobileNet model with `scripts/fetch-bodypix-model.sh` and accepted it only after SHA-256 verification.
3. Generated a deterministic PNG input fixture.
4. Ran the generated BodyPix runner `--describe` and received:
   - protocol version `1`;
   - `person-body-mask` with `available: true`;
   - no discovery errors.
5. Ran real TensorFlow BodyPix inference against `float_segments`.
6. The runner returned:
   - `OK pinned BodyPix person mask generated`;
   - backend `bodypix-mobilenet-tensorflow-pinned`;
   - the pinned model name;
   - the pinned model SHA-256;
   - protocol version `1`.
7. The generic runner artifact admission accepted the generated person mask, proving a real decodable same-size mask artifact.
8. Exported the runner path as `COSMOSIS_ORML_BODYPIX_RUNNER`.
9. Ran `acceptanceSmoke` with the real BodyPix runner configured.
10. The environment-gated acceptance branch invoked `StudioController.ormlPersonMask()`, persisted the mask, and completed successfully.
11. `ACCEPTANCE_SMOKE_PASS` was emitted with nine version nodes.
12. The native workflow job concluded `success`.

The TensorFlow runtime reported a CPU build using oneDNN with AVX2, AVX512F, and FMA available on the GitHub runner.

## Scope of claim

Verified:

- Linux x86_64
- TensorFlow Java 0.4.1 CPU native runtime
- pinned BodyPix MobileNet model
- protocol-v1 readiness
- real `float_segments` inference
- binary person-mask artifact admission
- Cosmosis desktop `ormlPersonMask()` integration
- acceptance/lineage persistence path

Not yet verified:

- macOS x86_64 native runtime
- Windows x86_64 native runtime
- GPU TensorFlow backend
- BodyPix pose/keypoint inference
- BodyPix 24-part decoding
- image classifier backend
- super-resolution backend

The omitted BodyPix surfaces are intentional: Cosmosis currently exposes this backend only as `person-body-mask`.
