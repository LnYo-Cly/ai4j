# AI4J Extension Permission and Install UX - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。阻塞性问题以 `review.md` 为准。

## 研究发现

### Extension resource trust boundary

- 背景：F-038 已经明确 `enable(...)` 对非 tool 资源是整包信任边界，本任务需要在不破坏兼容的前提下细化授权。
- 发现：tool 暴露和 command/Skill/Prompt/Guardrail 激活是不同边界；tool 必须继续由 `exposeTool(...)` 单独控制，非 tool 资源可以通过显式 allowlist 过滤 runtime snapshot。
- 影响：新增 `requireExplicitResourceActivation()` 和 `allow*` API，同时保留 `enable(...)` 默认兼容行为。
- 后续：真实第三方插件生态验证可在后续任务中补充。

### CLI pre-integration check

- 背景：使用者在把插件依赖加入 classpath 后，需要在接入 Agent 之前知道实际贡献了什么资源。
- 发现：`inspect --runtime` 能列资源，但不能表达“这次启用/授权后哪些 active”。因此需要单独 activation plan。
- 影响：新增 `ai4j-cli extension plan <id> --enable ... --strict`，输出每类资源的 active/inactive 状态和原因。
- 后续：如果未来有远程 marketplace，仍应保留 plan 作为本地 classpath 级检查入口。

### Spring binding parity

- 背景：普通 Java 和 Spring Boot starter 需要表达同一套权限模型。
- 发现：Spring starter 只应绑定配置并创建 `ExtensionRegistry` / `ExtensionRuntimeSnapshot`，不能替宿主创建 Agent，也不能自动安装插件依赖。
- 影响：新增 `ai.extensions.explicit-resource-activation` 与 `ai.extensions.{commands,skills,prompts,guardrails}.allow`，并在 snapshot 创建时 fail-fast 验证未注册资源。
- 后续：无。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 默认兼容 | `enable(...)` 继续激活非 tool 资源 | 不破坏已有宿主行为 | 默认全部 strict | accepted |
| 严格模式入口 | `requireExplicitResourceActivation()` 和任意 `allow*` 自动开启 strict | 使用者声明 allowlist 时应得到严格过滤 | 只允许显式调用 strict | accepted |
| activation plan | 新增 `ExtensionActivationPlan` / `ExtensionActivationItem` | 让 CLI 和宿主在接入前可解释资源状态 | 复用 runtime snapshot 输出 | accepted |
| CLI 语义 | `plan` 支持 `--expose-tool` 和全部 `--allow-*`；`run/resource` 只支持相关 allow 参数 | 避免把 tool 暴露和人工 command/resource 路径混在一起 | 所有命令都支持所有 activation 参数 | accepted |
| Spring 配置 | starter 映射 allowlist，不自动创建 Agent | 保持 starter 模块边界 | 自动把工具接入 Agent | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要真实第三方插件包做生态 smoke | 本任务不要求；本地 API/CLI/Spring/Ask User 合同已覆盖 | maintainer | 后续生态/样板插件任务 |
| 是否通过人工 Review Confirmation | 等待用户确认或退回 | human | review 队列 |
