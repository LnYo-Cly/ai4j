# ai4j sdk skill ab evaluation and docs install command

## Task ID

`2026-06-05-ai4j-sdk-skill-ab-evaluation-and-docs-install-co-1b4c2b80`

## 创建日期

2026-06-05

## 一句话结果

为 `ai4j-sdk` Skill 增加离线 A/B 评测证据，并在 `docs-site/README.md` 提供安装和调用命令。

## 完成后能得到什么

用户和后续 agent 可以直接查看 `artifacts/ab-evaluation.md`，判断 `ai4j-sdk` Skill 是否达到“降低新手使用 AI 协作开发成本”的目标；也可以在 `docs-site/README.md` 直接复制安装命令 `npx skills add LnYo-Cly/ai4j --skill ai4j-sdk` 和调用示例。

## 交付物

- 可见产物：A/B 评测报告、docs-site README 安装入口。
- 修改位置：`docs-site/README.md`、本任务 `artifacts/` 与任务材料。
- 验证证据：`npm run build`、`quick_validate.py skills/ai4j-sdk`、内容检索。

## 第一眼应该看什么

1. `docs-site/README.md` 的 `AI4J SDK Agent Skill` 小节。
2. `artifacts/ab-evaluation.md` 的结论、评分规则和 A/B 对照。
3. `review.md` 的 Evidence Checked 表。

## 边界

- 范围内：README 安装命令、离线 A/B rubric 评测、docs-site build 验证、Skill 结构复验。
- 范围外：不改 Skill 本体行为、不做真实线上用户实验、不推远程、不新增 docs-site 正文页面。
- 停止条件：如果安装命令需要绑定尚未发布的远程 tag 或 marketplace 流程，应先回到用户确认。

## 完成判断

- README 包含 `npx skills add LnYo-Cly/ai4j --skill ai4j-sdk`。
- README 包含 `$ai4j-sdk` 调用示例。
- A/B 评测报告说明方法、评分、结论和局限。
- `docs-site` 构建通过。
- Skill 本体 `quick_validate.py` 仍通过。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据记录到 `progress.md`，并提交 agent review

## 当前下一步

提交本轮文件改动，推进任务到 review，等待人工确认。
