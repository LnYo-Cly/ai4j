---
title: 生命周期扩展
description: 讲清第六种插件能力 ExtensionCapability.LIFECYCLE：插件通过 context.lifecycle().register(hook) 注册 AgentLifecycleHook，在 session/turn/model/tool/compact 事件点接收 AgentLifecycleEvent，用于观察、遥测与审计，不贡献工具或资源。
tags: [how-to]
---

# 生命周期扩展
`LIFECYCLE` 是 `ExtensionCapability` 的第六种能力，和前五种（`TOOL` / `COMMAND` / `SKILL` / `PROMPT` / `GUARDRAIL`）不同：它不贡献工具或资源，而是让插件在 agent 执行的关键节点**收到通知**。它解决的是观察、遥测、审计、外部状态同步这类需求。

适用场景：

- 把每次 model request / tool call 记录到外部审计系统
- 在 session 开始时初始化插件持有的会话状态，在 session 结束时清理
- 在上下文压缩（compact）前后做快照或度量
- 不需要拦截决策（拦截走 Guardrail），只需要观察

如果你想在工具执行**前**允许或拒绝，那是 `GUARDRAIL` 的职责，不是 `LIFECYCLE`。lifecycle hook 是单向通知，不能阻断流程。

:::warning
`LifecycleHookRegistry` 和 `AgentLifecycleHook` 当前标注为 `@Experimental(since = "2.4.3")`，签名和行为可能在后续小版本调整。依赖时请 pin 死确切版本，详见 [Extension 总览 - 插件 SPI 的稳定性矩阵](/docs/extending/overview#32-插件-spi-的稳定性矩阵)。
:::

## 1. 声明能力并注册 hook

插件要在 manifest 里声明 `LIFECYCLE` 能力，否则在 `apply(...)` 里调用 `context.lifecycle().register(...)` 会抛 `ExtensionException("did not declare capability: lifecycle")`。

```java
public ExtensionManifest manifest() {
    return ExtensionManifest.builder()
            .id("audit-pack")
            .name("Audit Pack")
            .version("1.0.0")
            .vendor("Example")
            .capability(ExtensionCapability.LIFECYCLE)
            .build();
}

public void apply(ExtensionContext context) {
    // ponytail: 只注册，不做网络/IO；副作用放到 onEvent 里按需触发
    context.lifecycle().register(new AuditHook());
}
```

`AgentLifecycleHook` 只有两个方法：

```java
public interface AgentLifecycleHook {
    String name();
    void onEvent(AgentLifecycleEvent event) throws Exception;
}
```

- `name()` 是 hook 的唯一标识，同一个插件内不能重复，否则 `snapshot()` 会 fail-fast（`duplicate lifecycle hook id`）。命名规则与其它公共 ID 一致：字母数字开头，只能含字母、数字、点、下划线、连字符。
- `onEvent(...)` 可以抛异常。抛出的异常**不会中断 agent**，而是被 dispatcher 转成一条 `ERROR` 类型的 `AgentEvent` 发布出去（见第 4 节）。

一个最小的 hook 实现：

```java
public class AuditHook implements AgentLifecycleHook {
    public String name() {
        return "audit.tool-call";
    }

    public void onEvent(AgentLifecycleEvent event) {
        if (event.getType() == AgentLifecycleEventType.AFTER_TOOL_CALL) {
            // 只观察，不阻断；记录到外部审计存储
            auditStore.record(event.getSessionId(), event.getStep(), event.getPayload());
        }
    }
}
```

## 2. 事件类型

`AgentLifecycleEventType` 枚举覆盖 agent 一次执行的十个节点：

| 事件类型 | 触发时机 |
| --- | --- |
| `SESSION_START` | agent 会话开始 |
| `SESSION_END` | agent 会话结束 |
| `BEFORE_TURN` | 每一轮 agent loop 开始前 |
| `AFTER_TURN` | 每一轮 agent loop 结束后 |
| `BEFORE_MODEL_REQUEST` | 发起模型请求前 |
| `AFTER_MODEL_RESPONSE` | 收到模型响应后 |
| `BEFORE_TOOL_CALL` | 执行工具调用前 |
| `AFTER_TOOL_CALL` | 执行工具调用后 |
| `BEFORE_COMPACT` | 上下文压缩前 |
| `ON_COMPACT` | 上下文压缩后 |

hook 实现里用 `event.getType()` 判断当前节点，只处理关心的事件，其它直接返回即可。dispatcher 会对所有事件类型都调用所有已注册 hook，因此忽略不关心的事件是常态。

## 3. 事件负载

`AgentLifecycleEvent` 用 builder 构造，由 agent 运行时在分发时填充，字段含义：

| 字段 | 含义 |
| --- | --- |
| `type` | 事件类型，必填 |
| `runtime` | agent runtime 标识（例如 `react`），可空 |
| `sessionId` | agent 会话 id，来自 `AgentContext`，可空 |
| `step` | agent loop 步数 |
| `message` | 人读消息，可空 |
| `payload` | 事件相关负载对象，结构随事件类型变化 |
| `attributes` | 附加键值对（不可变 map） |

`attributes` 返回不可修改的 `Map<String, Object>`，空时是空 map 而不是 `null`。hook 里读 `payload` 时要按事件类型预期来解释，建议对类型做防御性判断，避免在不同事件点强转。

## 4. agent 如何分发

agent 侧的 `AgentLifecycleHookDispatcher` 负责把事件分发给从 `ExtensionRegistry` 收集到的 hook 列表。关键行为：

- 已启用（`enable(...)`）插件注册的 hook 才会进入运行时；未启用的插件即使注册了 hook，也不会被分发。
- dispatcher 对每个 hook 单独 `try/catch`：单个 hook 抛异常**不会中断** agent，也不会影响其它 hook。
- hook 抛出的异常会被包装成 `AgentLifecycleHookError`（包含 hook 名、事件、原始异常），作为一条 `AgentEventType.ERROR` 的 `AgentEvent` 通过 `AgentEventPublisher` 发布。宿主可以通过现有的 agent event listener 观察这些错误。
- `payload`/`message` 等字段由 dispatcher 按节点填充；`sessionId` 取自 `AgentContext`。

也就是说，lifecycle hook 是“尽力而为的观察点”：你可以信任它被调用，但不能信任它返回正常——它失败只会变成一条错误事件，不会让 agent 停下来。

## 5. 使用者侧的接入

lifecycle hook 不需要 `exposeTool(...)` 或 `allow*(...)`：它不是模型可见工具，也不是显式授权的资源。它跟随插件 `enable(...)` 进入运行时。这是它与 skill / prompt / guardrail / tool 的一个重要区别。

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("audit-pack");   // 注册的 lifecycle hook 进入 agent 运行时

Agent agent = Agent.builder()
        // ... model / tools 配置 ...
        .extensions(registry)
        .build();
```

`ExtensionRegistry.getLifecycleHooks()`（或 `snapshot().getLifecycleHooks()`）返回当前已启用插件贡献的全部 hook，agent 在构造时把它们交给 `AgentLifecycleHookDispatcher`。

## 6. 常见错误

| 错误 | 后果 | 修正 |
| --- | --- | --- |
| 没在 manifest 声明 `LIFECYCLE` 就注册 hook | `snapshot()` 抛 `did not declare capability: lifecycle` | manifest 加 `.capability(ExtensionCapability.LIFECYCLE)` |
| 同一插件注册重名 hook | `snapshot()` 抛 `duplicate lifecycle hook id` | 每个 hook 的 `name()` 保持唯一 |
| 在 `onEvent(...)` 里做长时间阻塞 IO | 拖慢 agent 每个事件点 | 异步化或限制超时，hook 应快速返回 |
| 把拦截逻辑写在 lifecycle hook 里 | hook 无法阻断流程，拦截无效 | 拦截改用 `GUARDRAIL` |
| 在 `apply(...)` 而不是 `onEvent(...)` 里发网络请求 | `validate` / `inspect --runtime` 会触发副作用 | 副作用放到 `onEvent(...)`，`apply(...)` 只注册 |

## 7. 下一步阅读

1. [插件包](/docs/extending/plugins/plugin-packages)
2. [插件作者实战指南](/docs/extending/plugins/plugin-author-cookbook)
3. [扩展 SPI 内部机制](/docs/extending/plugins/extension-spi)
4. [Agent Tools and Registry](/docs/agent/tools-and-registry)
