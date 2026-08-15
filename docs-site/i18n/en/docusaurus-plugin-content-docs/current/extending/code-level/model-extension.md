---
title: "Model Extension"
description: "Explains AI4J model extension: how to absorb new model capabilities into existing providers and existing contracts without introducing a new PlatformType. The main battleground is the request objects and the provider adaptation layer, with a strong emphasis on converging provider differences inside the provider service rather than leaking them into the business layer."
tags: [concept]
---

# Model Extension

`model extension` addresses the following problem: **absorb new model capabilities into existing providers and existing contracts, without introducing a new `PlatformType` or a new top-level service.**

This is the most common type of extension in AI4J, and also the one most easily misjudged.

## 1. First clarify its boundary against provider / service extension

As long as both of the following still hold, treat it as a model extension first:

- The platform boundary has not changed; it still belongs to the same provider
- The call mental model still falls under an existing `Chat`, `Responses`, `Embedding`, `Image`, `Audio`, `Realtime`, `Rerank`, `Video`, or `Music`

Once you have to add a new `PlatformType`, you are already closer to a provider extension.
Once you find that the existing capability surface simply cannot accommodate the new interaction semantics, reconsider a service extension.

## 2. Where this kind of change usually lands in the current code

model extension most often lands on the following kinds of objects:

- Unified request objects, e.g. `ChatCompletion`, `ResponseRequest`
- Provider-side request mapping and result parsing
- Model-related fields in provider configuration objects

In other words, its main battleground is usually:

- Whether the request object can express the new capability
- Whether the provider service actually sends these fields out
- Whether the returned result is correctly absorbed by the existing listener / converter

Rather than the `PlatformType` or the top-level dispatch layer of `AiService`.

## 3. Three typical model extension scenarios

### 3.1 Just adding a model name

This is the lightest kind.

If an existing service of a provider already supports the target protocol, and the only changes are:

- The model string changed
- The default `apiHost` changed
- Some caller needs to switch to a new model

then you usually do not need to touch the base abstraction; just configure it correctly and go through the existing service.

### 3.2 The request or response gains a limited provider difference

For example:

- The new model requires a new reasoning parameter
- The new model supports new output format controls
- The new model adds some fields in streaming events

What you really need to check at this point is:

- Whether the unified request object needs new fields
- Whether the corresponding provider service has a serialization/deserialization update

:::warning
If you only change the request object without updating the provider mapping, the most common outcome in practice is "the field appears to be added, but the platform never actually receives it".
:::

### 3.3 Nominal new model, but the old contract has actually been broken through

If you find that:

- The input no longer looks like the existing request model
- The return no longer looks like the existing event or result object
- The caller's way of consuming state has completely changed

then this is usually no longer a pure model extension. Forcing it into the existing service typically just gradually warps the abstraction.

## 4. Check the provider support matrix before deciding where to change

In AI4J today, provider coverage across different service surfaces is not symmetric.

For example:

- `Chat` covers significantly more providers
- `Responses` is currently exposed only for a few providers

This leads to a very practical judgment:

- If the new model still falls under an existing `Chat` capability, prefer keeping it within the `Chat` path
- If the target capability only holds under `Responses` semantics, first confirm that the provider has already been added to `createResponsesService(...)`

So model extension is not as simple as "add a model string"; you first need to see which existing service main line it lands on.

## 5. The principles this kind of extension should uphold

### Converge provider differences inside the provider service as much as possible

Upper-layer callers should ideally continue to depend only on the unified request object and the unified service interface.

If every new model forces the business code to write a batch of:

- `if openai`
- `if deepseek`
- `if dashscope`

that means the model extension has not been well converged; provider differences are leaking into the business layer.

### As long as the existing contract still holds, do not upgrade the abstraction layer

This is a very important experience line in AI4J.

As long as the existing `Chat`, `Responses`, and similar contracts still hold, prefer to keep the change in:

- provider request mapping
- provider response parsing
- provider configuration fields

rather than immediately ballooning into a new provider or a new service.

## 6. A few easily overlooked consequences in the current implementation

### Not all fields are automatically sent to the provider

The point of the unified request object is to give the SDK a stable modeling surface, not to guarantee that every field automatically passes through to the external platform.

So when you add fields for a new model, you must at the same time verify that:

- The provider service actually reads the field
- The provider payload builder actually writes it into the request
- The streaming or non-streaming return path also needs to be adjusted accordingly

### Existing factory branches usually do not need to change

Many model extensions should not touch `AiService`.
If you are not adding a platform, and you are not adding a top-level capability surface, but you start changing `PlatformType` or `create*Service(...)`, that usually means the extension layer was misjudged.

### service objects are created per call by default

`AiService` currently does not enable service caching; obtaining a concrete service creates an instance per call.
This means state in a model extension should not secretly rely on a particular provider service being reused for a long time.

## 7. Where to look first when troubleshooting

### The new model name took effect, but the new field seems to have no effect

First look at the provider service's request mapping, rather than immediately blaming the configuration layer.
These issues are usually that the unified request object already has the field added, but the provider-side serialization did not catch up.

### The business side is forced to add many provider branches

First reflect on whether differences that should have stayed inside the provider service have been exposed.
The whole point of model extension is to absorb differences, not to propagate them.

### You have already started changing `PlatformType`

This is usually a warning sign: you may have slid from model extension into provider extension.

## 8. A practical judgment line

If this change is mainly concentrated in:

- request / response objects
- provider service
- provider configuration fields

and does not require changing:

- `PlatformType`
- `DefaultAiServiceRegistry.applyPlatformConfig(...)`
- the entire provider property wiring in the starter

then it is most likely a model extension.

## 9. Conclusion of this page

> In AI4J, the core of model extension is not "whether you can swap a model string", but whether the existing provider and existing top-level contracts can still carry this change. As long as the platform boundary and the capability semantics have not changed, the safest approach is to converge the differences inside the request object and the provider adaptation layer, rather than prematurely elevating to a provider- or service-level extension.
