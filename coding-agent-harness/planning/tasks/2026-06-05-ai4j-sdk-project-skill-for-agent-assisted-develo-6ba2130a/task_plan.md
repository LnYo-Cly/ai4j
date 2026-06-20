# ai4j sdk project skill for agent-assisted development

Task Contract: harness-task/v1
Task Kind: standard-task
Task Preset: standard-task
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/tasks/2026-06-05-ai4j-sdk-project-skill-for-agent-assisted-develo-6ba2130a/artifacts/preset/2026-06-05T04-03-15-436Z
Task Package Index: required

## 目标

为 `ai4j-sdk` 新增一个可提交、可分发、可被 agent 工具安装的项目 Skill，帮助新手用户借助 AI 低成本参与 SDK 开发、文档和验证。

## 范围

- 做什么：新增 `skills/ai4j-sdk/` Skill 包，包含触发说明、OpenAI UI 元数据、模块归属图谱和开发验证流程。
- 不做什么：不修改 Java 业务代码、不修改 docs-site 信息架构、不发布远程、不在 Skill 包内添加 README 或安装指南类冗余文档。
- 主要风险：Skill 描述过宽会误触发；参考内容过长会浪费上下文；`openai.yaml` 的 `$ai4j-sdk` 在 PowerShell 中可能被变量展开。

## 预算选择

选择预算：standard

选择理由：该任务不是业务代码实现，但需要创建可复用 Skill 包、校验元数据、记录 harness 证据并提交 review，复杂度高于 simple。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:AGENTS.md | 获取本仓库模块边界、Java 8、harness、验证和 review 规则。 | coordinator / reviewer |
| C-002 | code | TARGET:pom.xml | 确认 Maven monorepo 模块列表和 Java 8 baseline。 | coordinator |
| C-003 | code | TARGET:skills-lock.json | 确认现有 skill lock 是本地安装记录，不作为项目分发包。 | coordinator |
| C-004 | tool-output | LOCAL:skill-creator/SKILL.md | 确认 Skill 结构、`openai.yaml`、禁止冗余 README、验证脚本要求。 | coordinator |

## 步骤

1. 诊断仓库结构和现有 skill 状态，确认 `skills/` 可作为可提交分发目录。
2. 使用 skill-creator 脚手架生成 `skills/ai4j-sdk` 基础目录。
3. 编写 `SKILL.md`、`agents/openai.yaml`、`references/repo-map.md` 和 `references/development-workflow.md`。
4. 运行 `quick_validate.py`、模板残留扫描和 git diff 检查。
5. 提交实现产物，并把验证证据写入 harness 任务。
6. 提交 agent review，等待人工确认。

## 验收标准

- [x] Skill 目录位于 `skills/ai4j-sdk/`，不是本机 `.agents/skills` 安装缓存。
- [x] `SKILL.md` frontmatter 合法，触发描述覆盖 AI4J SDK 相关开发场景。
- [x] `agents/openai.yaml` 存在，且 default prompt 正确引用 `$ai4j-sdk`。
- [x] 参考文档能指导模块选择、harness 任务、验证命令和新手解释方式。
- [x] `quick_validate.py skills/ai4j-sdk` 通过。

## 工作树（Worktree）

- 路径：当前 checkout
- 分支：main
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：main
- 未使用 worktree 的原因：本任务只新增独立 Skill 包和任务材料，没有并行文件冲突。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：如需发布到远程或新增公开安装文档，回到用户确认。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self + human review queue
- No-finding 要求：self-check 未发现 P0/P1/P2 问题；人工确认仍由用户完成。

## 关联

- 相关 Regression Gate：Skill syntax validation
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI 重建
- 前置任务：无

## 模块关联（启用模块并行时填写）

- Module：base / project-tooling
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：由 lifecycle CLI 已同步
- Closeout / Regression update needed：不新增固定回归面，无需更新 Regression SSoT

## Standard Task Preset

This task was created through the declarative `standard-task` preset.

| Field | Value |
| --- | --- |
| Preset Title | ai4j sdk project skill for agent-assisted development |
