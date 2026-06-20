# Agent Blueprint schema export and docs hardening - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### Blueprint 已有 loader/validator，但缺少 authoring schema

- 背景：P1-A/P1-B/P1-C 已提供 `AgentBlueprintLoader`、`AgentBlueprintValidator`、`AgentFactory` 和 `ai4j-cli run`，但用户写 YAML 时缺 IDE 提示和可导出的字段合同。
- 发现：`docs-site/docs/agent/agent-blueprint.md` 已描述字段；源码中没有 `agent-blueprint.schema.json` 或 schema accessor。
- 影响：最小增量是提供稳定 JSON Schema resource 和 CLI 导出，而不是引入运行期 JSON Schema validator。
- 后续：通过 JUnit 固定 schema resource、`$schema` 忽略行为和 CLI 导出。

### `$schema` 应为 authoring hint，不进入 runtime DTO

- 背景：用户通常希望 YAML 顶部写 `$schema` 获得 IDE 提示。
- 发现：现有 loader 会把未知 top-level field 记录为 warning；如果不把 `$schema` 加入允许列表，会让正确的 IDE hint 产生噪声。
- 影响：将 `$schema` 加入 loader known top-level fields，但不加入 `AgentBlueprint` DTO 和 runtime 行为。
- 后续：测试 `AgentBlueprintLoader` + `AgentBlueprintValidator` 对 `$schema` 不产生 unknown warning。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Schema 交付方式 | 内置 resource + Java accessor + CLI 导出 | 无需网络和 live provider，适合小白和 IDE authoring | 只写 docs 示例；远端托管 schema；引入 validator 依赖 | accepted |
| Runtime 行为 | `$schema` 只作为 loader 允许字段，不进入 DTO | 避免改变 Blueprint v1 runtime 语义 | 增加 DTO 字段；保留 unknown warning | accepted |
| CLI 命令 | `ai4j-cli blueprint schema [--out]` | 与 `run` 分离，清晰表达 authoring helpers | 放到 `run --schema` 或 `extension` 子命令 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否发布远端 schema URL | 本任务不发布，只在 schema `$id` 和 docs 中保留稳定 URL 语义 | coordinator | 后续 release/docs 部署任务 |
