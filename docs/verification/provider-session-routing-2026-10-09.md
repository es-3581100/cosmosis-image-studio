# Provider session routing checkpoint — 2026-10-09

## Purpose

This checkpoint fixes provider configuration seams found during hands-on COSMOSIS desktop testing after the friendly-editor merge.

Base main before this work:

```text
27fc4592417178ed4efccb09f8ecdcfe552190e2
```

Implementation branch:

```text
fix/custom-model-gemini-session-20261009
```

## User-visible fixes

### Custom routes no longer hijack the active provider

Registering an OpenAI-compatible custom route now only defines that route. It does **not** change the current OpenAI, Gemini, local or LiteLLM selection.

The user must explicitly press **Use Custom** to switch to it.

### Manual custom model IDs

Custom route configuration now includes an exact **MODEL ID / MANUAL** field.

That model ID is converted into a local model declaration for the custom provider using the conservative generic OpenAI-compatible image capability profile.

This deliberately does not grant:

- OpenAI Responses image semantics;
- Gemini Search/thinking/storage semantics;
- continuation protocols;
- other provider-specific capabilities.

The exact-model contract remains active.

### Separate credentials

Advanced settings now expose independent, session-only key entry for:

- OpenAI;
- Google / Gemini;
- custom OpenAI-compatible route.

Session keys are held only in memory and are cleared when replaced, explicitly cleared, or the controller closes.

Environment fallbacks remain:

```text
OPENAI_API_KEY
GEMINI_API_KEY
GOOGLE_API_KEY
CUSTOM_OPENAI_API_KEY (or user-selected custom env name)
```

No session credential is written into project state, SQLite, settings, diagnostics or logs by these code paths.

## Gemini model registry

The built-in Gemini image registry now includes:

```text
gemini-nano-banana-2.1
gemini-3.1-flash-image
gemini-3.1-flash-lite-image
gemini-3-pro-image
```

The adapter continues to use the Gemini Interactions API and `x-goog-api-key` authentication.

Provider pricing and quotas remain external. COSMOSIS does not label a model as free merely because it can be tested in Google AI Studio.

## Regression coverage

New tests verify:

- a custom OpenAI-compatible route declares the exact manually supplied model ID;
- the old `route-configured` placeholder is not silently accepted for that custom route;
- registering custom does not mutate the currently active provider or model;
- custom session keys do not appear in UI state messages;
- Gemini session credentials are recognized as session credentials;
- the current Gemini image model IDs are present.

## Verification

PR #20 implementation run:

```text
38015395544
```

Result: **SUCCESS**.

Passed gates:

- deterministic core smoke;
- static provenance + Offworld audit;
- dependency-resolved unit tests;
- `acceptanceSmoke`;
- offline provider transport/capability contract;
- ORML runner distribution smoke;
- desktop graphical runtime smoke;
- U2Net / BodyPix / classifier / super-resolution syntax and distribution checks.

No live paid/provider request was made by this verification.

## Boundaries

The graphical smoke verifies the default friendly workspace and does not interactively type into every Advanced settings field. The new settings code is compiled as part of the application and the controller/provider behavior is covered by unit/acceptance tests, but final human UX validation should still be performed on the target desktop.

A custom OpenAI-compatible gateway may expose a different or incomplete subset of OpenAI endpoints. COSMOSIS can now send its exact manual model ID, but successful live generation still depends on that gateway implementing the image-generation transport COSMOSIS uses.

This checkpoint does not claim that Gemini image-generation API usage is free. Provider pricing and quota policy can change independently of COSMOSIS.
