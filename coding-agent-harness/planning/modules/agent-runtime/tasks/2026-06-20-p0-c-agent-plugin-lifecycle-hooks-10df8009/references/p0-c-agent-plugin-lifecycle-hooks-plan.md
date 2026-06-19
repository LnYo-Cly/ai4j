# P0-C Agent Plugin Lifecycle Hooks Plan

> Task: `MODULES/agent-runtime/2026-06-20-p0-c-agent-plugin-lifecycle-hooks-10df8009`  
> Scope: `ai4j-extension-api` + `ai4j-agent` + docs-site; planning and implementation target.  
> Decision: implement a small observation-first lifecycle hook foundation.

## 1. Why this task exists

AI4J 的插件生态已经有 Tool、Command、Skill、Prompt、Guardrail，但这仍然偏“资源贡献”。如果要靠近 Pi 那类可组合插件生态，第三方插件还需要参与 Agent 运行过程，例如记录审计、观察模型请求、统计工具调用、接入 memory/compact/sandbox 策略。

本任务只做生命周期 Hook 的最小内核，不一次性做完整插件市场、UI 扩展、YAML Blueprint 或 SandboxProvider。

## 2. Design options

### Option A — ExtensionContext lifecycle registry（推荐）

插件继续实现：

```java
public final class MyExtension implements Ai4jExtension {
    public ExtensionManifest manifest() { ... }

    public void apply(ExtensionContext context) {
        context.lifecycle().register(new MyLifecycleHook());
    }
}
```

优点：

- 与现有 `context.tools()`、`context.commands()`、`context.guardrails()` 风格一致。
- 老插件不需要新增方法或重新编译源代码。
- `ExtensionRuntimeState` 和 `ExtensionRuntimeSnapshot` 可以继续做统一聚合。
- 后续可自然扩展到 Memory provider、Compact strategy、SandboxProvider。

缺点：

- 需要给 `ExtensionContext` 增加一个新 registry 方法。
- 需要同步更新 runtime state、snapshot、inspection/activation 相关模型。

结论：采用。

### Option B — `Ai4jExtension.default lifecycleHooks()`

优点：新增方法很直接。

缺点：

- 绕开现有 `apply(context)` 的贡献物注册模型。
- 后续与 manifest/inspection/activation plan 的统一性较差。
- 容易形成“有些能力在 context，有些能力在 extension method”的双入口。

结论：不采用。

### Option C — 只在 `ai4j-agent` 暴露 builder hook

优点：实现最少。

缺点：

- 第三方插件生态无法通过 extension package 安装和组合。
- 不能解决“插件不仅贡献 Tool，还能参与 Agent 生命周期”的核心目标。

结论：不采用。

## 3. Public contract shape

建议新增包：

```text
io.github.lnyocly.ai4j.extension.lifecycle
```

建议对象：

- `AgentLifecycleHook`
- `AgentLifecycleEvent`
- `AgentLifecycleEventType`
- `LifecycleHookRegistry`
- `LifecycleHookRegistration` 或直接使用 hook list

首版事件类型：

```text
BEFORE_TURN
AFTER_TURN
BEFORE_MODEL_REQUEST
AFTER_MODEL_RESPONSE
BEFORE_TOOL_CALL
AFTER_TOOL_CALL
ON_COMPACT
```

可先保留但不自动触发：

```text
SESSION_START
SESSION_END
```

原因：当前 Agent 没有稳定的显式 close/end 生命周期，贸然触发 `SESSION_END` 容易语义不准。

## 4. Observation-first boundary

P0-C 的 Hook 是观察点，不是拦截器。Hook 收到事件和 payload，但默认不能直接改写：

- prompt items
- model request
- model response
- tool arguments
- tool result

如果后续要支持可变 Hook，应另开任务设计：

- `HookResult`
- chain order
- conflict policy
- fail-fast policy
- audit trail
- sensitive payload redaction

## 5. Runtime wiring

推荐链路：

