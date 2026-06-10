# AI4J Extension Check Gate - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### `plan` 是预览，不适合作为唯一 CI 门禁

- 背景：F-040 已把插件接入 recipe 写清楚，但第三方作者和使用者仍需要人工判断 `validate` 与 `plan` 输出。
- 发现：`ai4j-cli extension validate` 已对 authoring errors 返回非零；`extension plan` 会输出 activation state，但资源拼错时仍是预览命令，不适合作为 CI gate。
- 影响：新增 `check` 比改变 `plan` 退出码更稳，避免破坏已有“预览”语义。
- 后续：实现 `check`，复用 validator 与 activation plan。

### check 只断言显式请求的资源

- 背景：插件可贡献 tool、command、Skill、Prompt、Guardrail，但使用者不一定要全部启用。
- 发现：`ExtensionActivationPlan` 会列出所有贡献资源和请求缺失资源，未请求资源也可能 inactive。
- 影响：`check` 不能把“插件所有资源都 active”作为通过条件，否则会反向鼓励全启用。
- 后续：只检查命令行中 `--expose-tool` / `--allow-*` 指定的资源是否 active。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 新增 `extension check` | accepted | 保留 `plan` 预览语义，同时提供 CI 友好的非零退出门禁 | 给 `plan` 加 `--fail-on-inactive`，语义混杂 | accepted |
| `check` 要求 `--enable` | accepted | 接入前门禁必须验证真实启用后的 activation state，未启用不是有效 recipe | 允许只做 validate，容易与 `validate` 重复 | accepted |
| 只检查显式请求资源 | accepted | 支持最小权限 recipe，不强迫全资源启用 | 所有贡献资源 inactive 都 fail，会让插件使用者过度暴露能力 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要改变 extension API | 当前判断不需要 | coordinator | 实现中若无法复用现有 API 时 |
