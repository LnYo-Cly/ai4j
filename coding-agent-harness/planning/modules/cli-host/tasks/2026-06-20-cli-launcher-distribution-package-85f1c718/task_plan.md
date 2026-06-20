# CLI launcher distribution package

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/cli-host/tasks/2026-06-20-cli-launcher-distribution-package-85f1c718/artifacts/preset/2026-06-20T11-05-50-268Z
Task Package Index: required

## 目标

让 `ai4j-cli` 从“只能直接运行 fat jar”升级为“源码 package 可产出面向终端用户的 distribution 包”。发行包必须提供稳定 `ai4j` 命令入口、保留 Java 8 兼容、不写入任何真实 provider key，并让 docs-site 如实说明当前可用安装/运行路径。

## 范围

- 做什么：新增 Maven dist assembly、Unix/Windows launcher、示例配置、包内 README、distribution layout test、docs-site install/quickstart 更新、Regression SSoT / Cadence Ledger 证据。
- 不做什么：不改 CLI runtime 命令分发；不把 provider/model 写死进 launcher；不做真实 provider 测试；不实现 GitHub Release 上传、checksum 或系统级 installer；不使用用户提供的 token。
- 主要风险：根 `.gitignore` 的 `dist/` 规则会误忽略 `ai4j-cli/src/main/dist`；Maven filtering 必须使用 `@project.version@`；PowerShell/Windows launcher smoke 不能依赖真实安装路径。

## 预算选择

选择预算：complex

选择理由：本任务跨 Maven build、发行包布局、脚本、测试、docs-site 和回归治理；虽然不改 Java runtime，但属于发布/安装链路，必须有包结构和 launcher smoke 证据。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | repo-guidance | TARGET:AGENTS.md | Monorepo 和 Harness 流程约束 | coordinator / reviewer |
| C-002 | reference | TARGET:docs/11-REFERENCE/engineering-standard.md | Java 8、模块边界、发布边界 | coordinator / reviewer |
| C-003 | reference | TARGET:docs/11-REFERENCE/testing-standard.md | touched-surface 回归标准 | coordinator / reviewer |
| C-004 | regression | TARGET:docs/05-TEST-QA/Regression-SSoT.md | RG-004 / RG-007 / RG-008 更新位置 | coordinator |
| C-005 | cadence | TARGET:docs/05-TEST-QA/Cadence-Ledger.md | SRB-062 证据记录位置 | coordinator |
| C-006 | code | TARGET:ai4j-cli/pom.xml | fat jar 和 dist assembly 配置 | worker / reviewer |
| C-007 | docs | TARGET:docs-site/docs/coding-agent/install-and-release.md | 用户可见安装/发行边界 | reviewer |

## 步骤

1. 创建并启动 Harness module task。
2. 在现有 `maven-assembly-plugin` fat jar 基础上新增 filtered dist resources 和 `dist.xml` assembly。
3. 新增 `src/main/dist/bin/ai4j`、`ai4j.cmd`，只负责定位 Java / fat jar / 转发参数。
4. 新增 `providers.example.json`、`workspace.example.json` 和包内 README，全部只用 placeholder/env var，不放真实 key。
5. 新增 `CliDistributionLayoutTest`，覆盖源布局、示例配置 parse、secret-pattern 和 descriptor 结构。
6. 运行 targeted test、package smoke、archive inspection、launcher help smoke。
7. 更新 docs-site quickstart/install-and-release、Regression SSoT、Cadence Ledger。
8. 运行 docs-site build、diff check、Harness status。
9. 提交、推送、创建 PR；CI 通过后合并。

## 验收标准

- [x] `mvn -pl ai4j-cli -am "-Dtest=CliDistributionLayoutTest" -DskipTests=false -DfailIfNoTests=false test` 通过。
- [x] `mvn -pl ai4j-cli -am -DskipTests package` 生成 `ai4j-cli-2.3.0-dist.zip` 和 `.tar.gz`。
- [x] 归档包含必需路径并且 launcher 版本过滤成功。
- [x] `ai4j.cmd --help` smoke 能通过 `AI4J_CLI_JAR` override 启动。
- [x] 文档明确在线 install 脚本、dist 包、fat jar 的区别。
- [ ] `npm --prefix docs-site run build` 通过。
- [ ] `git diff --check` 和 `npx --yes coding-agent-harness status --json .` 通过。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\feature\cli-launcher-distribution`
- 分支：`feature/cli-launcher-distribution`
- Worker owner：coordinator
- Worker handoff commit required：是，本任务改代码/文档，需 commit-backed PR。
- Coordinator integration branch：`dev`

## 长程任务判定

- 是否属于长程任务：否，本切片是 CLI distribution 的 bounded implementation。
- 若是，合同文件：不适用
- 连续执行权限：用户已授权继续拆解并实现；本任务仍按单 PR 收口。
- Stop Condition 摘要：若需真实 provider、发布凭证、GitHub Release 自动化或系统 PATH installer 重构，另开任务。

## 审查判定

- 是否需要对抗性审查：是，发布/安装链路需要 self review；PR 可继续走 CI + human review。
- 若是，报告文件：`review.md`
- Reviewer：self
- No-finding 要求：不得存在未解释的 secret 泄漏、launcher hardcode 或 docs/API 漂移。

## 关联

- 相关 Regression Gate：RG-004、RG-007、RG-008
- 审查报告：`review.md`
- Generated Ledger：由 Harness CLI 同步
- 前置任务：CLI memory / sandbox / provider profile 命令已在 `origin/dev` 基线中

## 模块关联（启用模块并行时填写）

- Module：cli-host
- Step：CLI-DIST-01
- Module Plan：`coding-agent-harness/planning/modules/cli-host/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-pr
- Registry update needed：已由 `new-task` / `task-start` 同步；review/complete 时再刷新。
- Harness Ledger update needed：已由 lifecycle CLI 同步。
- Closeout / Regression update needed：Regression SSoT 和 Cadence Ledger 已更新，最终验证结果待补。

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | cli-host |

## Module Context Entry Points

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/cli-host/brief.md | Start here for the module purpose and current scope. |
| Module plan | coding-agent-harness/planning/modules/cli-host/module_plan.md | Use this for module steps, active task links, and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/cli-host/visual_map.md | Inspect when the change affects module sequencing or dependencies. |
