# first wave project upgrades

Task Contract: harness-task/v1
Task Kind: standard-task
Task Preset: standard-task
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/tasks/2026-06-04-first-wave-project-upgrades-93da333c/artifacts/preset/2026-06-04T08-29-08-411Z
Task Package Index: required

## 目标

完成第一波低风险项目升级，把本机 release GPG 路径从 POM 中移除，把本地生成输出排除出 Git 边界，并形成可复查的 harness 任务包。

## 范围

- 做什么：清理 release POM 的 Windows 绝对 GPG 路径；添加 `gpg.executable` Maven 属性默认值；忽略本地 `output/` 生成目录；记录验证、review 和 walkthrough。
- 不做什么：不修改业务逻辑，不新增测试框架，不启用新的 harness capability，不推送远端，不替用户执行人工确认。
- 主要风险：release profile 的签名实测依赖本机或 CI 是否安装 GPG；本轮只验证 Maven 构建与路径清理，不证明真实发布签名链路。

## 预算选择

选择预算：complex

选择理由：用户同意的升级列表包含 release 配置、回归治理和 module-parallel 三个方向；本次先落地低风险切片，并保留剩余方向为后续任务，因此需要完整任务包、证据和 review 路由。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:pom.xml | 根 release profile 和子模块继承关系决定 `gpg.executable` 默认值放置位置。 | coordinator |
| C-002 | code | TARGET:ai4j*/pom.xml | 各发布模块的 GPG plugin 配置需要保持一致且不依赖本机路径。 | coordinator |
| C-003 | public-doc | TARGET:AGENTS.md | 仓库要求 Java 8、Maven monorepo、harness flow 和不污染 Git 边界。 | coordinator |
| C-004 | generated-evidence | TARGET:coding-agent-harness/planning/tasks/2026-06-04-first-wave-project-upgrades-93da333c/progress.md | 记录执行命令、验证结果与残余风险。 | coordinator, reviewer |

## 步骤

1. 诊断仓库升级点并确认第一波切片范围。
2. 创建 harness 任务并启动生命周期。
3. 修改 `.gitignore` 和 release POM，使本地输出与本机 GPG 路径不再污染项目。
4. 运行路径复查、Maven package 和 harness status 验证。
5. 提交代码变更与 task lifecycle 记录，补齐 review 材料。

## 验收标准

- [x] `rg` 复查不再出现 `D:\Develop\DevelopEnv\GnuPG` 本机路径。
- [x] release profile 仍能通过 `gpg.executable` 指定 GPG 可执行文件。
- [x] `output/` 被 `.gitignore` 忽略，现有本地生成图片不进入提交边界。
- [x] `mvn -DskipTests package` 通过。
- [x] `npx --yes coding-agent-harness status --json .` 通过且无失败无警告。

## 工作树（Worktree）

- 路径：same checkout
- 分支：`main`
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：本轮是低风险配置切片，写入文件少且没有并行 worker。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：需要人工确认、远端 push、发布签名实测或扩大到后续升级切片时停止。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self
- No-finding 要求：self review 无 P0/P1/P2 阻塞发现；人工确认另行等待用户。

## 关联

- 相关 Regression Gate：本轮使用 Maven package smoke，不新增固定 regression gate。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建。
- 前置任务：无

## 模块关联（启用模块并行时填写）

- Module：不适用
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：n/a
- Registry update needed：不适用
- Harness Ledger update needed：已由 lifecycle CLI 同步 generated Harness Ledger
- Closeout / Regression update needed：`walkthrough.md` 已记录；Regression SSoT 无新增 gate

## Standard Task Preset

This task was created through the declarative `standard-task` preset.

| Field | Value |
| --- | --- |
| Preset Title | first wave project upgrades |
