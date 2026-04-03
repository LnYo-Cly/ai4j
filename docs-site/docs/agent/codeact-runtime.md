---
sidebar_position: 7
---

# CodeAct 运行时（代码驱动工具调用）

这页会把 CodeAct 相关类、参数、执行时机讲透：

- `CodeActRuntime`
- `CodeActOptions`
- `CodeExecutor / GraalVmCodeExecutor`
- 代码与工具如何互调
- 失败后如何自动修复

## 1. CodeAct 是什么

CodeAct 不是“普通 function call 的别名”，而是完整闭环：

1. 模型先输出代码（JSON 包裹）
2. Runtime 执行代码
3. 代码内部调用工具
4. 执行结果再决定最终输出（直接返回或回给模型总结）

## 2. 模型输出协议（必须遵守）

`CodeActRuntime` 约定模型只输出 JSON：

- 执行代码：

```json
{"type":"code","language":"python","code":"..."}
```

- 最终回答：

```json
{"type":"final","output":"..."}
```

如果模型输出不是合法 JSON，Runtime 会按普通文本兜底返回。

## 3. 关键类和职责

- `CodeActRuntime`：CodeAct 主循环策略
- `CodeActOptions`：CodeAct 专属开关（当前是 `reAct`）
- `CodeExecutionRequest`：代码执行入参
- `CodeExecutionResult`：执行结果（`stdout/result/error`）
- `CodeExecutor`：执行器接口
- `GraalVmCodeExecutor`：默认执行器（Python + JS）

## 4. `CodeActOptions.reAct` 的语义

## `reAct = false`（默认）

- 代码执行成功后，结果可直接作为最终输出。
- 适合“工具结果就是答案”的场景。

## `reAct = true`

- 代码执行结果会回写 memory，模型再来一轮自然语言整理。
- 适合“要人类可读总结”的场景。

```java
.codeActOptions(CodeActOptions.builder().reAct(true).build())
```

## 5. 执行时的工具注入与调用

执行器会注入两种调用方式：

1. `callTool("queryWeather", {...})`
2. 直接按工具名调用（如 `queryWeather(location="Beijing", ...)`）

CodeAct 代码示例（Python）：

```python
cities = ["Beijing", "Shanghai", "Shenzhen"]
lines = []
for city in cities:
    weather = queryWeather(location=city, type="daily", days=1)
    lines.append(f"{city}: {weather}")
__codeact_result = "\n".join(lines)
```

> 约定：若不 `return`，请赋值 `__codeact_result`。

## 6. 为什么你会看到“函数结果直接拼到输出里”

因为这是代码自身的行为，不是 Runtime 强制拼接。

例如：

```python
summary_parts.append(f"Weather in {city}: {weather_data}")
```

这段代码当然会把原始工具结果字符串拼出来。

如果你想要结构化/可读结果，需要在代码里先 parse，再 format，或开启 `reAct=true` 让模型再整理一轮。

## 7. 失败后自动修复是否支持

支持，机制是“多步循环 + 错误反馈”：

- 代码执行失败 -> Runtime 写入 `CODE_ERROR: ...`
- 模型在下一 step 看见错误 -> 重新生成代码
- 直到成功或达到 `maxSteps`

要让这个链路更稳定：

1. `maxSteps` 不要太小（建议 `>=3`）
2. system prompt 写清楚“失败后修复并重试”
3. 保持工具 schema 明确、参数名固定

## 8. 如何在“执行前”看到代码

默认 `CODEACT CODE` 你可能在最后才打印，这是因为你在结果里读了 `toolCalls`。

更推荐做法：监听 `TOOL_CALL` 事件并在 `name=code` 时打印。

```java
AgentEventPublisher publisher = new AgentEventPublisher();
publisher.addListener(event -> {
    if (event.getType() == AgentEventType.TOOL_CALL && event.getPayload() instanceof AgentToolCall) {
        AgentToolCall call = (AgentToolCall) event.getPayload();
        if ("code".equals(call.getName())) {
            System.out.println("CODEACT CODE (pre-exec): " + call.getArguments());
        }
    }
});
```

## 9. GraalVmCodeExecutor 语言支持与回退

当前默认支持：

- `python`（GraalPy）
- `js/javascript`（Polyglot + ScriptEngine 回退）

执行器行为要点：

- 有超时控制（默认 8 秒，可由 `timeoutMs` 覆盖）
- 会捕获 `stdout/error`
- `result` 优先读取 `__codeact_result` 或表达式返回值

## 10. 一份完整测试模板

```java
Agent agent = Agents.codeAct()
        .modelClient(new ResponsesModelClient(aiService.getResponsesService(PlatformType.DOUBAO)))
        .model("doubao-seed-1-8-251228")
        .systemPrompt("You are a weather assistant. Use Python only.")
        .toolRegistry(Arrays.asList("queryWeather"), null)
        .options(AgentOptions.builder().maxSteps(4).build())
        .codeActOptions(CodeActOptions.builder().reAct(true).build())
        .eventPublisher(buildEventPublisher())
        .build();
```

参考测试：

- `CodeActRuntimeTest`
- `CodeActRuntimeWithTraceTest`

## 11. 生产建议（重要）

默认执行器不是强隔离沙箱；生产环境建议：

1. 独立进程或容器执行
2. CPU/内存/时间限制
3. 文件系统与网络权限最小化
4. 工具白名单 + 参数校验

这样才能让 CodeAct 从“能跑”升级为“可上线”。
