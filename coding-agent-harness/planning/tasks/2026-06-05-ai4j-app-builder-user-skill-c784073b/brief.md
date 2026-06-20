# ai4j app builder user skill

## Task ID

`2026-06-05-ai4j-app-builder-user-skill-c784073b`

## 创建日期

2026-06-05

## 一句话结果

新增面向 AI4J 使用者的 `$ai4j-app-builder` Skill，并在 docs-site README 给出安装与调用入口。

## 完成后能得到什么

使用者可以通过 `npx skills add LnYo-Cly/ai4j --skill ai4j-app-builder` 安装一个面向应用开发的 AI4J Skill。该 Skill 会指导 agent 在普通 Java 或 Spring Boot 项目中选择最小 AI4J 依赖、配置 provider、生成第一条可运行 Chat/RAG/MCP/Agent/FlowGram 集成路径，并给出安全验证命令。它和已有 `$ai4j-sdk` 分工清晰：前者帮助用户开发自己的 AI4J 应用，后者帮助维护本仓库。

## 交付物

- 可见产物：`skills/ai4j-app-builder/`、`docs-site/README.md` 的 Skill 安装说明。
- 修改位置：`skills/ai4j-app-builder/SKILL.md`、`skills/ai4j-app-builder/references/*.md`、`skills/ai4j-app-builder/agents/openai.yaml`、`docs-site/README.md`。
- 验证证据：`quick_validate.py` 校验两个 Skill；占位符扫描；`docs-site` 执行 `npm run build` 成功。

## 第一眼应该看什么

1. `skills/ai4j-app-builder/SKILL.md`
2. `skills/ai4j-app-builder/references/app-paths.md`
3. `docs-site/README.md`
4. 本任务 `progress.md` 的验证记录

## 边界

- 范围内：新增用户侧 AI4J app-building Skill；补充 docs-site README 安装命令；验证 Skill 和文档站构建。
- 范围外：不改 AI4J Java runtime API；不新增 SDK 功能；不改线上发布流程；不推送远程。
- 停止条件：如果需要发布 Skill 到外部 registry、改 Maven 坐标或新增真实用户实验，需要用户另行确认。

## 完成判断

- `$ai4j-app-builder` 有完整 `SKILL.md`、OpenAI UI metadata 和按需 reference。
- Skill 明确区分“用 AI4J 开发应用”和“维护 AI4J SDK 仓库”。
- `docs-site/README.md` 包含 `$ai4j-app-builder` 安装命令和调用示例。
- Skill 基础校验通过，docs-site 构建通过。
- 实现已本地提交，任务材料进入 review 状态。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`、`walkthrough.md`、`lesson_candidates.md`
- 完成条件：验证证据记录到 `progress.md`，并通过 `task-review` 提交人工确认队列。

## 当前下一步

修复任务材料、运行 harness 状态检查，并提交 review。
