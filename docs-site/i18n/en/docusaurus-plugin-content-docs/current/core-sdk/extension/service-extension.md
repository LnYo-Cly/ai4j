---
title: "Service Extension"
description: "Clarifies AI4J service extension: adding a new top-level capability contract expands the SDK's entire public API surface, so you must keep AiService, AiServiceRegistry, and the FreeAiService compatibility entry points in sync. AiServiceFactory is not a service plugin bus; a new service is only worth adding when the existing contracts can no longer carry the load."
tags: [concept]
---

# Service Extension

`service extension` addresses the question: **should AI4J add a new top-level capability contract?**
This is heavier than a model extension, because you are not filling in a platform — you are expanding the SDK's entire public capability surface.

## 1. The top-level capability surfaces that already exist

Under `ai4j/src/main/java/io/github/lnyocly/ai4j/service/`, these formal interfaces already exist:

- `IChatService`
- `IResponsesService`
- `IEmbeddingService`
- `IImageService`
- `IAudioService`
- `IRealtimeService`
- `IRerankService`

These interfaces are not split arbitrarily. They determine:

- which formal entry points `AiService` exposes
- the dimension along which the provider support matrix is maintained
- how upper-layer modules form a stable mental model

So adding a new service is not "think of a name and add an interface" — it is redefining the SDK's capability map.

## 2. What really changes is not a single point, but an entire chain

If you add `IXxxService`, you actually have to go through these layers:

1. The new interface itself, plus the request/response objects
2. At least one provider's implementation class
3. `AiService`'s `getXxxService(...)` and `createXxxService(...)`
4. The convenience access methods on `AiServiceRegistry`
5. The compatibility static entry points on `FreeAiService`
6. Corresponding docs, examples, and regression tests

The easiest steps to miss are #4 and #5.

Many extensions run fine at the `AiService` layer, but:

- in multi-instance scenarios the new service is unreachable
- the legacy `FreeAiService` compatibility entry points are not kept in sync

This produces the inconsistency of "the core capability exists, but the formal access surface is incomplete".

## 3. When adding a new service is actually worth it

You should only take it seriously when the following are all close to true:

- None of the existing `Chat / Responses / Embedding / Image / Audio / Realtime / Rerank` can naturally carry the load
- The input/output semantics are no longer a variant of an existing capability surface
- The caller's runtime mental model has visibly shifted
- Expressing it as "add a few more fields to an existing request object" would deform the abstraction

Conversely, if it is only:

- a few more options under the same interaction model
- some fields that differ per provider
- one more result format

then it usually has not reached the threshold for a new service.

## 4. Why this layer carries the highest cost

### 4.1 It directly enlarges the public API

Adding a top-level service always widens the interface surface that the `ai4j` module commits to externally. This affects:

- Java API stability
- documentation structure
- how upper-layer modules call it
- the comprehension cost of starters and demos

This kind of change is inherently harder to roll back than "one more provider branch".

### 4.2 Every provider's support boundary must be redefined

service extension does not require every provider to support it immediately, but you must be explicit about:

- which providers already support it
- which providers do not yet support it
- whether unsupported cases still throw an explicit exception

Every existing capability surface on `AiService` works this way. For example, `Responses` is only exposed for a few providers, while `Chat` has broader coverage.

So when adding a new service, the priority is not chasing "a uniform-looking surface across all platforms", but maintaining a clear support matrix.

### 4.3 It ripples into the registry and compatibility layer

`AiServiceRegistry` is not a simple id->AiService map; it also turns common capabilities into id-access default methods.
`FreeAiService` continues to keep static compatibility entry points.

If you add a new service without filling in these two layers, the call chain breaks at the multi-instance or compatibility mode.

## 5. `AiServiceFactory` is not a shortcut for service extension

The repo does have the `AiServiceFactory` abstraction, but its responsibility is "how to create an `AiService`", not "how to dynamically discover new service types".

More concretely:

- the provider/service capability matrix is still defined on `AiService` itself
- the starter registers `DefaultAiServiceFactory` directly by default
- this layer does not provide a "new service auto-flows into the main line" capability

:::note
So do not mistake `AiServiceFactory` for a service plugin bus.
:::

## 6. A more stable way to judge

Before adding a new service, ask yourself three times:

1. Is this really a new capability, or just a runtime mode of `Chat`/`Responses`?
2. Am I dodging field-mapping complexity on the provider side, or is the abstraction genuinely insufficient?
3. If I make it a new service, will the upper-layer docs and mental model become clearer, or more fragmented?

If the answer to the third question is ambiguous, it usually means a new service should not be added yet.

## 7. What to look at during debugging and acceptance

### `AiService` can return the new service, but the registry cannot

This means the `AiServiceRegistry` default methods were not filled in, or the registry call chain was not wired into the new capability.

### The registry works, but `FreeAiService` does not

This means the compatibility shell did not sync its static entry points. This affects legacy usage and historical examples.

### Only a certain provider works; other providers error out

This is not necessarily a bug. First determine whether it is an asymmetric support matrix you deliberately preserved.
As long as the exceptions are clear and the docs are explicit, this asymmetry is allowed to exist.

## 8. When you should fall back to a model extension

If you find the change is mainly concentrated in:

- `ChatCompletion`
- `ResponseRequest`
- the request serialization of a certain provider
- the event/response parsing of a certain provider

then it is more likely still a model extension, not a service extension.

Prematurely promoting changes that should have stayed inside a provider to a top-level service usually produces a capability surface with a brand-new name but semantics that heavily overlap an existing interface.

## 9. The conclusion of this page

> In AI4J, service extension changes the SDK's formal capability map, not a local implementation of some platform. Only when the existing top-level contracts can no longer naturally carry the new semantics is it worth adding a new service surface; otherwise the more stable approach is to absorb the change within the existing service contracts and the provider adaptation layer.
