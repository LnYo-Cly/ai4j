# P0-C Agent plugin lifecycle hooks - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### Extension API 当前是 registry/context 风格，生命周期 Hook 应作为同级贡献物

- 背景：P0-C 需要让第三方插件贡献生命周期 Hook，但不能破坏已有 Tool/Command/Skill/Prompt/Guardrail 贡献方式。
- 发现：`Ai4jExtension` 当前只有 `manifest()` 和 `apply(ExtensionContext)`；`ExtensionContext` 暴露多个 registry；`ExtensionRuntimeState` 负责聚合贡献物并生成 `ExtensionRuntimeSnapshot`。
- 影响：首选设计是在 extension API 增加 lifecycle registry，并让 enabled extension 在 `apply(context)` 中注册 Hook；不要求老插件实现新方法。
- 后续：实现时新增 `io.github.lnyocly.ai4j.extension.lifecycle` 包，并把 lifecycle hooks 纳入 runtime snapshot。

### Agent runtime 已有事件发布面，Hook 异常应沿用事件化思路

- 背景：Hook 来自第三方插件，默认不能让插件错误直接破坏 Agent 主流程。
- 发现：`BaseAgentRuntime` 已通过 `publish(...)` 发布 `STEP_START`、`MODEL_REQUEST`、`MODEL_RESPONSE`、`TOOL_CALL`、`TOOL_RESULT`、`ERROR`、`MEMORY_COMPRESS` 等事件。
- 影响：Agent-side dispatcher 可以复用 `AgentEventPublisher` / listener 观测面；Hook 抛异常时默认发布 `ERROR` payload 并继续。
- 后续：实现时补测试断言 Hook 异常被记录且最终 `AgentResult` 仍返回。

### 首版应 observation-first，不做可变拦截器

- 背景：用户希望插件生态像 Pi 一样可由第三方扩展，但本项目需要控制个人维护成本。
- 发现：如果 Hook 允许修改 prompt、tool arguments、model result，会立刻引入拦截顺序、冲突、回滚和安全策略问题。
- 影响：P0-C 只交付观察型 Hook；可变 HookResult / chain-of-responsibility 放到后续独立任务评估。
- 后续：docs-site 需要明确“首版 Hook 是观察点，不是 prompt/tool 参数改写器”。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| lifecycle 注册方式 | `ExtensionContext.lifecycle().register(...)` | 与现有 extension registry 风格一致，老插件无需新增方法 | `Ai4jExtension.default lifecycleHooks()` 会绕开现有 apply/context 模型 | accepted |
| Hook 语义 | observation-first | 控制公共 API 风险和第三方副作用 | 首版支持可变 before/after result | accepted |
| 异常策略 | 默认 record-and-continue | 第三方插件异常不应默认中断 Agent 主流程 | fail-fast 默认策略 | accepted |
| P0-C 覆盖范围 | extension-api + ai4j-agent + docs-site | Hook 公共合同和 runtime 触发必须一起落地 | 只写 extension API 不接 runtime | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| Hook 事件 payload 是否暴露完整 prompt/model raw response | 默认可带 payload，但文档强调插件作者不要记录敏感内容；如需脱敏策略另开任务 | coordinator | 实现前 |
| `onSessionStart/onSessionEnd` 是否首版自动触发 | 当前没有显式 close/end 生命周期；P0-C 可先定义 event type，自动触发只覆盖可明确定位的 turn/model/tool/compact | coordinator | 实现前 |
| 是否需要 allowlist lifecycle hook | 首版跟随 extension enable，不增加单 Hook allowlist；若用户需要更细粒度治理，后续扩展 | coordinator | 实现前 |
