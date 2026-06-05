# ai4j sdk project skill for agent-assisted development

## Task ID

`2026-06-05-ai4j-sdk-project-skill-for-agent-assisted-develo-6ba2130a`

## 创建日期

2026-06-05

## 一句话结果

新增一个可分发的 `ai4j-sdk` 项目 Skill，让安装到 agent 工具里的 AI 能按本仓库模块边界、harness 流程和验证规则协助新手开发。

## 完成后能得到什么

项目获得 `skills/ai4j-sdk/` 目录，包含 `SKILL.md`、`agents/openai.yaml` 和两份按需加载的参考文档。后续用户把这个 Skill 安装到支持 Skills 的 agent 工具后，可以让 agent 先识别 AI4J 的模块归属、选择正确的 Maven 或 docs-site 验证命令、遵守 Java 8 与密钥边界，并用更适合新手的方式解释开发步骤。

## 交付物

- 可见产物：`skills/ai4j-sdk/` 项目 Skill 包。
- 修改位置：`skills/ai4j-sdk/SKILL.md`、`skills/ai4j-sdk/agents/openai.yaml`、`skills/ai4j-sdk/references/*.md`。
- 验证证据：`python C:\Users\1\.codex\skills\.system\skill-creator\scripts\quick_validate.py skills\ai4j-sdk` 通过；模板残留扫描通过；实现提交 `3b8af61`。

## 第一眼应该看什么

1. `skills/ai4j-sdk/SKILL.md`
2. `skills/ai4j-sdk/agents/openai.yaml`
3. `skills/ai4j-sdk/references/repo-map.md`
4. `skills/ai4j-sdk/references/development-workflow.md`
5. `review.md` 中的 Evidence Checked 表

## 边界

- 范围内：创建项目 Skill 包、OpenAI Skill UI 元数据、AI4J 仓库模块图谱、开发与验证流程参考。
- 范围外：不改 Java SDK 代码、不改 docs-site 页面、不发布远程、不写额外 README 或安装指南到 Skill 包内。
- 停止条件：如果 Skill 分发规范或安装命令需要绑定具体平台市场，应回到用户确认后再新增公开文档或发布流程。

## 完成判断

- `skills/ai4j-sdk/SKILL.md` 有合法 frontmatter，触发描述覆盖 AI4J SDK 开发、文档、测试和审查场景。
- `agents/openai.yaml` 存在，并且 `default_prompt` 正确包含 `$ai4j-sdk`。
- `references/` 中的 repo map 和 workflow 能帮助 agent 选择模块、遵守 harness、运行最小验证。
- `quick_validate.py` 对 `skills/ai4j-sdk` 返回通过。
- 产物已在本地 git 提交，且任务材料进入 review 阶段等待人工确认。

## 执行合同

- Owner：coordinator
- 生命周期状态：审查中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据已记录到 `progress.md`，任务等待人工 review confirm

## 当前下一步

由用户审查 `skills/ai4j-sdk/` 产物；若认可，可执行 harness review confirmation 与后续 closeout。
