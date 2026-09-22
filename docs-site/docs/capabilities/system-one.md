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

### 三种问题原语：用客服工单走一遍

假设 `state` 是一封用户来信：`"我的卡被重复扣款了（尾号 4242），请尽快退款！"`。三种问题各管一件事：

| 类型 | 它在问什么 | criteria 里填什么 | 答案长什么样 | 什么时候用它 |
| --- | --- | --- | --- | --- |
| `choice` | "从这几个互斥选项里选一个" | `{选项名: 选项说明}` 映射，选项名就是可能的答案 | `choice` = 胜出的选项名，外加每个选项的 `probabilities` 和整体 `confidence` | 路由、分类、意图分流："这封工单该给 billing / tech / sales 哪个组？" |
| `score` | "在有序等级上评到第几级" | 有序列表 `["不急","一般","紧急","致命"]`，下标即等级 | `score` = 等级下标（如 `2`），`legend` 把下标映射回描述，另有 `probabilities`/`confidence` | 程度评级：紧急度、严重程度、质量分档——选项之间有明确顺序时用 |
| `noul` | "这个命题成立吗？给概率" | 可空；可填 `{true: "算'是'的情形", false: "算'否'的情形"}` 澄清边界 | `noul` = "是"的概率 `0..1` | 是/否判定：是否含隐私信息、是否违规、是否需要人工介入——天然适合做护栏阈值 |

同一次求值的返回：

```json
"answers": {
  "route":        {"type": "choice", "choice": "billing", "probabilities": {"billing": 0.91, "tech": 0.06, "sales": 0.03}, "confidence": 0.91},
  "urgency":      {"type": "score", "score": 2, "probabilities": {"0": 0.02, "1": 0.10, "2": 0.81, "3": 0.07}, "confidence": 0.81, "legend": {"0": "不急", "1": "一般", "2": "紧急", "3": "致命"}},
  "contains_pii": {"type": "noul", "noul": 0.97}
}
```

要点：`route`/`urgency`/`contains_pii` 这些 key 是**你自己起的问题名**，请求里叫什么，答案里就用同一个 key 取回来。

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

`SystemOneRequest.builder()` 的 `choice` / `score` / `noul` 三个方法各对应一种问题原语，第一个参数是问题名（答案的 key），第二个是 instructions，第三个是该类型的 criteria：

```java
// state：任意 EntryType——String / Map / List / null
Map<String, Object> state = new LinkedHashMap<>();
state.put("message", "I was charged twice on my card ending 4242, refund ASAP");

// choice 的 criteria：{选项名: 选项说明}
Map<String, Object> routes = new LinkedHashMap<>();
routes.put("billing", "Invoices, refunds, subscription payments");
routes.put("support", "Product usage, bugs, how-to questions");
routes.put("sales", "Pricing, upgrades, new purchases");

SystemOneResponse response = systemOne.evaluate(
        SystemOneRequest.builder()
                .state(state)
                .model("jev-latest")                    // 可省略，缺省 jev-latest
                .choice("route", "Which team should handle this?", routes)
                .score("urgency", "How urgent is the request?",
                        Arrays.asList("low", "normal", "high", "critical"))
                .noul("contains_pii", "Does the message contain personally identifiable information?")
                .build());

response.choice("route");        // 例如 "billing"
response.score("urgency");       // 等级下标，如 2.0
response.noul("contains_pii");   // "yes" 的概率 0..1
response.confidence("route");    // 0..1
```

noul 需要澄清判定边界时传第三个参数 `.noul(name, instructions, new NoulCriteria("算'是'的情形", "算'否'的情形"))`；不传则该字段不出现在请求里。也可以用底层写法自己组 `Map<String, SystemOneQuestion>` 传给 `SystemOneRequest.of(state, model, questions)`——两种写法产出的 JSON 完全一致。

`SystemOneResponse` 还提供 `choices()` / `scores()` / `nouls()` 按类型过滤答案，以及 `getAnswers().get(name)` 拿整个 `SystemOneAnswer`（含 `probabilities`、`legend`）。`listModels()` 对应 `GET /v1/models`。

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
