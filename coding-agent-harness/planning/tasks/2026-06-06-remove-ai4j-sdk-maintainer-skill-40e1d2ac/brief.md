# remove ai4j sdk maintainer skill

## Task ID

`2026-06-06-remove-ai4j-sdk-maintainer-skill-40e1d2ac`

## 创建日期

2026-06-06

## 一句话结果

删除公开的 `$ai4j-sdk` 维护者 Skill，只保留面向使用者的 `$ai4j-app-builder`。

## 完成后能得到什么

对外 Skill 入口收敛为一个清晰选择：用户安装 `$ai4j-app-builder` 来在自己的 Java / Spring Boot 项目中接入 AI4J；贡献者和维护者继续阅读仓库根目录 `AGENTS.md` 并按 `coding-agent-harness/` 执行任务、验证和审查流程。这样减少普通用户困惑，也避免 `$ai4j-sdk` 与 harness 形成重复维护面。

## 交付物

- 可见产物：`skills/` 下只保留 `ai4j-app-builder`。
- 修改位置：`docs-site/README.md`、`skills/ai4j-app-builder/SKILL.md`、删除 `skills/ai4j-sdk/**`。
- 验证证据：`quick_validate.py skills\ai4j-app-builder`、active Skill 扫描、`docs-site` 构建。

## 第一眼应该看什么

1. `docs-site/README.md` 的 `AI4J App Builder Skill` 小节。
2. `skills/ai4j-app-builder/SKILL.md` frontmatter。
3. `review.md` 的 Evidence Checked 表。

## 边界

- 范围内：删除公开 maintainer Skill；清理 README 安装入口；更新 app-builder 描述；验证 docs-site 和剩余 Skill。
- 范围外：不删除历史任务证据；不改 `AGENTS.md`、harness 标准或 Java 运行时代码；不推送远程。
- 停止条件：如果需要改远程发布标签、skill marketplace 或 GitHub release，需要另开任务确认。

## 完成判断

- `skills/ai4j-sdk/**` 已从当前工作树删除。
- `docs-site/README.md` 不再出现 `$ai4j-sdk` 或 `--skill ai4j-sdk`。
- `skills/ai4j-app-builder/SKILL.md` 不再把维护者路由到 `$ai4j-sdk`。
- 剩余 Skill 校验通过，docs-site build 通过。
- harness status 通过，任务进入 review 队列。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据记录到 `progress.md`，并提交 agent review。

## 当前下一步

提交任务材料修复并推进 review。
