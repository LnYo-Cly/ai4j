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

### Three question primitives, on one support ticket

Suppose `state` is a customer message: `"I was charged twice on my card ending 4242, refund ASAP!"`. Each primitive answers a different kind of question about it:

| Type | What it asks | What goes in criteria | What the answer looks like | When to use it |
| --- | --- | --- | --- | --- |
| `choice` | "Pick exactly one of these mutually exclusive options" | `{label: description}` map — the labels are the possible answers | `choice` = winning label, plus per-option `probabilities` and overall `confidence` | Routing, classification, intent dispatch: "which team — billing / tech / sales — owns this ticket?" |
| `score` | "Rate it on an ordered scale" | ordered list `["low","normal","high","critical"]`; the index is the level | `score` = level index (e.g. `2`), `legend` maps indices back to descriptions, plus `probabilities`/`confidence` | Degree rating: urgency, severity, quality bands — whenever options have a clear ordering |
| `noul` | "Is this proposition true? Give a probability" | nullable; optionally `{true: "what counts as yes", false: "what counts as no"}` to sharpen the boundary | `noul` = probability of "yes", `0..1` | Yes/no judgment: contains PII? violates policy? needs human review? — a natural fit for threshold guardrails |

One evaluation returns:

```json
"answers": {
  "route":        {"type": "choice", "choice": "billing", "probabilities": {"billing": 0.91, "tech": 0.06, "sales": 0.03}, "confidence": 0.91},
  "urgency":      {"type": "score", "score": 2, "probabilities": {"0": 0.02, "1": 0.10, "2": 0.81, "3": 0.07}, "confidence": 0.81, "legend": {"0": "low", "1": "normal", "2": "high", "3": "critical"}},
  "contains_pii": {"type": "noul", "noul": 0.97}
}
```

Note: `route`/`urgency`/`contains_pii` are **question names you pick** — whatever key you use in the request is the key you read back in `answers`.

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

`SystemOneRequest.builder()` exposes one method per question primitive — `choice` / `score` / `noul`. First argument is the question name (the answer key), second is instructions, third is that type's criteria:

```java
// state: any EntryType — String / Map / List / null
Map<String, Object> state = new LinkedHashMap<>();
state.put("message", "I was charged twice on my card ending 4242, refund ASAP");

// choice criteria: {label: description}
Map<String, Object> routes = new LinkedHashMap<>();
routes.put("billing", "Invoices, refunds, subscription payments");
routes.put("support", "Product usage, bugs, how-to questions");
routes.put("sales", "Pricing, upgrades, new purchases");

SystemOneResponse response = systemOne.evaluate(
        SystemOneRequest.builder()
                .state(state)
                .model("jev-latest")                    // optional, defaults to jev-latest
                .choice("route", "Which team should handle this?", routes)
                .score("urgency", "How urgent is the request?",
                        Arrays.asList("low", "normal", "high", "critical"))
                .noul("contains_pii", "Does the message contain personally identifiable information?")
                .build());

response.choice("route");        // e.g. "billing"
response.score("urgency");       // level index, e.g. 2.0
response.noul("contains_pii");   // probability of "yes", 0..1
response.confidence("route");    // 0..1
```

To sharpen a noul's decision boundary, pass a third argument `.noul(name, instructions, new NoulCriteria("what counts as yes", "what counts as no"))`; omit it and the field is absent from the request. The low-level path still works — assemble a `Map<String, SystemOneQuestion>` yourself and pass it to `SystemOneRequest.of(state, model, questions)`; both produce identical JSON.

`SystemOneResponse` also offers `choices()` / `scores()` / `nouls()` to filter answers by type, and `getAnswers().get(name)` for the full `SystemOneAnswer` (including `probabilities` and `legend`). `listModels()` maps to `GET /v1/models`.

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

Map<String, String> routeMap = new LinkedHashMap<>();
routeMap.put("billing", "billing");
routeMap.put("support", "support");
routeMap.put("generic", "generic");

StateGraphWorkflow workflow = new StateGraphWorkflow()
        .addNode("decide", decideNode)
        .addNode("billing", billingNode)
        .addNode("support", supportNode)
        .addNode("generic", genericNode)
        .start("decide")
        .addConditionalEdges("decide", router, routeMap);
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
