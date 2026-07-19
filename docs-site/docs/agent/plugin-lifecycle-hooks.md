---
sidebar_position: 7
---

# Plugin Lifecycle Hooks

`ai4j-extension-api` 现在支持 Agent 生命周期 Hook。它让插件不只贡献 Tool、Command、Skill、Prompt 或 Guardrail，还可以观察 Agent 的运行过程。

这是一层插件生态基础能力，适合做：

- 运行审计和 trace 补充
- 工具调用统计
- prompt / model request 观察
- memory / compact 策略前置准备
- sandbox、runner、CLI 插件体验的后续接入点

:::info
首版 lifecycle hook 是 **observation-first**：Hook 可以观察事件和 payload，但不是 prompt / tool / model response 的可变拦截器。
:::

## 1. 插件如何注册 Hook

插件继续实现 `Ai4jExtension`，并在 `apply(ExtensionContext context)` 中注册：

```java
public final class AuditExtension implements Ai4jExtension {
    @Override
    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id("audit-pack")
                .name("Audit Pack")
                .version("1.0.0")
                .vendor("example")
                .capability(ExtensionCapability.LIFECYCLE)
                .build();
    }

    @Override
    public void apply(ExtensionContext context) {
        context.lifecycle().register(new AgentLifecycleHook() {
            @Override
            public String name() {
                return "audit.lifecycle";
            }

            @Override
            public void onEvent(AgentLifecycleEvent event) {
                System.out.println(event.getType() + " step=" + event.getStep());
            }
        });
    }
}
```

使用者侧仍然通过 `ExtensionRegistry` 启用插件：

`ai4j-cli extension inspect <id> --runtime` now prints a `lifecycleHooks=` line, so plugin authors can verify that hooks were packaged and discovered before wiring them into an Agent.

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("audit-pack");

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .extensions(registry)
        .build();
```

没有实现 lifecycle hook 的老插件不需要修改。

## 2. 当前事件类型

| Event | 触发时机 |
| --- | --- |
| `BEFORE_TURN` | 每个 Agent step 开始后 |
| `AFTER_TURN` | 每个 Agent step 收尾前 |
| `BEFORE_MODEL_REQUEST` | prompt 构建完成、调用模型前 |
| `AFTER_MODEL_RESPONSE` | 模型返回后 |
| `BEFORE_TOOL_CALL` | 工具或 CodeAct 代码执行前 |
| `AFTER_TOOL_CALL` | 工具或 CodeAct 代码执行后 |
| `ON_COMPACT` | `AgentSession.compact(...)` 产生 compact result 后 |
| `SESSION_START` | 已保留，首版不自动触发 |
| `SESSION_END` | 已保留，首版不自动触发 |

`SESSION_START` / `SESSION_END` 目前只是保留事件类型。因为当前 Agent 还没有稳定的显式 close/end 生命周期，首版不会猜测触发点。

## 3. Event payload 包含什么

`AgentLifecycleEvent` 提供：

| 字段 | 说明 |
| --- | --- |
| `type` | 生命周期事件类型 |
| `runtime` | `react`、`codeact`、`session` 等来源 |
| `sessionId` | 如果来自 `AgentSession`，会带 session id |
| `step` | 当前 step |
| `message` | 轻量说明，例如 runtime 名称或 tool 名称 |
| `payload` | 对应事件的上下文对象，例如 `AgentPrompt`、`AgentModelResult`、`AgentToolCall`、`CompactResult` |
| `attributes` | 扩展属性，首版用于插件自定义上下文 |

payload 是运行态对象，不建议插件直接持久化完整原文。尤其是 prompt、model raw response、tool arguments 可能包含用户输入、业务数据或配置内容。

## 4. 异常策略

Hook 默认使用 `record-and-continue`：

1. dispatcher 捕获 Hook 抛出的异常。
2. Agent 发布 `AgentEventType.ERROR`。
3. 主 Agent loop 继续执行。

这样设计是为了避免第三方插件 bug 直接破坏核心 Agent 流程。

如果后续需要“Hook 失败就中断”的能力，应该通过显式策略单独加入，而不是作为默认行为。

## 5. 与 Guardrail 的区别

| 能力 | 作用 |
| --- | --- |
| Guardrail | 做策略判断，例如是否允许某个工具调用 |
| Lifecycle Hook | 观察运行过程，例如记录模型请求、工具结果、compact 状态 |

如果你要阻止或放行某个行为，用 Guardrail。  
如果你要记录、统计、审计或把事件转给外部系统，用 Lifecycle Hook。

## 6. 当前限制

首版不做这些事：

- 不修改 prompt、tool arguments 或 model response。
- 不提供 Hook allowlist；Hook 跟随 extension enable 生效。
- 不提供插件市场或远端安装协议。
- 不直接接入 sandbox provider。
- 不把任何 OpenAI-compatible 中转平台名称写成 SDK 概念。

这些能力可以在后续 Blueprint、Sandbox SPI、CLI/TUI 插件体验中继续扩展。
