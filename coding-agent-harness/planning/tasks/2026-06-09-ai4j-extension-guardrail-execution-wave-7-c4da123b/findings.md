# AI4J extension guardrail execution wave 7 - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### Guardrail 应作用在实际 tool executor 边界，而不是 tool registry 边界

- 背景：插件 Guardrail 不应该让模型看到更多工具，也不应该依赖 `exposeTool(...)` 才能工作。
- 发现：`ExtensionRuntimeSnapshot` 已保留启用插件注册的 Guardrail；实际缺口在 Agent / Coding Agent 的 tool executor 调用链没有消费这些 Guardrail。
- 影响：本轮新增 `ExtensionGuardrailToolExecutor` 作为 executor wrapper。它不会改变 tool registry，只在 `ToolExecutor.execute(...)` 前构造 `GuardrailRequest(action="tool.execute", target=<toolName>)` 并按顺序评估 guardrails。
- 后续：如果未来要支持 prompt / model request / response guardrail，需要新增 action contract，不应复用 `tool.execute`。

### Coding Agent 主会话和 delegated child session 都会重建 executor

- 背景：Coding Agent build 阶段已有完整 tool executor，但 `CodingAgent.newSession()` 会按当前 session process registry 重建内置工具 executor；DefaultCodingRuntime delegated child session 也会重建 executor 并套 tool policy。
- 发现：只在 `CodingAgentBuilder.build()` 包装 Guardrail 会被 `newSession()` 路径绕过。初始 targeted test 里 `bash` 实际执行并返回 `should-not-run`，暴露了这个缺口。
- 影响：本轮把 `ExtensionAgentTools` 保存在 `CodingAgent` 和 `DefaultCodingRuntime`，并在主会话和 delegated child session 的最终 executor 上应用 `CodingAgentBuilder.applyExtensionGuardrails(...)`。
- 后续：新增或重构 Coding Agent session/executor 构建路径时，应检查是否仍经过 Guardrail wrapper。

### CLI extension run/resource 保持人工显式路径

- 背景：插件生态同时包含 Agent tool loop 和 CLI 手动命令 / 资源读取入口。
- 发现：`extension run --enable` 是人手动调用 command；`extension resource --enable` 是人手动读取 UTF-8 资源。二者不是模型 tool call，也不会走 `ToolExecutor.execute(AgentToolCall)`。
- 影响：本轮不把 CLI 命令纳入 `tool.execute` Guardrail，并在 docs-site 里明确边界，避免用户误以为 Guardrail 会拦截所有 CLI 子命令。
- 后续：如果需要 CLI command guardrail，应另开 action contract，例如 `command.execute`，并明确是否适用于人手动命令。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Guardrail 执行位置 | `ToolExecutor` wrapper | 覆盖内置工具、custom tools、extension tools 和 subagent route 后的最终执行边界，不改变模型可见工具清单 | 在 registry 映射阶段过滤工具 | accepted |
| Request contract | `action=tool.execute`, `target=<toolName>`, attributes 含 arguments/callId/type | 简单稳定，第三方插件可按工具名和参数做判断 | 每种工具单独 action | accepted |
| Deny 处理 | 抛出 `ExtensionException`，由 Agent runtime 转成 `TOOL_ERROR` | 延续现有工具异常回写模型的语义，并能证明 delegate executor 未执行 | 在 wrapper 内直接返回自定义字符串 | accepted |
| Coding Agent 接线 | builder、主 session、delegated child session 均保留 extensionTools 并包装最终 executor | Coding Agent 会多处重建 executor，必须覆盖所有实际执行路径 | 只在 build 阶段包装 | superseded |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要 Guardrail 拦截 CLI 手动 command | 当前不做；CLI 是 explicit human path，不属于 Agent tool loop | coordinator | 后续 CLI command governance 任务 |
| 是否需要更多 action contract | 当前只定义 `tool.execute`；prompt/model/response guardrail 另开设计 | coordinator | 新 guardrail action wave |
