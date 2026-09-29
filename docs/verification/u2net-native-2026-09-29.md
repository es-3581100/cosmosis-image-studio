# U2Net Native Verification — 2026-09-29

## Result

**PASS — Linux x86_64 / TensorFlow Java 0.4.1**

This checkpoint verifies the pinned `smart-subject-mask` backend with real model inference and the Cosmosis desktop admission path.

## Code under test

- main implementation checkpoint: `8c08c1dcc053e27adc091ea876173a86deb5257e`
- verification branch: `verify/u2net-native`
- workflow-only trigger commit used by the successful run: `96dc1ab2e4dac2a2bd31c935cac6a370cbcfc25f`
- workflow: `.github/workflows/u2net-native.yml`
- workflow run: `36612354676`
- workflow job: `109556690533`

The verification branch changed only the trigger condition needed to invoke the existing manual native workflow from this connected build session. That trigger-only branch is not required for promotion to `main`.

## Runtime pin

- ORML repository: `openrndr/orml`
- ORML commit: `bb6333e62b17a9a0fc12ef889bc3642428e6f4f4`
- ORX TensorFlow lineage commit: `0a24780d8446f998b9f13b61a328b97d91878500`
- TensorFlow Java: `0.4.1`
- native classifier exercised: `linux-x86_64`
- model: `u2netp-320x320-float32-1.0`
- model SHA-256: `79e8757f7c5342c4f2b0dab4c66c4bc128b077c9541db5c6a5b363ff75c8b54a`

## Evidence

The successful job completed all of these gates:

1. Built `:orml-runner-u2net:test` and `:orml-runner-u2net:installDist` with `-PcosmosisU2NetTensorFlow=true`.
2. Downloaded the pinned model through `scripts/fetch-u2net-model.sh` and accepted it only after SHA-256 verification.
3. Generated a deterministic PNG input fixture.
4. Ran the generated U2Net runner `--describe` and received:
   - protocol version `1`;
   - `smart-subject-mask` with `available: true`;
   - no discovery errors.
5. Ran real TensorFlow U2Net inference.
6. The runner returned:
   - `OK pinned U2Net subject mask generated`;
   - backend `u2net-tensorflow-pinned`;
   - the pinned model name;
   - the pinned model SHA-256;
   - protocol version `1`.
7. The protocol runner's artifact admission accepted the generated mask, which means it was a real decodable image and matched the source dimensions.
8. Exported the generated runner path as `COSMOSIS_ORML_U2NET_RUNNER`.
9. Ran `acceptanceSmoke` with the real runner configured.
10. Cosmosis desktop admission and derived-mask lineage path passed.
11. The full native workflow job concluded `success`.

The TensorFlow runtime reported a CPU build using oneDNN and AVX2/FMA on the GitHub Linux runner.

## Scope of claim

Verified:

- Linux x86_64
- TensorFlow Java 0.4.1 CPU native runtime
- pinned U2Net model
- protocol-v1 readiness
- real inference
- mask artifact admission
- Cosmosis desktop process integration
- acceptance/lineage path

Not yet verified:

- macOS x86_64 native runtime
- Windows x86_64 native runtime
- GPU TensorFlow backend
- BodyPix
- image classifier
- super-resolution

Those remain separate platform/backend verification tasks and must not inherit the Linux U2Net verification claim.
