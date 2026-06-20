# ai4j sdk skill ab evaluation and docs install command - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。阻塞性问题写入 `review.md`。

## 研究发现

### 安装命令坐标

- 背景：README 需要给出真实可复制的安装命令。
- 发现：`git remote -v` 显示 origin 为 `git@github.com:LnYo-Cly/ai4j.git`。
- 影响：README 使用 `npx skills add LnYo-Cly/ai4j --skill ai4j-sdk`。
- 后续：如远程仓库名或 owner 变化，需要同步 README。

### A/B 评测性质

- 背景：用户要求判断 Skill 是否达到预期效果。
- 发现：当前没有真实外部用户流量或多 agent 工具安装回放，因此本轮采用同任务集离线 rubric 评测。
- 影响：报告明确标注不是线上实验，并给出残余风险。
- 后续：远程发布后可补一轮真实安装和实际 agent session 评测。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| A/B 方式 | 离线 rubric 评测 | 可复查、低成本，适合评估 Skill 内容是否覆盖预期行为。 | 真实线上用户实验 | accepted |
| README 位置 | `docs-site/README.md` | 用户明确要求 docs-site/readme，且这是文档站维护入口。 | docs 正文页 | accepted |
| 验证命令 | docs-site build + Skill validation | 覆盖 README 改动和 Skill 本体完整性。 | 只做静态 grep | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否把安装说明扩展成 docs-site 正文页 | 本轮不纳入 | coordinator / user | 用户要求公开完整教程时 |