```text
Ai4jExtension.apply(context)
  -> context.lifecycle().register(...)
  -> ExtensionRuntimeState
  -> ExtensionRuntimeSnapshot
  -> ExtensionAgentTools
  -> AgentBuilder.extensions(...)
  -> AgentContext
  -> AgentLifecycleHookDispatcher
  -> BaseAgentRuntime / CodeActRuntime / AgentSession.compact(...)
```

`AgentLifecycleHookDispatcher` 的职责：

1. 按注册顺序触发 hook。
2. 对 null hook/event 做防御。
3. 捕获 hook 异常。
4. 默认发布 `ERROR` event 并继续。
5. 不持有 provider secret 或不可序列化状态。

## 6. Trigger points

Base/ReAct runtime:

- loop 开始：`BEFORE_TURN`
- prompt 构建后、模型调用前：`BEFORE_MODEL_REQUEST`
- 模型返回后：`AFTER_MODEL_RESPONSE`
- 每个工具执行前：`BEFORE_TOOL_CALL`
- 每个工具执行后：`AFTER_TOOL_CALL`
- step 收尾：`AFTER_TURN`

CodeAct runtime:

- 如果复用 Base runtime path，则只补覆盖测试。
- 如果有独立执行路径，应触发同样的最小事件集，至少 model/tool/turn 不缺失。

Compact:

- `AgentSession.compact(CompactPolicy)` 成功生成 result 后触发 `ON_COMPACT`。
- 如果 compact policy 失败，不在本任务新增复杂异常语义，沿用现有异常路径。

## 7. Error policy

默认策略：record-and-continue。

原因：

- 插件来自第三方，默认不能让插件 bug 破坏核心 Agent loop。
- 当前 `BaseAgentRuntime` 已有 `AgentEventType.ERROR`。
- fail-fast 可以后续通过显式 policy 加入。

测试必须覆盖：

- Hook 抛异常。
- Agent 最终仍返回结果。
- `ERROR` event 或等价事件被发布。

## 8. Activation policy

首版 lifecycle hooks 跟随 extension enable：

- extension enabled -> hooks active
- extension disabled -> hooks inactive

不新增单 Hook allowlist。

原因：

- 当前 Tool 有 expose allowlist，是因为 Tool 会暴露给模型调用。
- Hook 是 host-side observer，首版更像 Guardrail/Prompt/Skill 资源。
- 如后续用户需要更细粒度治理，再引入 `allowLifecycleHook(...)`。

## 9. Tests

Extension API tests:

- old extension without lifecycle still works。
- lifecycle registry registers hooks into snapshot。
- disabled extension hooks do not appear in snapshot。
- registration order is stable。

Agent runtime tests:

- fake lifecycle extension receives turn/model/tool events in expected order。
- hook exception records error and does not break result。
- `AgentBuilder.extensions(ExtensionRegistry)` automatically wires hooks。
- no extension/no hook path remains unchanged。
- compact emits `ON_COMPACT` event when session compact is called。

Suggested commands:

```powershell
mvn -pl ai4j-extension-api "-Dtest=*Lifecycle*" -DskipTests=false test
mvn -pl ai4j-agent "-Dtest=AgentPluginLifecycleHooksTest" -DskipTests=false test
mvn -pl ai4j-extension-api,ai4j-agent -am -DskipTests=false test
```

Docs:

```powershell
cd docs-site
npm run build
```

Harness:

```powershell
npx --yes coding-agent-harness status --json .
```

## 10. Docs-site updates

Add:

```text
docs-site/docs/agent/plugin-lifecycle-hooks.md
```

Update:

- `docs-site/docs/agent/sdk-roadmap.md`
- `docs-site/sidebars.ts`

Docs must state:

- what lifecycle hooks are for
- how a plugin registers a hook
- which event types exist
- what payload stability means
- default error policy
- first-version limitations

Avoid:

- claiming a full plugin marketplace exists
- claiming hooks can mutate prompt/tool/model outputs
- mentioning any provider token or specific relay platform as an SDK concept

## 11. Done definition

This task is done only when:

1. extension-api and ai4j-agent targeted tests pass.
2. cross-module regression passes.
3. docs-site build passes.
4. Harness status has no failures.
5. review packet has no open P0/P1/P2 finding.
6. PR checks pass before merge.
