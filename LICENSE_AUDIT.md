# License Audit

Audit date: 2026-09-27. This is an engineering provenance record, not legal advice.

## Reference repositories

- **NanoBananaEditor — AGPL-3.0.** No source copied. Interaction behavior only was studied and independently reimplemented.
- **awesome-gpt-image-2 — MIT repository.** The repository aggregates community examples; individual prompt/content rights may differ. Bulk import is intentionally not shipped in this slice.
- **image-to-prompt — reference-only in this build.** No source copied.
- **image-2-reverse-prompt — MIT.** Conceptual decomposition informed one original built-in prompt recipe; attribution retained.
- **html-anything — MIT-0.** Conceptual inspiration only; no source copied.
- **OPENRNDR — BSD-2-Clause.** Build dependency.
- **ORML — BSD-2-Clause (published POM metadata).** No source is copied. The upstream `openrndr/orml` Gradle publication declares `BSD-2-Clause` and links the OPENRNDR license. The U2Net, BodyPix, image-classifier and FALSR super-resolution backends independently reproduce pinned preprocessing/graph contracts; they do not vendor upstream implementation source or the classifier label-name table.
- **TensorFlow Java — Apache-2.0.** The optional U2Net, BodyPix, image-classifier and FALSR native distributions are lineage-pinned to TensorFlow Java 0.4.1 as used by the matching ORX `orx-tensorflow` commit. TensorFlow jars/natives are opt-in runtime dependencies, not core desktop dependencies.
- **sqlite-jdbc — Apache-2.0.** Runtime dependency.
- **kotlinx-coroutines — Apache-2.0.** Build/runtime dependency.

## Shipping policy

Third-party source or prompt corpora are not vendored without a per-source audit. Generated project exports preserve provenance fields and redact secrets.
