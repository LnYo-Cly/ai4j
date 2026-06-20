# ai4j sdk project skill for agent-assisted development - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。阻塞性问题写入 `review.md`。

## 研究发现

### Skill 分发目录选择

- 背景：仓库已有 `.agents/skills/coding-agent-harness` 和 `skills-lock.json`，需要判断新增项目 Skill 应放在哪里。
- 发现：`.agents/` 与 `skills-lock.json` 是本地安装和锁文件路径，受 `.gitignore` 管理；根目录 `skills/` 未被忽略，适合作为项目可提交分发目录。
- 影响：本任务新增 `skills/ai4j-sdk/`，没有写入本机 agent 安装缓存。
- 后续：无。

### Skill Creator 结构要求

- 背景：用户明确要求使用 `$skill-creator`。
- 发现：Skill 包必须包含 `SKILL.md`；推荐 `agents/openai.yaml`；详细上下文应放入 `references/`；不应在 Skill 包内添加 README、INSTALLATION_GUIDE、QUICK_REFERENCE 等冗余文档。
- 影响：本任务只创建 4 个必要文件，并把仓库细节拆到两份 references。
- 后续：如需用户安装说明，应在 docs-site 或发布说明中另起任务处理。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Skill 包位置 | `skills/ai4j-sdk/` | 可提交、可分发，不污染本机安装缓存。 | `.agents/skills/ai4j-sdk` | accepted |
| 参考内容组织 | `SKILL.md` 精简，细节放 `references/` | 符合 progressive disclosure，减少 agent 上下文成本。 | 把所有规则塞进 `SKILL.md` | accepted |
| 校验方式 | `quick_validate.py` + 内容扫描 + diff check | 本任务不改运行时代码，Skill 结构校验和内容校验足够。 | Maven 或 docs-site build | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要在 docs-site 增加公开安装入口 | 本任务不纳入，作为后续增强 | coordinator / user | 用户要求公开文档或发布前 |
