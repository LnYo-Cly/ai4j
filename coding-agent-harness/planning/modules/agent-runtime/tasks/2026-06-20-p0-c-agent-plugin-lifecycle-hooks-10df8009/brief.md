# P0-C Agent plugin lifecycle hooks

## Task ID

`2026-06-20-p0-c-agent-plugin-lifecycle-hooks-10df8009`

## 创建日期

2026-06-20

## 一句话结果

为 `ai4j-extension-api` 和 `ai4j-agent` 增加可选的 Agent 生命周期 Hook 基础，让第三方插件不只贡献 Tool/Command，还能观察 Agent turn、模型请求/响应、工具调用和 compact 等关键节点。

## 完成后能得到什么

完成后，AI4J 的插件生态会从“安装工具包”升级到“参与 Agent 运行过程”。插件作者可以通过公共合同注册生命周期 Hook；Agent runtime 负责按稳定顺序触发 Hook，并把异常记录为事件而不是默认打断主流程。使用者仍然通过现有 extension registry 启用插件，老插件无需修改也能继续运行。这个基础会成为后续 Blueprint YAML、插件市场、Memory/Compact 策略插件、SandboxProvider 和 CLI 插件体验的前置能力。

## 交付物

- 可见产物：生命周期 Hook 公共 API、runtime dispatcher、AgentBuilder/AgentContext 接入、runtime 触发点、测试和 docs-site 页面。
- 修改位置：`ai4j-extension-api/**`、`ai4j-agent/**`、`docs-site/docs/agent/**`、`docs-site/sidebars.ts`、本任务 Harness 包。
- 验证证据：extension-api targeted tests、agent targeted tests、跨模块 Maven regression、docs-site build、Harness status。

## 第一眼应该看什么

先读 `references/p0-c-agent-plugin-lifecycle-hooks-plan.md`，再读 `task_plan.md` 的范围/验收标准和 `visual_map.md` 的阶段表。实现时从 `Ai4jExtension`、`ExtensionRuntimeState`、`ExtensionRuntimeSnapshot`、`ExtensionAgentTools`、`AgentBuilder`、`AgentContext`、`BaseAgentRuntime`、`CodeActRuntime`、`AgentSession` 这些入口开始。

## 边界

- 范围内：新增 optional lifecycle hook contract；把启用插件贡献的 Hook 纳入 runtime snapshot；在 ReAct/Base runtime、CodeAct runtime 和 session compact 的关键节点触发；补 deterministic tests 和 docs-site 说明。
- 范围外：不做 YAML Blueprint、不做 Sandbox SPI、不做远端 Runner、不做 CLI/TUI 插件 UI、不做真实插件市场、不引入 provider token 或 live-provider gate。
- 停止条件：如果 Hook 需要修改模型请求/工具参数的可变语义、需要新增跨模块 Maven module、或会改变老插件启用/暴露规则，必须先停下回到 coordinator 重新定界。

## 完成判断

- 老插件不实现 Hook 时仍可编译和运行。
- 新 Hook 插件能在启用后被 registry/snapshot 收集，并在 agent runtime 中按顺序收到事件。
- Hook 异常默认不会破坏 Agent 主流程，会被发布为 `ERROR` 或等价事件并可在测试中断言。
- ReAct/Base runtime、CodeAct runtime、session compact 至少覆盖最小触发点。
- docs-site 说明插件生命周期定位、可用 Hook、异常策略和不在范围内的能力。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

按 `references/p0-c-agent-plugin-lifecycle-hooks-plan.md` 的 Option A 实施：先在 `ai4j-extension-api` 增加观察型 lifecycle contract 和 registry snapshot，再在 `ai4j-agent` 增加 dispatcher 与最小 runtime 触发点。
