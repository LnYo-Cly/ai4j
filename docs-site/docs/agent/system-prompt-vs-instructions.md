---
sidebar_position: 2
---

# System Prompt 与 Instructions 的区别

这两个字段经常被混用，但在可控性上差异很大。

## 1. 一句话区分

- `systemPrompt`：定义“你是谁、必须遵守什么规则”。
- `instructions`：定义“这次具体要你做什么”。

## 2. 作用域与稳定性

| 维度 | systemPrompt | instructions |
| --- | --- | --- |
| 作用范围 | 整个会话/长期有效 | 当前任务/当前轮次 |
| 稳定性 | 高（少改） | 高灵活（可频繁改） |
| 典型内容 | 角色、风格、边界、禁令 | 输出格式、步骤要求、当前上下文 |

## 3. AI4J 中的字段建模

AI4J 在 `AgentBuilder` 中将二者显式分离：

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("doubao-seed-1-8-251228")
        .systemPrompt("你是资深 Java 架构师，回答要简洁并给可执行建议")
        .instructions("本次输出 JSON，字段: risk, proposal, nextStep")
        .build();
```

## 4. 在不同模型协议中的映射

### ResponsesModelClient

- 字段语义天然分离，推荐优先使用。

### ChatModelClient

- 会映射为 system 消息（先 systemPrompt，再 instructions）。
- 语义仍可用，但边界感不如 Responses 明确。

## 5. 实战写法模板

### System Prompt 模板（全局）

```text
你是企业级智能助手。
必须遵守：
1) 不编造未验证事实；
2) 涉及工具时先调用工具再回答；
3) 输出先给结论，再给依据。
```

### Instructions 模板（本轮）

```text
任务：根据 queryWeather 输出今日天气建议。
输出格式：JSON，字段 city/summary/advice。
如果工具失败，请给出可执行的重试建议。
```

## 6. 常见误区

1. 把动态上下文都塞进 systemPrompt，导致 prompt 膨胀。
2. 每轮都重写一整套全局规则，增加 token 成本。
3. 在 instructions 写身份设定，导致跨轮不稳定。

## 7. 建议实践

- 将组织级规则沉淀到 `systemPrompt` 模板文件。
- 将任务级策略放在 `instructions`，按场景动态组装。
- 对关键链路（金融/医疗/法务）结合 Trace 记录输入输出，便于审计。
