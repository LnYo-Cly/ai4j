---
title: System One (TypeSafe Jev)
description: Integrate TypeSafe's System One decision model Jev — evaluate Choice/Score/Noul questions in parallel against one state and get typed answers with probabilities and confidence for confidence-gated routing and guardrails.
tags: [capability, provider]
---

# System One (TypeSafe Jev)

This page answers three questions:

- What Jev / System One is, and how it fundamentally differs from Chat/Responses
- How to run an evaluation through `ISystemOneService`
- How to wire it into agent orchestration for confidence-gated routing and guardrails

## 1. It is not a chat model

TypeSafe's **System One** model `jev` does not generate text. It performs a **decision evaluation**:

```text
POST https://api.typesafe.ai/v1/systemone
{ "state": <string|object|array|null>, "model": "jev-latest", "questions": { ... } }
→ { "answers": { <key>: typed answer + probabilities + confidence }, "usage": { ... } }
```

You submit a `state` (string, JSON object, array, or null) plus a set of **typed questions**. The model evaluates all questions **in parallel and independently** against the same state, returning every answer in one call — each with a probability distribution and a confidence value.

Three question primitives:

| Type | Semantics | Criteria shape | Answer fields |
| --- | --- | --- | --- |
| `choice` | Pick one label from a set | `label → description` map | `choice` (winning label), `probabilities`, `confidence` |
| `score` | Rate on an ordered level list | ordered list (index 0..n) | `score` (level index), `probabilities`, `confidence`, `legend` |
| `noul` | Judge a yes/no proposition | optional `{true, false}` branch descriptions | `noul` (probability of "yes", 0..1) |

So its place in the SDK is not `IChatService` but a dedicated `ISystemOneService` — like `IRerankService`, a **structured, non-generative** service surface.

## 2. Configuration

The platform id is `typesafe`; the service entry is `getSystemOneService(PlatformType.TYPESAFE)`.

### Spring Boot (recommended: `ai.platforms[]` + `api-key-env`)

```yaml
ai:
  platforms:
    - id: typesafe-main
      platform: typesafe
      api-key-env: TYPESAFE_API_KEY   # credential stays out of config files
      # api-host: https://api.typesafe.ai/   # default, overridable
      # system-one-url: v1/systemone         # default, overridable
```

Equivalent legacy path: `ai.typesafe.api-host` / `api-key` / `system-one-url` / `models-url` / `default-model`.

### Plain Java

```java
TypeSafeConfig config = new TypeSafeConfig();
config.setApiKey(System.getenv("TYPESAFE_API_KEY"));

Configuration configuration = new Configuration();
configuration.setTypeSafeConfig(config);

ISystemOneService systemOne = new AiService(configuration)
        .getSystemOneService(PlatformType.TYPESAFE);
```

## 3. Running an evaluation

```java
Map<String, SystemOneQuestion> questions = new LinkedHashMap<>();

Map<String, Object> routes = new LinkedHashMap<>();
routes.put("billing", "Invoices, refunds, subscription payments");
routes.put("support", "Product usage, bugs, how-to questions");
routes.put("sales", "Pricing, upgrades, new purchases");
questions.put("route", ChoiceQuestion.of("Which team should handle this?", routes));

questions.put("urgency", ScoreQuestion.of("How urgent is the request?",
        Arrays.asList("low", "normal", "high", "critical")));

questions.put("contains_pii", NoulQuestion.of(
        "Does the message contain personally identifiable information?"));

Map<String, Object> state = new LinkedHashMap<>();
state.put("message", "I was charged twice on my card ending 4242, refund ASAP");

SystemOneResponse response = systemOne.evaluate(
        SystemOneRequest.of(state, null, questions));  // model defaults to jev-latest

SystemOneAnswer route = response.getAnswers().get("route");
route.getChoice();        // e.g. "billing"
route.getConfidence();    // 0..1
route.getProbabilities(); // per-label probabilities

SystemOneAnswer pii = response.getAnswers().get("contains_pii");
pii.getNoul();            // probability of "yes", 0..1
```

`SystemOneResponse` also offers `choices()` / `scores()` / `nouls()` to filter answers by type. `listModels()` maps to `GET /v1/models`.

**Diagram: System One evaluation sequence** — state+questions → parallel evaluation → typed answers+confidence → routing/guardrail decision.

import useBaseUrl from '@docusaurus/useBaseUrl';

<iframe
  src={useBaseUrl('/archify/system-one-sequence.html')}
  title="system-one-sequence"
  style={{width: '100%', height: 720, border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: 8}}
/>

<a href={useBaseUrl('/archify/system-one-sequence.html')} target="_blank" rel="noopener noreferrer">Open the full diagram in a new window</a>

## 4. Wiring into agent orchestration

Jev produces no text, so it cannot drive `Agent.run()`'s main loop. Its value lives in the **orchestration layer** — replacing "let the LLM pick a branch" with a decision model's confidence score.

### Confidence-gated routing (`SystemOneRouter`)

`ai4j-agent` ships `SystemOneRouter`: a `StateRouter` that maps a Choice answer to a `StateGraphWorkflow` conditional-edge route label:

```java
SystemOneRouter router = new SystemOneRouter(systemOne)
        .instructions("Which branch should handle this request?")
        .route("billing", "Invoices, payments, subscription issues")
        .route("support", "Product usage questions and bugs")
        .minConfidence(0.7)          // below threshold → fallback
        .fallbackRoute("generic");

StateGraphWorkflow workflow = new StateGraphWorkflow()
        .addNode("decide", decideNode)
        .addNode("billing", billingNode)
        .addNode("support", supportNode)
        .addNode("generic", genericNode)
        .start("decide")
        .addConditionalEdges("decide", router, Map.of(
                "billing", "billing",
                "support", "support",
                "generic", "generic"));
```

Routing input defaults to `WorkflowContext.state` (when non-empty), otherwise `request.input`. The return value is a **route label**, mapped to node ids by `routeMap`.

### Noul guardrail (`SystemOneGuardrail`)

Use a `noul` question for yes/no policy checks; `noul ≥ threshold` counts as a violation:

```java
SystemOneGuardrail pii = new SystemOneGuardrail(systemOne)
        .instructions("Does the content contain personally identifiable information?")
        .threshold(0.8);

// As a condition (matches() = allowed to proceed)
boolean allowed = pii.matches(context, request, result);

// Or directly as a workflow node returning a fixed result on violation
workflow.addNode("piiGate", pii.asNode(
        AgentResult.builder().outputText("Blocked: PII detected").build()));
```

### Deterministic testing (`ai4j-testing`)

`ScriptedSystemOneService` follows the same pattern as `ScriptedModelClient`: enqueue `SystemOneResponse`s and record every `SystemOneRequest` — router/guardrail tests run fully offline with no credentials:

```java
ScriptedSystemOneService jev = new ScriptedSystemOneService()
        .enqueueAnswer("route", answer);   // SystemOneAnswer

String route = new SystemOneRouter(jev)
        .route("billing").route("support")
        .minConfidence(0.7).fallbackRoute("generic")
        .route(context, request, null);
```

## 5. Boundaries

- **No text generation**: it does not replace Chat/Responses; answers are always typed.
- **Confidence is model-reported**: `minConfidence` gating is an engineering fallback, not a correctness guarantee.
- **`state` is the full context**: evaluations are independent and stateless; put whatever context matters into `state`.
- **Default model** is `jev-latest`; override via `TYPESAFE_DEFAULT_MODEL` or config.
