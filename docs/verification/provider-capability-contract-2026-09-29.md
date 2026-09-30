# Provider Capability Contract Verification — 2026-09-29

## Result

**PASS — loopback-only / zero unsupported HTTP / no paid provider calls / JDK 21**

This checkpoint hardens the boundary between requested provider semantics and the selected model/route declaration. It extends, but does not replace, the earlier provider HTTP transport checkpoint.

## Provenance

- Frozen main base: `54e035f74074808b6f1367fd9bf013006009281d`
- Implementation commit: `d9700dcf6557fa611d50de40cc95c8acf44c249c`
- Typed semantic-error repair: `c93d5cd9dfb28c23d7f67d6592b173e7f09267f1`
- Pull request: #16 — `harden/provider-capability-contract-20260929`
- Successful implementation run: https://github.com/es-3581100/cosmosis-image-studio/actions/runs/36653383400
- Evidence artifact: `cosmosis-provider-contract`
- Artifact id: `11070768322`
- Artifact digest: `sha256:73af8a1844cdff8fdce91dc0e89c5919b058e887d3b4582adaf51179dbf070f9`

The first PR run, `36653180899`, passed core smoke, static audit and the full dependency-resolved test/acceptance stage. The new capability harness then exposed one error-typing inconsistency: an orphan `reasoningModel` was already rejected before HTTP, but Kotlin `require()` surfaced a plain `IllegalArgumentException`. The repair converted provider-semantic malformed/conflict failures to the project capability-error family. The assertions were not weakened.

## Capability-contract architecture

```text
GenerationRequest
    ↓ derives
RequestRequirements
    ↓ checked against
ModelDefinition.capabilities
    ↓ exact provider/model resolution
ProviderRegistry.validate / ProviderRegistry.route
    ↓ same authoritative declaration rechecked
provider adapter assertion
    ↓ only after success
HttpSupport
```

### Authoritative declaration

The bundled authoritative declarations are in:

```text
src/main/resources/config/model-registry.json
    → ModelRegistry
    → ModelDefinition
    → ProviderCapabilities
```

The registry schema is now version 4. Provider identity by itself does not grant provider-specific semantics.

The capability additions used by current COSMOSIS behavior are:

- `thinkingConfiguration`
- `responsesImageGeneration`
- `outputCompression`
- `interactionStorage`
- `continuationProtocol`

Existing fields such as `searchGrounding`, `imageSizes`, `multiTurnEditing`, image/edit capabilities and output-format declarations remain authoritative.

### Request requirements

`CapabilityValidator.requirements(request)` derives semantic requirements from provider-consumed request surfaces, including:

- Gemini `searchGrounding`
- Gemini `thinkingLevel`
- Gemini `imageSize`
- Gemini `store`
- OpenAI `openAiWorkflow=responses`
- OpenAI `reasoningModel`
- OpenAI `compression`
- OpenAI size-preset metadata
- `previousResponseId` continuation semantics

Internal editor metadata that does not enter provider wire formats, such as workflow bookkeeping, remains outside this provider-semantic contract.

### Enforcement

The last provider-independent enforcement point is `ProviderRegistry.route()`. It:

1. resolves the exact declared model for the selected provider;
2. validates request requirements against that model declaration;
3. calls the adapter only after compatibility succeeds.

`JobEngine` and explicit job resume both dispatch through this route.

The OpenAI, Gemini and local adapters also exact-resolve the same model declaration and invoke the same `CapabilityValidator` as defense in depth. They do not maintain a second capability source of truth.

## Actual bypasses found and fixed

The implementation trace established real bypass classes:

1. **Generic metadata smuggling.** Gemini Search/thinking/storage/image-size and OpenAI Responses/reasoning/compression/size metadata could be accepted by the request structure without matching model capability checks.
2. **Permissive unknown model fallback.** OpenAI and Gemini unknown or alias-like model IDs inherited fallback/first-model capabilities instead of requiring an exact declaration.
3. **OpenAI-compatible semantic escape.** `LiteLlmProvider` subclasses `OpenAiProvider`, so `openAiWorkflow=responses` could redirect into the inherited Responses path despite the LiteLLM route not declaring Responses semantics.
4. **Continuation ambiguity.** `previousResponseId` was generic enough to reach a route that did not implement the corresponding continuation protocol.
5. **Silently irrelevant provider options.** Provider-specific options such as Gemini storage or OpenAI compression could be supplied to a different provider-shaped route and ignored rather than rejected.

