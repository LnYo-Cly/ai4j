# regression baseline live split - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### F-001 - 真实 provider 测试不能作为默认本地基线

- 背景：本轮目标是把本地必跑回归与 live-provider / 凭证依赖回归拆开。
- 发现：测试树中存在读取 `System.getenv`、`SystemPropsUtil.get` 和 JUnit `Assume` 的 provider/usage 测试；典型范围包括 `ai4j` provider tests、`ai4j-agent` workflow/team usage tests、`ai4j-coding` provider-backed workspace usage tests。
- 影响：不能把真实 provider 行为默认为 closeout 必跑证据；必须映射为 LV-001/LV-002 opt-in gate，并把 profile/category 规范化作为残余。
- 后续：后续任务应引入 env-only live test profile/category，清理任何 embedded/default credential-like value，并确保缺凭证时 clean skip。

### F-002 - Java CI 与 webapp CI 覆盖不对称

- 背景：Cadence Ledger 需要反映真实 CI 能力，而不是理想状态。
- 发现：`.github/workflows/java-regression.yml` 已在 PR 上运行 Java 8 package smoke 和 6 模块 test matrix；`docs-site` 有 build workflow；`ai4j-flowgram-webapp-demo` 只有 package scripts，当前未发现 dedicated CI workflow。
- 影响：RG-001..RG-007 可以记录为 PR wired pending first run；RG-009 仍是 local mapped gate，并需要 CI 残余。
- 后续：如要把 RG-009 提升为 CI gate，新增 workflow 后同步本 SSoT 与 Cadence。

### F-003 - v2 harness 回归总账是当前提交事实源

- 背景：仓库 `.gitignore` 忽略 legacy `docs/`，但 tracked harness v2 文件位于 `coding-agent-harness/governance/regression/`。
- 发现：`docs/` 目录仍被 `.gitignore` 忽略；本轮只对 `docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`、`docs/11-REFERENCE/testing-standard.md` 做显式 `git add -f`，已跟踪 v2 事实源仍在 `coding-agent-harness/governance/regression/`。
- 影响：本轮提交边界以 tracked v2 harness 文件为核心，并同步三份 legacy projection；dashboard/status 的事实源仍是 `coding-agent-harness/`。
- 后续：如果团队要恢复 numbered `docs/` 为 tracked SSoT，需要单独处理 `.gitignore` 和迁移策略。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| D-001 | local-required / live-provider-opt-in / credential-release-opt-in 三层 | 能把默认可重复验证和真实凭证行为分开，避免 closeout 隐式依赖本机密钥或 provider 可用性。 | 继续单表混合 RG gate。 | accepted |
| D-002 | 不在本轮改测试代码 | 本轮是治理/SSoT 切片；测试 profile/category 和 credential hygiene 需要独立任务验证。 | 顺手修改 surefire/profile。 | accepted |
| D-003 | `-am` 作为 Maven module gate 默认入口 | clean checkout/CI 中需要同时构建依赖模块，更接近现有 Java workflow。 | 保留无 `-am` 的本地命令。 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否把 legacy `docs/` 重新纳入 Git 跟踪 | 本轮不扩大到 docs tracking 策略；核心提交使用 tracked v2 harness。 | project coordinator | 后续 docs governance 任务 |
