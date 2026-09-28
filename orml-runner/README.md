# Cosmosis ORML Runner

This subproject is the isolated executable boundary between Cosmosis Image Studio and optional legacy/native ORML model stacks.

The desktop app does **not** load TensorFlow/model code to discover a capability. Instead, it can launch a runner executable with a fixed argv contract:

```text
orml-runner   --capability <id>   --input <absolute-input-file>   --output <absolute-output-file>   [--option key=value]...
```

Supported protocol-v1 capability IDs:

- `smart-subject-mask`
- `person-body-mask`
- `image-embedding`
- `super-resolution`

## Build

```bash
gradle :orml-runner:test
gradle :orml-runner:installDist
```

The generated launcher is under:

```text
orml-runner/build/install/orml-runner/bin/orml-runner
```

That launcher is suitable for one of the Cosmosis environment variables:

```text
COSMOSIS_ORML_U2NET_RUNNER
COSMOSIS_ORML_BODYPIX_RUNNER
COSMOSIS_ORML_CLASSIFIER_RUNNER
COSMOSIS_ORML_SUPER_RESOLUTION_RUNNER
```

A runner may expose several capabilities. Cosmosis can point multiple environment variables at the same executable.

## Backend SPI

The runner itself intentionally has no model dependency. Native backends implement:

```kotlin
studio.cosmosis.orml.runner.OrmlRunnerBackend
```

and register through Java ServiceLoader:

```text
META-INF/services/studio.cosmosis.orml.runner.OrmlRunnerBackend
```

A backend declares an ID and a set of capability IDs. Duplicate providers for one capability are rejected instead of being selected by classpath order.

This separation lets a legacy ORML/TensorFlow/OpenGL backend own its own dependency graph and graphics/model lifecycle while the stable runner owns argument parsing, capability admission, output validation and failure semantics.

## Output contract

The backend writes exactly to the `--output` path supplied by the runner.

- subject/person masks: a decodable image with exactly the same dimensions as the input;
- super-resolution: a non-empty decodable image;
- image embedding/classification: a non-empty JSON object or array.

A backend returning success without a valid artifact is converted into failure.

The runner never overwrites the input file.

## Introspection

```bash
gradle :orml-runner:run --args='--describe'
gradle :orml-runner:run --args='--self-test'
```

`--describe` returns the protocol version, known capabilities, whether each currently has an admitted backend, and sanitized discovery errors.

## Native ORML backend boundary

This package does not claim that U2Net, BodyPix, the image classifier, or super-resolution models are installed. A concrete native backend remains a separately verified plugin because upstream ORML carries an older TensorFlow/OPENRNDR lifecycle.

The intended next implementation slice is a pinned backend distribution that implements this SPI against a verified ORML source/model set.
