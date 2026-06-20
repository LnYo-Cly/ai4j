# Plugin contribution contract expansion - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### 现有 ExtensionCapability 只能表达已注册 runtime surface

- 背景：路线图要求第三方能声明 sandbox provider、runner provider、memory/compact 等插件能力。
- 发现：当前 `ExtensionCapability` 只有 Tool、Command、Skill、Prompt、Guardrail、Lifecycle；`ExtensionInspectionSnapshot` 也只展示这些 runtime registry 的注册结果。
- 影响：如果直接为每种 provider 立刻加 registry，会把任务扩大成真实 provider 实现；更稳的是先补 manifest-level contribution metadata。
- 后续：真实 provider registry / binding 放到 sandbox routing、remote runner 或 CLI 任务。

### Provider-style 插件必须默认 host binding

- 背景：sandbox/runner/memory 这类贡献项可能涉及文件系统、远端资源、token 或隔离环境。
- 发现：插件 manifest 可以声明 provider 能力，但不能自动创建真实 provider，也不能自动读取密钥。
- 影响：`ExtensionContribution` 默认 `requiresExplicitActivation=true`；activation plan 对 provider-style contribution 显示 `requires host binding`。
- 后续：CLI/TUI 或宿主应用后续根据 contribution metadata 做安装提示、权限提示和 provider binding。

### Ask User 插件适合作为最小官方样例

- 背景：第三方插件作者需要真实 API 示例，docs-site 不能写伪 API。
- 发现：`ai4j-plugin-ask-user` 已覆盖 Tool、Command、Skill、Prompt，适合增加 contribution metadata。
- 影响：ask-user manifest 现在声明 tool、CLI command、skill、prompt contributions；测试固定其清单。
- 后续：docs-site 可引用 ask-user 作为完整插件作者 cookbook 的基础。

### worktree 缺少 engineering-standard.md

- 背景：AGENTS 阅读矩阵建议插件契约任务读取 `docs/11-REFERENCE/engineering-standard.md`。
- 发现：该 worktree 的 `docs/11-REFERENCE/` 只有 `testing-standard.md`；主 checkout 的更新标准未在此 branch 中。
- 影响：本任务以 AGENTS、testing-standard、现有代码和 roadmap 为权威，不阻塞实现。
- 后续：如需同步 standards，应单独通过 dev/main rebase 或 harness 标准同步任务处理。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 插件贡献建模 | 新增 `ExtensionContribution` / `ExtensionContributionType` | 轻量、稳定、metadata-only，适合第三方声明 provider-style 能力 | 为每种 provider 立即加 registry | accepted |
| Capability 扩展 | 增加 memory/compact/context/sandbox/runner/ui capability | 让 manifest 能声明这些包级能力，并与 contribution type 做一致性校验 | 只在 contribution type 中表达，不扩展 capability | accepted |
| Activation 语义 | provider-style contribution 默认 inactive / requires host binding | 避免插件启用后自动触发危险外部资源 | 启用插件即 active | accepted |
| Validator | warning-first，不因 metadata-only 缺少 runtime registry 失败 | 保障兼容；用 warning 提醒作者补元数据/权限 | 将所有 provider metadata 缺失设为 error | accepted |
| docs | 新增 Plugin Contribution Contract 页面 | docs-site 必须讲清真实 API 与边界 | 只改 roadmap 几行说明 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| SandboxProvider 真实 registry 如何绑定 | 当前只做 manifest metadata；后续由 sandbox routing/provider 任务确定 | coordinator | P3 sandbox tool routing 前 |
| CLI Command Plugin 是否需要独立 registry | 当前用 `CLI_COMMAND` contribution type + command capability；后续 CLI UX 稳定后再扩展 | coordinator | CLI command plugin task 前 |
| Plugin install/market/signing 是否进入 AI4J | 当前不进入本任务；未来按 CLI packaging/extension install 任务评估 | coordinator | one-command install / extension install task 前 |
