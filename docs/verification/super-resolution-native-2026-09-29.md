# FALSR Super Resolution Native Verification — 2026-09-29

## Result

**PASS — Linux x86_64 / TensorFlow Java 0.4.1 CPU runtime / one 2× octave**

## Code and execution provenance

- Main checkpoint: `5d329915f95d55b33be7bb0b5210e0b8b30ce174`
- Tested commit: `2313a579dbaf3719a425f211f1eb30781b6e03f1`
- Isolated branch: `verify/falsr-native-20260929`
- Workflow: `.github/workflows/super-resolution-native.yml`
- [Run 36640856920](https://github.com/es-3581100/cosmosis-image-studio/actions/runs/36640856920)
- [Job 109652521802](https://github.com/es-3581100/cosmosis-image-studio/actions/runs/36640856920/job/109652521802)
- Ubuntu 24.04 x86_64; Temurin Java 21.0.12+1; Gradle 9.8.0
- Only the branch-specific push trigger differs from main. That trigger is not promoted to main.
- Previous classifier checkpoint PR #12 passed full PR CI (36640632552) and post-merge main CI (36640827362).

## Runtime/model identity

- ORML commit: `bb6333e62b17a9a0fc12ef889bc3642428e6f4f4`
- ORX lineage: `0a24780d8446f998b9f13b61a328b97d91878500`
- TensorFlow Java: `0.4.1`, native classifier `linux-x86_64`
- Model: `FALSR-A-1.0`
- SHA-256: `639cd2ea510990fa58855a7a15bd1ea0d8756b6e6ffe7a980d0427f61f2fb4a1`
- Inputs: `input_image_evaluate_y`, `input_image_evaluate_pbpr`
- Output: `test_sr_evaluator_i1_b0_g/target`

## Evidence

The successful job built the opt-in TensorFlow distribution and tests, fetched and SHA-256-verified the pinned model, and generated a deterministic 64×48 PNG input. Protocol-v1 `--describe` reported `super-resolution` with `available:true` and no discovery errors. Real TensorFlow inference produced a 128×96 PNG. RunnerEngine decoded the output through ImageIO before admitting it; the native smoke additionally asserted the PNG signature and exact 2× dimensions.

The workflow exported `COSMOSIS_ORML_SUPER_RESOLUTION_RUNNER` before executing `acceptanceSmoke`. The existing environment-gated branch invoked `StudioController.ormlSuperResolution()`, required the output file, checked 192×128 → 384×256 dimensions and an UPSCALE version node, then restored the original version and its original dimensions. Acceptance completed with ten version nodes.

Selected timestamped job-log evidence:

```text
2026-09-29T22:40:32.8546797Z OK pinned FALSR-A super-resolution generated
2026-09-29T22:40:32.8550852Z META backend=falsr-a-tensorflow-pinned
2026-09-29T22:40:32.8553284Z META height=96
2026-09-29T22:40:32.8556171Z META model=FALSR-A-1.0
2026-09-29T22:40:32.8559154Z META modelSha256=639cd2ea510990fa58855a7a15bd1ea0d8756b6e6ffe7a980d0427f61f2fb4a1
2026-09-29T22:40:32.8570869Z META protocolVersion=1
2026-09-29T22:40:32.8571771Z META scale=2
2026-09-29T22:40:32.8573504Z META upstreamCommit=bb6333e62b17a9a0fc12ef889bc3642428e6f4f4
2026-09-29T22:40:32.8574315Z META width=128
2026-09-29T22:40:32.8845534Z SUPER_RESOLUTION_NATIVE_PNG_OK 64 48 128 96
2026-09-29T22:40:32.8880462Z SUPER_RESOLUTION_NATIVE_SMOKE_OK /tmp/cosmosis-falsr-output.png
2026-09-29T22:41:11.9588419Z ACCEPTANCE_SMOKE_PASS project=/tmp/cosmosis-acceptance-4860846186903500640/project versions=10
```

## Exact claim and limitations

Verified: pinned FALSR-A model identity, protocol readiness, real one-octave inference, decodable 2× PNG admission, and desktop non-destructive UPSCALE controller/lineage acceptance on Linux x86_64 with TensorFlow Java 0.4.1.

This is headless controller acceptance, not a new UI rendering test or a perceptual image-quality benchmark. macOS, Windows, ARM, GPU, two/three native octaves, maximum-size workloads, and performance/resource-limit behavior at those sizes remain unverified. Existing implementation bounds remain 1–3 octaves, 8192 pixels per side, and 16,777,216 output pixels; this smoke does not prove all boundary workloads. Ordinary CI remains fail-closed and does not gain native inference.
