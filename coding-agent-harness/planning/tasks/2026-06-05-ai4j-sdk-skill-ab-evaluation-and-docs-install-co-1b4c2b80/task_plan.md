# ai4j sdk skill ab evaluation and docs install command

Task Contract: harness-task/v1
Task Kind: standard-task
Task Preset: standard-task
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/tasks/2026-06-05-ai4j-sdk-skill-ab-evaluation-and-docs-install-co-1b4c2b80/artifacts/preset/2026-06-05T04-17-05-080Z
Task Package Index: required

## 目标

评估 `ai4j-sdk` Skill 是否达到预期效果，并在 docs-site README 中提供安装和调用命令。

## 范围

- 做什么：新增 A/B 评测报告，更新 `docs-site/README.md`，运行 docs-site 构建和 Skill 校验。
- 不做什么：不修改 Skill 行为本体、不做远程发布、不新增完整 docs-site 页面、不做真实用户流量实验。
- 主要风险：A/B 评测若不说明局限，容易被误解为线上实验；安装命令若不基于真实 remote，会变成不可执行占位。

## 预算选择

选择预算：standard

选择理由：该任务改动文档和评测材料，涉及 docs-site 构建、Skill 校验和 harness review，适合 standard。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:skills/ai4j-sdk/SKILL.md | 评估 Skill 是否覆盖目标场景。 | coordinator / reviewer |
| C-002 | code | TARGET:skills/ai4j-sdk/references/repo-map.md | 评估模块定位能力。 | coordinator / reviewer |
| C-003 | code | TARGET:skills/ai4j-sdk/references/development-workflow.md | 评估 harness、验证和新手协作流程。 | coordinator / reviewer |
| C-004 | code | TARGET:docs-site/README.md | 写入安装命令和调用示例。 | coordinator / reviewer |
| C-005 | command-output | TARGET:. | `git remote -v` 确认安装命令仓库坐标为 `LnYo-Cly/ai4j`。 | coordinator |

## 步骤

1. 启动 harness 任务并确认当前工作树。
2. 从 `git remote -v` 提取真实 GitHub 仓库坐标。
3. 在 `docs-site/README.md` 增加 Skill 安装命令和调用示例。
4. 创建 `artifacts/ab-evaluation.md`，用同一任务集和 rubric 比较无 Skill / 有 Skill 条件。
5. 运行 `npm run build`、`quick_validate.py` 和内容检索。
6. 提交改动并进入 agent review。

## 验收标准

- [x] README 包含安装命令 `npx skills add LnYo-Cly/ai4j --skill ai4j-sdk`。
- [x] README 包含 `$ai4j-sdk` 调用示例。
- [x] A/B 评测报告给出任务集、评分规则、分数、结论和局限。
- [x] `docs-site` 的 `npm run build` 通过。
- [x] `skills/ai4j-sdk` 的 `quick_validate.py` 通过。

## 工作树（Worktree）

- 路径：当前 checkout
- 分支：main
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：main
- 未使用 worktree 的原因：改动集中在 README 和当前任务目录，无并行冲突。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：如需发布远程、tag 或 marketplace 流程，先回到用户确认。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self + human review queue
- No-finding 要求：self-check 无 P0/P1/P2 阻塞项。

## 关联

- 相关 Regression Gate：docs-site build、Skill validation
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI 重建
- 前置任务：`2026-06-05-ai4j-sdk-project-skill-for-agent-assisted-develo-6ba2130a`

## 模块关联（启用模块并行时填写）

- Module：docs-site / project-tooling
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：由 lifecycle CLI 同步
- Closeout / Regression update needed：无新增固定回归面

## Standard Task Preset

This task was created through the declarative `standard-task` preset.

| Field | Value |
| --- | --- |
| Preset Title | ai4j sdk skill ab evaluation and docs install command |
