# docs site agent sdk real api completeness pass - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### F-001：Agent 文档缺少一张“能力 -> 真实 API -> 状态”的总索引

- 背景：用户明确要求 docs-site 必须把每个特色功能讲清楚，并且不能继续使用不存在的 API 示例。
- 发现：`docs-site/docs/agent/**` 已有 session、compact、blueprint、sandbox、runner、plugin 等专题页，但缺少一页把这些能力和真实源码入口汇总起来。源码中当前已存在 `AgentSession`、`SessionCompactPlan`、`AgentBlueprintLoader`、`ExtensionContribution`、`SandboxProvider`、`AgentRunnerProvider`、`SlashCommandController` 等真实入口。
- 影响：新增 `docs-site/docs/agent/real-api-matrix.md` 作为 Agent 章节的第二入口，优先帮助用户确认“能不能用”和“该读哪篇”。
- 后续：后续新增 Agent 能力时，应同步更新该矩阵。

### F-002：`reference-core-classes.md` 的 AgentSession 描述已过期

- 背景：早期 `AgentSession` 可以简化理解为“同一 runtime 下换一份 memory”。
- 发现：当前 `AgentSession` 源码已经包含 metadata、event log、snapshot/restore、store、compact result、sandbox binding 等职责。
- 影响：如果核心类参考仍只写“它只是切换 memory”，会误导开发者低估 session runtime 的使用价值。
- 后续：已在本任务修正文档，详细用法继续指向 `session-runtime` 和新能力矩阵。

### F-003：新增 docs-site 页面被 `.gitignore` 忽略

- 背景：仓库 `.gitignore` 存在 `docs/` 规则，会匹配 `docs-site/docs/**` 下的新文件。
- 发现：`git check-ignore -v docs-site/docs/agent/real-api-matrix.md` 命中 `.gitignore:76:docs/`。
- 影响：提交时必须使用 `git add -f docs-site/docs/agent/real-api-matrix.md`，否则新页面会漏进 PR。
- 后续：提交阶段强制确认 `git status --short --untracked-files=all` 和 `git show --name-status`。

### F-004：最新 `origin/dev` 缺少部分 AGENTS.md 仍引用的 numbered reference 文件

- 背景：AGENTS.md reading matrix 提到 `docs/11-REFERENCE/engineering-standard.md` 和 `execution-workflow-standard.md`。
- 发现：当前 `origin/dev` worktree 仅存在 `docs/11-REFERENCE/testing-standard.md`。
- 影响：本 docs-site 切片可继续依赖 AGENTS.md、testing standard、module plan 和源码证据；reference 缺失本身不属于本任务修复范围。
- 后续：如需修复 reading matrix drift，应另开 harness/reference repair 任务。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 文档切片形态 | 新增真实 API 能力矩阵，而不是重写所有 Agent 文档 | 当前页面多但分散，矩阵能最快降低小白用户判断成本，并作为后续维护清单 | 全量重构 Agent 章节，风险大且不适合本切片 | accepted |
| 能力状态分类 | 使用“可直接使用 / SPI 合同已存在 / Host CLI 绑定中 / 规划中” | 能避免把 provider-style SPI 写成完整产品能力 | 只写已实现/未实现二值状态，表达不足 | accepted |
| 新页面入口 | 放在 Agent overview 后、why-agent 前 | 用户进入 Agent 章节后先看到真实能力边界 | 放到 Reference 末尾，发现成本高 | accepted |
| 任务范围 | docs-site only，不改 Java API | 本任务是文档完整性切片，真实 API 已在 dev 存在 | 顺手补 API 或测试，超出 docs-site 模块边界 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要补 `docs/11-REFERENCE` 缺失文件 | 不在本任务范围；记录为 reference drift | coordinator | 后续 harness/reference repair 任务 |
| 是否要把能力矩阵扩展到 Core SDK 全站 | 不在本任务范围；本轮只做 Agent SDK | coordinator | 后续 docs-site completeness pass |
