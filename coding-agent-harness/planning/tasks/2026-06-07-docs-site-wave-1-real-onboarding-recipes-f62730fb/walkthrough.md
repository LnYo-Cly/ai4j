# 收口记录：docs site wave 1 real onboarding recipes

## 摘要

本任务完成了 docs-site Wave 1 真实接入路径重写：新增 OpenAI-compatible/TroveBox recipe，强化 Java / Spring Boot quickstart、多 profile 配置、service registry 说明，并通过 docs-site build。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | docs-site canonical docs |
| 新增文件 | `docs-site/docs/start-here/openai-compatible-and-trovebox.md` |
| 删除文件 | 无 |
| 不在范围内 | Java API、中文 i18n 全量同步、远程推送 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| docs build | `npm run build` in `docs-site` | pass | `progress.md` |
| rejected facade scan | `rg -n "ChatClient\.openAi\|Ai4j\.chat\(" docs-site/docs README.md` | pass；仅“不推荐/不作为主入口”语境 | `progress.md` |
| sidebar reachability | 新页面加入 `docs-site/sidebars.ts` | pass | `sidebars.ts` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self-review | 0 | ready for human confirmation | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 中文 i18n 未同步 | coordinator | yes | 后续单独任务 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | yes，接受 no-candidate |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 新增 recipe | `docs-site/docs/start-here/openai-compatible-and-trovebox.md` |
| 任务计划 | `task_plan.md` |
| 发现记录 | `findings.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
