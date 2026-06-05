# 收口记录：docs-site 文档重构总任务

## 摘要

本轮已完成 docs-site 首批结构性重构：新增 canonical / legacy 文档地图，补齐版本、发布、安全、生产检查、迁移、排障和选型对比入口，重写 Core SDK、Spring Boot、Agent、Coding Agent、FlowGram、Solutions 总览页，并更新 Docusaurus include、sidebar、footer、FAQ、Glossary 和 Start Here 关键链接。

本文件是 closeout draft。任务仍需 Human Review Confirmation 后才能执行 `task-complete`。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | docs-site；task-local harness materials |
| 新增文件 | `documentation-map.md`、`reference/*`、`security/overview.md`、`operations/production-checklist.md`、`migration/overview.md`、`troubleshooting/overview.md`、`comparison/overview.md` |
| 删除文件 | 无 |
| 不在范围内 | Java 代码、legacy 目录删除、provider live behavior、远程推送 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| docs-site build | `cd docs-site && npm run build` | passed | `progress.md` |
| sidebar conflict repair | 为两个 Advanced category 增加唯一 key 后重跑 build | passed | `sidebars.ts`、build output |
| wording scan | `rg` 检查新增/改动范围的生硬措辞 | passed | `progress.md` |
| git boundary | `git diff --cached --check`、`git status --ignored=matching docs-site/build` | passed | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| Core/MCP read-only audit | MCP 路径分裂、ai-basics 仍是旧站强内容 | 顶层 `mcp/` 作为正式入口；旧页后续迁移 | `findings.md` |
| Agent/Coding/FlowGram audit | 重要页面未挂 sidebar，总览像源码解读 | sidebar 增补，主入口重写 | `review.md` |
| IA audit | 缺少版本、安全、迁移、排障、选型入口 | 新增独立页面和 footer/sidebar 入口 | `review.md` |
| self review | 无 P0/P1/P2 阻塞发现 | 等待人工确认 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| Legacy 深页强内容尚未全部迁移 | coordinator | yes | 后续 deep page merge / legacy notice wave |
| 新增辅助页仍可继续补真实错误案例和配置样例 | coordinator | yes | 后续按模块补充 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是，本轮暂不沉淀共享 lesson |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 发现记录 | `findings.md` |
| 本轮 docs commit | `251d364` |
