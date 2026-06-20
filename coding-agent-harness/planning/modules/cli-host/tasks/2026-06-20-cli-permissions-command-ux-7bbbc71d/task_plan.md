# CLI permissions command UX

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/cli-host/tasks/2026-06-20-cli-permissions-command-ux-7bbbc71d/artifacts/preset/2026-06-20T14-10-40-533Z
Task Package Index: required

## 目标

新增 `ai4j-cli` 只读 `/permissions` 命令，展示当前 Coding Agent session 的 approval mode、tool permission 行为、ACP permission gateway 与 sandbox 边界，让用户不用翻启动参数或文档就能理解当前工具审批策略。

## 范围

- 做什么：`/permissions`、`/permissions status`；CLI/TUI dispatch；JLine completion/palette；ACP command；CodeCommand help；docs-site command reference / tools approvals 文档；targeted tests。
- 不做什么：不做权限编辑器；不在运行中切换 approval mode；不改 agent runtime permission policy；不改 coding tool 执行；不打印 raw tool input、prompt、secret、工具输出全文。
- 主要风险：把诊断命令误做成权限管理器；把 sandbox 当成 permission 替代；输出过多导致敏感信息泄露。

## 预算选择

选择预算：complex

选择理由：虽是小功能，但涉及 CLI/TUI/ACP/docs/regression parity，是固定 slash command surface。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | repo-guidance | TARGET:AGENTS.md | Monorepo、Harness、Java 8、任务位置约束 | coordinator / reviewer |
| C-002 | module-plan | TARGET:coding-agent-harness/planning/modules/cli-host/module_plan.md | CLI host 当前任务与边界 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java | slash root/completion/palette | worker |
| C-004 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java | interactive dispatch 和 TUI 输出 | worker |
| C-005 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/acp/AcpSlashCommandSupport.java | ACP command list / headless 输出 | worker |
| C-006 | docs | TARGET:docs-site/docs/coding-agent/command-reference.md | 用户命令参考 | worker / reviewer |
| C-007 | docs | TARGET:docs-site/docs/coding-agent/tools-and-approvals.md | approval 机制文档 | worker / reviewer |
| C-008 | task-reference | TARGET:coding-agent-harness/planning/modules/cli-host/tasks/2026-06-20-cli-permissions-command-ux-7bbbc71d/references/cli-permissions-command-ux-plan.md | 本任务设计与执行方案 | coordinator / worker / reviewer |

## 步骤

1. 从最新 `origin/dev` 创建 `.worktrees/feature/cli-permissions-command-ux`。
2. 创建 Harness module task 并启动。
3. 注册 `/permissions` root command 和 `status` 补全。
4. 在 CLI runtime 和 ACP 中渲染只读 permission summary。
5. 更新 top-level help、in-session help、docs-site。
6. 添加/更新 tests。
7. 运行 targeted tests、broad CLI tests、docs build、diff check、Harness status。
8. 更新 progress/review/walkthrough，提交、推送、创建 PR 到 `dev`。

## 验收标准

- [ ] `/permissions` 在 root completion、palette、help、runtime dispatch 中可用。
- [ ] `/permissions status` 是显式别名。
- [ ] `/permissions inspect` 等未知子命令返回明确错误。
- [ ] ACP `available_commands` 包含 `permissions`，执行返回 deterministic summary。
- [ ] 输出不包含 raw tool input、prompt、provider key、baseUrl credential 或工具输出全文。
- [ ] docs-site 写清 `/permissions` 与 approval mode、ACP、sandbox 的关系。
- [ ] RG-004 targeted/broad、RG-008 docs build、Harness status 通过。

## 工作树（Worktree）

- 路径：TARGET:.worktrees/feature/cli-permissions-command-ux
- 分支：feature/cli-permissions-command-ux
- Worker owner：coordinator
- Worker handoff commit required：yes
- Coordinator integration branch：dev

## 长程任务判定

- 是否属于长程任务：否；本切片 bounded。
- Stop Condition 摘要：如果需要做权限编辑器、动态切换 approval、或跨模块改 permission policy，则停止并另开任务。

## 审查判定

- 是否需要对抗性审查：是，self review。
- Reviewer：self + PR/CI。
- No-finding 要求：不得存在敏感信息泄露、权限语义误导或 CLI/ACP/docs 不一致。

## 关联

- 相关 Regression Gate：RG-004、RG-008。
- 审查报告：`review.md`
- Module：cli-host
- Module Plan：TARGET:coding-agent-harness/planning/modules/cli-host/module_plan.md
