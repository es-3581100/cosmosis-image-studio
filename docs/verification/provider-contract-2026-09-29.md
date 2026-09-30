# Provider HTTP Contract Verification — 2026-09-29

## Result

**PASS — loopback-only / no paid provider calls / JDK 21**

Cosmosis now has a deterministic transport-level smoke for its real provider adapters. The smoke uses the production `HttpSupport` code against an embedded loopback HTTP server; it does not replace live provider validation.

## Code and execution provenance

- Main base before this slice: `8a103cf51586cd82589f5ea67eedeea4a4af257d`
- Tested implementation commit: `e557482f6e011f6030acd7b72757a3a9b18a2bef`
- Pull request: #15 — `verify/provider-contract-offline-20260929`
- Workflow: `.github/workflows/ci.yml`
- Successful PR run: https://github.com/es-3581100/cosmosis-image-studio/actions/runs/36651232116
- Provider report artifact: `cosmosis-provider-contract`
- Artifact id: `11070313226`
- Artifact digest: `sha256:ae8c131d3d2ae7737fc2439e3b4e16053e0404f3e850581b3925125d320d4439`

The first PR run (`36651132387`) failed before reaching the new provider step because the dependency-light `core-smoke.sh` jar discovered a second top-level `main()` and therefore had no unique main manifest. The repair added `ProviderContractSmoke.kt` to the existing alternate-main exclusion list; it did not weaken the provider assertions or the core smoke.

## Network boundary

The smoke binds an ephemeral server to `127.0.0.1` and injects loopback base URLs into each adapter. No external provider endpoint or DNS route is required by the smoke. Synthetic credentials exist only in process memory and are checked by the loopback fixture; they are not emitted into the report.

## Verified contracts

### OpenAI Images

Verified through the production `OpenAiProvider` and `HttpSupport`:

- Bearer authentication header;
- `GET /v1/models` connection probe;
- `POST /v1/images/generations`;
- canonical prompt/output-format translation;
- base64 result parsing;
- revised-prompt admission;
- decodable PNG output.

### OpenAI image edit

Verified:

- `POST /v1/images/edits`;
- multipart/form-data boundary creation;
- `image[]` file part;
- prompt form field;
- decoded PNG admission.

### OpenAI Responses image workflow

Verified:

- `POST /v1/responses`;
- reasoning-model selection;
- image-generation tool payload;
- input-image data URI transport;
- `previous_response_id`;
- `result` image payload parsing;
- response ID admission to result metadata and provider asset ID.

### Gemini Interactions

Verified through the production `GeminiProvider` and `HttpSupport`:

- `x-goog-api-key` authentication header;
- model lookup;
- Interactions generation;
- Interactions edit with reference image;
- `previous_interaction_id`;
- Search grounding tool field;
- thinking level;
- image size and aspect ratio;
- typed image-block parsing;
- decodable PNG output and interaction ID admission.

### LiteLLM / OpenAI-compatible route

Verified:

- Bearer authentication header;
- configured loopback base URL;
- model lookup;
- OpenAI-compatible image generation request;
- decoded PNG admission;
- provider identity remains `litellm`.

The generic custom OpenAI-compatible provider shares the same transport implementation, but a particular third-party gateway can still differ in capabilities or endpoint behavior; this smoke does not certify arbitrary remote gateways.

## Preserved report

```text
PROVIDER_CONTRACT_SMOKE_PASS
network=loopback-only
requests=9
openai.connection=pass
openai.direct-generation=pass
openai.multipart-edit=pass
openai.responses-image=pass
gemini.connection=pass
gemini.generate=pass
gemini.edit-multiturn=pass
litellm.connection=pass
litellm.openai-compatible-generation=pass
auth.headers=pass
decoded-images=pass
```

## Limits

This checkpoint verifies Cosmosis request construction, authentication placement, local HTTP transport, response parsing and output admission against deterministic fixture responses. It does **not** prove that OpenAI, Gemini or a user-configured LiteLLM gateway currently accepts every modeled field, that provider-side model names/capabilities have not changed, or that paid generation succeeds.

`liveProviderSmoke` remains explicit opt-in evidence for real configured endpoints and credentials.
