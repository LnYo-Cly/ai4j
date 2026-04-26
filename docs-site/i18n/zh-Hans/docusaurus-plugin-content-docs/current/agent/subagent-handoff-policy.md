---
sidebar_position: 8
---

# SubAgent 与 Handoff Policy（主从协作治理）

这一页聚焦“Lead-Agent -> SubAgent”的工程化实现：

- 什么时候该用 SubAgent
- 子代理在 AI4J 里如何被封装成 Tool
- `HandoffPolicy` 每个参数的时机
- 并行、失败回退、超时、深度限制怎么配置

## 1. 适用场景

当一个 Agent 同时承担“路由、检索、分析、格式化”会很快变臃肿，建议拆成：

- Lead-Agent：任务分解 + 结果汇总
- SubAgent：专项能力（天气、格式化、代码审查、检索）

## 2. 类级结构（按执行链）

1. `SubAgentDefinition`
   - 定义一个子代理（name/description/toolName/agent/sessionMode）
2. `StaticSubAgentRegistry`
   - 把定义转换为可暴露工具，并负责真正调用
3. `SubAgentToolExecutor`
   - 拦截工具调用，若命中子代理工具则执行 handoff
4. `HandoffPolicy`
   - 统一治理：是否允许、深度、重试、超时、失败动作
5. `HandoffContext`
   - 用 `ThreadLocal` 记录嵌套委托深度

## 3. 子代理是如何“变成工具”的

`StaticSubAgentRegistry` 会为每个 `SubAgentDefinition` 自动构造一个 function tool：

- `toolName`：默认 `subagent_<name>`（可手动指定）
- 参数 schema：`task`（必填）+ `context`（可选）

当主模型调用这个工具时：

1. registry 解析 arguments
2. 调用对应 `Agent`（new session 或复用 session）
3. 把子代理结果包装成 JSON 返回主链路

## 4. `SubAgentSessionMode` 选择

- `NEW_SESSION`（默认）
  - 每次 handoff 独立记忆
  - 线程安全、隔离性好
- `REUSE_SESSION`
  - 同一子代理工具复用会话
  - 有上下文连续性，适合长期任务

## 5. HandoffPolicy 参数详解

```java
HandoffPolicy policy = HandoffPolicy.builder()
        .enabled(true)
        .maxDepth(1)
        .maxRetries(0)
        .timeoutMillis(0L)
        .allowedTools(null)
        .deniedTools(null)
        .onDenied(HandoffFailureAction.FAIL)
        .onError(HandoffFailureAction.FAIL)
        .inputFilter(null)
        .build();
```

字段说明：

- `enabled`
  - 是否启用策略检查；false 时直接放行 subagent 执行。
- `maxDepth`
  - 最大嵌套深度；`1` 表示只允许 lead -> sub。
- `maxRetries`
  - 子代理异常后的重试次数（不含首次）。
- `timeoutMillis`
  - 单次 handoff 超时；`0` 表示不超时。
- `allowedTools`
  - 允许的 subagent tool 名单（空表示不限）。
- `deniedTools`
  - 显式拒绝名单。
- `onDenied`
  - 策略拒绝时动作：`FAIL` / `FALLBACK_TO_PRIMARY`
- `onError`
  - 子代理执行异常时动作：`FAIL` / `FALLBACK_TO_PRIMARY`
- `inputFilter`
  - 委托前改写参数（脱敏、裁剪、补充上下文）。

## 6. `FALLBACK_TO_PRIMARY` 是什么

当 handoff 失败/拒绝时，不抛异常，而是回退到主执行器（`ToolExecutor delegate`）执行同名工具。

常见用途：

- 子代理挂了，主工具兜底
- 某些环境禁用子代理时自动回退

## 7. 并行 handoff

在主 Agent 配 `parallelToolCalls=true` 且同轮返回多个 subagent tool call 时：

- `BaseAgentRuntime` 会并行执行工具
- `SubAgentToolExecutor` 内部也支持并发 handoff

这就是你测试里“Codex 风格并行委托”能跑起来的原因。

## 8. 完整示例

```java
SubAgentDefinition weather = SubAgentDefinition.builder()
        .name("weather")
        .toolName("delegate_weather")
        .description("Collect weather")
        .agent(weatherAgent)
        .build();

SubAgentDefinition formatter = SubAgentDefinition.builder()
        .name("formatter")
        .toolName("delegate_format")
        .description("Format answer")
        .agent(formatAgent)
        .build();

Agent lead = Agents.react()
        .modelClient(managerClient)
        .model("manager-model")
        .parallelToolCalls(true)
        .subAgents(Arrays.asList(weather, formatter))
        .handoffPolicy(HandoffPolicy.builder()
                .maxDepth(1)
                .maxRetries(1)
                .timeoutMillis(15000)
                .onError(HandoffFailureAction.FALLBACK_TO_PRIMARY)
                .build())
        .toolExecutor(primaryFallbackExecutor)
        .build();
```

## 9. 为什么开源组件一定要有 Handoff Policy

没有策略层，SubAgent 很容易出现：

1. 无限嵌套委托
2. 未授权工具越权调用
3. 失败行为不一致（有时抛异常，有时沉默）

`HandoffPolicy` 的价值就是把“智能协作”变成“可治理的系统能力”。

## 10. 你可直接参考的测试

- `SubAgentRuntimeTest`
  - 子代理工具暴露、调用与返回格式
- `SubAgentParallelFallbackTest`
  - 并行子代理 + 失败回退
- `HandoffPolicyTest`
  - allowed/denied、retry、timeout、maxDepth、inputFilter

如果你要做开源框架级能力，建议把这些测试当成行为契约。
