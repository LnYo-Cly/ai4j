# 收口记录：首聊可复制代码合同

## 摘要

本任务把 docs-site 与 `ai4j-app-builder` 的首聊示例升级为可验证合同：普通 Java 首聊链路由 core SDK 本地测试保护，Spring Boot 首聊注入链路由 starter 本地测试保护，docs-site 与 Skill recipe 明确列出对应验证命令。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j`、`ai4j-spring-boot-starter`、`docs-site`、`skills/ai4j-app-builder`、回归治理文档 |
| 新增文件 | `ai4j/src/test/java/io/github/lnyocly/ai4j/docs/FirstChatCopyableCodeTest.java`、`ai4j-spring-boot-starter/src/test/java/io/github/lnyocly/ai4j/AiServiceFirstChatAutoConfigurationTest.java`、`docs/10-WALKTHROUGH/2026-06-06-first-chat-copyable-code-contract.md` |
| 删除文件 | 无 |
| 不在范围内 | 真实 provider live validation、公共 API 重构、RAG/MCP/Agent/FlowGram 示例扩写 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Core targeted | `mvn -pl ai4j "-Dtest=FirstChatCopyableCodeTest,ConfigurationTest" -DskipTests=false test` | pass, 5 tests | `progress.md` |
| RG-001 | `mvn -pl ai4j -am -DskipTests=false test` | pass, 103 tests | `progress.md` |
| Starter targeted | `mvn -pl ai4j-spring-boot-starter -Dtest=AiServiceFirstChatAutoConfigurationTest -DskipTests=false test` | pass, 1 test | `progress.md` |
| RG-005 | `mvn -pl ai4j-spring-boot-starter -am -DskipTests=false test` | pass, core 103 tests + starter 3 tests | `progress.md` |
| RG-008 | `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck` and `npm run build` in `docs-site/` | pass | `progress.md` |
| RG-007 | `mvn -DskipTests package` | pass, 9 reactor modules | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self review | none | ready for human confirmation | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 本任务不证明真实 provider 质量、额度、网络或模型可用性 | coordinator | yes | LV-001 仍作为 opt-in live gate |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | yes，接受 no-candidate |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 仓库级 walkthrough | `docs/10-WALKTHROUGH/2026-06-06-first-chat-copyable-code-contract.md` |

Closeout Status: closed