All tested forms now fail locally.

## Alias boundary

COSMOSIS currently has **no production model-alias resolver**. This phase therefore did not manufacture an alias system solely to make a positive alias test.

The hardened rule is exact model identity: an alias-like or otherwise undeclared model ID is rejected with `CapabilityMismatchException` before transport. If a real alias mechanism is added later, it must resolve to a concrete `ModelDefinition` before capability validation.

## Positive matrix

| Request feature | Declared route semantics | Result |
|---|---|---|
| Generic OpenAI generation/edit | Images-capable OpenAI model | allow; real loopback transport |
| OpenAI Responses image + continuation | Responses-capable OpenAI model + `OPENAI_RESPONSES` protocol | allow; real `/responses` loopback transport |
| Gemini Search grounding | Gemini model with `searchGrounding=true` | allow; real Interactions transport |
| Gemini thinking | Gemini model with `thinkingConfiguration=true` | allow; real Interactions transport |
| Gemini continuation | Gemini model with multi-turn + `GEMINI_INTERACTIONS` protocol | allow; real Interactions transport |
| LiteLLM generic generation | declared generic OpenAI-compatible route | allow; real loopback transport |
| Existing image decoding/admission | supported provider requests | pass |

The smoke reports `positive-cases=7`.

## Negative matrix

| Request feature | Selected declaration | Expected / observed |
|---|---|---|
| Gemini Search | Gemini test route without Search | local reject / zero HTTP |
| Gemini thinking | Gemini test route without thinking | local reject / zero HTTP |
| Gemini Search | OpenAI model | local reject / zero HTTP |
| Gemini Search | LiteLLM route | local reject / zero HTTP |
| OpenAI Responses | generic OpenAI test route | local reject / zero HTTP |
| OpenAI Responses | LiteLLM route | local reject / zero HTTP |
| `reasoningModel` without Responses workflow | Responses-capable OpenAI model | local reject / zero HTTP |
| direct `previousResponseId` without Responses workflow | OpenAI model | local reject / zero HTTP |
| output compression semantic | LiteLLM route without declaration | local reject / zero HTTP |
| Gemini `store` option | OpenAI model | local reject / zero HTTP |
| alias-like undeclared model ID | OpenAI provider | local reject / zero HTTP |

The smoke reports `negative-cases=11`.

## Zero-HTTP proof

Every negative case executes through a helper that records the loopback fixture request count before validation, invokes the production provider boundary, requires a `CapabilityException`, then requires the request-count delta to remain zero.

Aggregate preserved evidence:

```text
unsupported-http-requests=0
```

This proves the tested unsupported combinations are rejected before `HttpSupport` reaches the loopback server. It is not provider-side rejection.

## Preserved smoke output

```text
PROVIDER_CONTRACT_SMOKE_PASS
PROVIDER_CAPABILITY_CONTRACT_PASS
network=loopback-only
requests=9
positive-cases=7
negative-cases=11
unsupported-http-requests=0
gemini.search.supported=pass
gemini.search.unsupported=pass
gemini.thinking.supported=pass
gemini.thinking.unsupported=pass
openai.responses.supported=pass
openai.responses.generic-route-rejected=pass
openai.responses.litellm-route-rejected=pass
cross-provider-option-smuggling=pass
undeclared-model-alias-rejected=pass
provider-compatible-not-semantic-compatible=pass
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

## Regression evidence

Run `36653383400` also passed:

- deterministic core smoke;
- static provenance / Offworld audit;
- Kotlin/JUnit tests;
- dependency-resolved acceptance smoke;
- original provider transport assertions;
- ORML runner distribution smoke;
- real graphical OPENRNDR + Swing runtime smoke;
- U2Net, BodyPix, classifier and super-resolution helper/distribution checks.

No additional verification `main()` was introduced. The existing `ProviderContractSmoke.kt` entry point was extended, so the prior second-entry-point regression was not reintroduced.

## Limits

This remains **offline capability-contract verification**. It proves COSMOSIS enforces its declared semantic contract locally and that supported requests still traverse production adapters against the loopback fixture.

It does not prove:

- that current live OpenAI or Gemini endpoints accept every modeled field;
- that provider model names/capabilities have not changed;
- that a user-configured LiteLLM/custom gateway implements any undeclared semantics;
- paid-provider generation success;
- semantic capabilities not currently represented by COSMOSIS.

Live provider verification remains explicit opt-in through `liveProviderSmoke` with user-supplied credentials.
