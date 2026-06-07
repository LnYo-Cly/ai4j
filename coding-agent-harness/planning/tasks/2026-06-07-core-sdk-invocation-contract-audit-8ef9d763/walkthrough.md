# 收口记录：core sdk invocation contract audit

## 摘要

本任务完成了 Core SDK 调用合同审计。结论是：AI4J 当前应保留 `Configuration -> AiService -> IChatService -> ChatCompletion -> ChatCompletionResponse` 作为主线，不应恢复 lightweight `ChatClient`，也不应新增隐藏式 `Ai4j.chat()` 大门面。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | harness task package |
| 新增文件 | `design.md` |
| 删除文件 | 无 |
| 不在范围内 | Java API 实现、docs-site 正文、远程推送 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| source audit | 读取 core/starter/tool/rag/memory 入口 | pass | `progress.md`; `findings.md` |
| design package | `design.md` + task package 材料 | pass | `design.md`; `review.md` |
| public stale API scan | `rg -n "ChatClient\.openAi\|Ai4j\.chat\(" -S .` | pass；仅历史任务包和本审计结论中有引用 | `progress.md` |
| diff hygiene | `git diff --check` | pass；仅 LF/CRLF 提示 | `progress.md` |
| harness status | `npx.cmd --yes coding-agent-harness status --json .` | 预提交仅 dirty-state warning；提交后复核 | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self-review | 0 | ready for human confirmation | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 本任务只做设计审计，不新增公开 API | coordinator | yes | 后续 API 变更单独开任务 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | yes，接受 no-candidate |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 设计结论 | `design.md` |
| 任务计划 | `task_plan.md` |
| 发现记录 | `findings.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
