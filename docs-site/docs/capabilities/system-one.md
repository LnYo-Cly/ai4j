---
title: System One（TypeSafe Jev）
description: 接入 TypeSafe 的 System One 决策模型 Jev：对同一份 state 并行求值 Choice/Score/Noul 三类问题，返回带概率和置信度的类型化答案，用于置信度门控路由和护栏。
tags: [capability, provider]
---

# System One（TypeSafe Jev）

这一页回答三个问题：

- Jev / System One 是什么、它和 Chat/Responses 有什么本质区别
- 怎么用 `ISystemOneService` 发起一次求值
- 怎么把它接到 Agent 编排里做置信度路由和护栏

## 1. 它不是聊天模型

TypeSafe 的 **System One** 模型 `jev` 不生成文本。它做一次**决策求值**：

```text
POST https://api.typesafe.ai/v1/systemone
{ "state": <string|object|array|null>, "model": "jev-latest", "questions": { ... } }
→ { "answers": { <key>: 类型化答案 + 概率 + 置信度 }, "usage": { ... } }
```

你提交一份 `state`（字符串、JSON 对象、数组或 null）和一组**类型化问题**，模型对同一份 state **并行且独立**地求值所有问题，一次请求返回全部答案——每个答案带概率分布和置信度。

三种问题原语：

| 类型 | 语义 | criteria 形态 | 答案字段 |
| --- | --- | --- | --- |
| `choice` | 从一组标签中选一个 | `label → 描述` 映射 | `choice`（胜出标签）、`probabilities`、`confidence` |
| `score` | 在有序等级表上评级 | 有序列表（index 0..n） | `score`（等级索引）、`probabilities`、`confidence`、`legend` |
| `noul` | 判定一个是/否命题 | 可选 `{true, false}` 分支描述 | `noul`（"是"的概率 0..1） |

这意味着它在 SDK 里的位置不是 `IChatService`，而是独立的 `ISystemOneService`——和 `IRerankService` 一样，是一个**结构化、非生成式**的 service 面。

## 2. 配置

平台 id 是 `typesafe`，服务入口 `getSystemOneService(PlatformType.TYPESAFE)`。

### Spring Boot（推荐 `ai.platforms[]` + `api-key-env`）

```yaml
ai:
  platforms:
    - id: typesafe-main
      platform: typesafe
      api-key-env: TYPESAFE_API_KEY   # 凭据不落盘，装配期解析
      # api-host: https://api.typesafe.ai/   # 默认值，可覆盖
      # system-one-url: v1/systemone         # 默认值，可覆盖
```

等价 legacy 路径：`ai.typesafe.api-host` / `api-key` / `system-one-url` / `models-url` / `default-model`。

### 纯 Java

```java
TypeSafeConfig config = new TypeSafeConfig();
config.setApiKey(System.getenv("TYPESAFE_API_KEY"));

Configuration configuration = new Configuration();
configuration.setTypeSafeConfig(config);

ISystemOneService systemOne = new AiService(configuration)
        .getSystemOneService(PlatformType.TYPESAFE);
```

## 3. 发起一次求值

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
        SystemOneRequest.of(state, null, questions));  // model 缺省 = jev-latest

SystemOneAnswer route = response.getAnswers().get("route");
route.getChoice();        // 例如 "billing"
route.getConfidence();    // 0..1
route.getProbabilities(); // 每个标签的概率

SystemOneAnswer pii = response.getAnswers().get("contains_pii");
pii.getNoul();            // "yes" 的概率 0..1
```

`SystemOneResponse` 还提供 `choices()` / `scores()` / `nouls()` 按类型过滤答案。`listModels()` 对应 `GET /v1/models`。

**图例：System One 求值序列** —— state+questions → 并行求值 → 类型化答案+置信度 → 路由/护栏决策。

import useBaseUrl from '@docusaurus/useBaseUrl';

<iframe
  src={useBaseUrl('/archify/system-one-sequence.html')}
  title="system-one-sequence"
  style={{width: '100%', height: 720, border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: 8}}
/>

<a href={useBaseUrl('/archify/system-one-sequence.html')} target="_blank" rel="noopener noreferrer">新窗口打开全图</a>

## 4. 接进 Agent 编排

Jev 不产出文本，所以不能驱动 `Agent.run()` 的主循环；它的价值在**编排层**——用决策模型的置信度替代"让 LLM 自己选分支"。

### 置信度门控路由（`SystemOneRouter`）

`ai4j-agent` 提供 `SystemOneRouter`：一个 `StateRouter`，把 Choice 答案映射为 `StateGraphWorkflow` 条件边的路由标签：

```java
SystemOneRouter router = new SystemOneRouter(systemOne)
        .instructions("Which branch should handle this request?")
        .route("billing", "Invoices, payments, subscription issues")
        .route("support", "Product usage questions and bugs")
        .minConfidence(0.7)          // 置信度不足 → 走 fallback
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

路由输入默认取 `WorkflowContext.state`（非空时），否则取 `request.input`。返回值是**路由标签**，由 `routeMap` 映射到节点 id。

### Noul 护栏（`SystemOneGuardrail`）

用 `noul` 问题做 yes/no 策略判定，`noul ≥ threshold` 判违规：

```java
SystemOneGuardrail pii = new SystemOneGuardrail(systemOne)
        .instructions("Does the content contain personally identifiable information?")
        .threshold(0.8);

// 作为条件判断（matches() = 允许通过）
boolean allowed = pii.matches(context, request, result);

// 或直接作为 workflow 节点：违规时返回固定结果
workflow.addNode("piiGate", pii.asNode(
        AgentResult.builder().outputText("Blocked: PII detected").build()));
```

### 确定性测试（`ai4j-testing`）

`ScriptedSystemOneService` 和 `ScriptedModelClient` 同款思路：预排队 `SystemOneResponse`，记录每次收到的 `SystemOneRequest`——路由/护栏测试完全离线、无密钥：

```java
ScriptedSystemOneService jev = new ScriptedSystemOneService()
        .enqueueAnswer("route", answer);   // SystemOneAnswer

String route = new SystemOneRouter(jev)
        .route("billing").route("support")
        .minConfidence(0.7).fallbackRoute("generic")
        .route(context, request, null);
```

## 5. 边界

- **不是文本生成**：不要用它替代 Chat/Responses；答案永远是类型化的。
- **置信度是模型自报的**：`minConfidence` 门控是工程兜底，不是正确性保证。
- **`state` 是全量上下文**：每次求值独立、无会话记忆；需要上下文就自己放进 `state`。
- **默认模型** `jev-latest`；可用 `TYPESAFE_DEFAULT_MODEL` 或 config 覆盖。
