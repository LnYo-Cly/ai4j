# 收口记录：轻量 ChatClient 首聊门面

## 摘要

本任务把 Plain Java 第一条 chat 请求从完整对象链样板降到 `ChatClient.openAi(...).chat(...)`，同时保留底层
`Configuration`、`AiService`、`IChatService` 和原始 `ChatCompletionResponse` 访问能力。docs-site、root README、
docs-site README 与 `ai4j-app-builder` recipe 已同步为短路径优先、完整对象链进阶。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j` core SDK；`docs-site` start-here 文档；`skills/ai4j-app-builder`；harness task / regression docs |
| 新增文件 | `ai4j/src/main/java/io/github/lnyocly/ai4j/service/ChatClient.java`; `ai4j/src/test/java/io/github/lnyocly/ai4j/service/ChatClientTest.java` |
| 删除文件 | 无 |
| 不在范围内 | 不改变 `AiService` / `IChatService` 合同；不扩展所有 provider facade；不运行 live provider；不处理前置任务的人审确认 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| ChatClient targeted test | `mvn -pl ai4j -Dtest=ChatClientTest -DskipTests=false test` | pass, 5 tests | `progress.md` |
| First-chat targeted regression | `mvn -pl ai4j "-Dtest=ChatClientTest,FirstChatCopyableCodeTest,ConfigurationTest" -DskipTests=false test` | pass, 10 tests | `progress.md` |
| RG-001 core SDK | `mvn -pl ai4j -am -DskipTests=false test` | pass, 108 tests | `progress.md` |
| RG-007 package | `mvn -DskipTests package` | pass, 9 reactor modules | `progress.md` |
| RG-008 docs-site typecheck | `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck` | pass, exit 0 | `progress.md` |
| RG-008 docs-site build | `NODE_OPTIONS=--max-old-space-size=8192 npm run build` | pass, generated `docs-site/build` | `progress.md` |
| Diff hygiene | `git diff --check` | no whitespace errors; Windows LF/CRLF warnings only | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self-review | 0 | 可提交人工确认；live provider 作为 opt-in residual 保留 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 真实 provider key、quota、网络、模型质量未验证 | user / release operator | yes | provider 协议或 release 任务需要时执行 LV-001 |
| `ChatClient` 当前只覆盖 OpenAI-compatible 首聊 facade | coordinator | yes | 多 provider facade 如需扩展，单独设计 |
| 前置首聊合同任务仍待人审确认 | human | yes | 独立执行 `2026-06-06-item-885d365a` review confirmation |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是 |
| 经验候选详情文件 | `lesson_candidates.md` |
| 结论 | checked-none: chatclient-first-chat-facade-local-api-no-new-governance-lesson |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 回归 SSoT | `docs/05-TEST-QA/Regression-SSoT.md` |
| Cadence Ledger | `docs/05-TEST-QA/Cadence-Ledger.md` |
