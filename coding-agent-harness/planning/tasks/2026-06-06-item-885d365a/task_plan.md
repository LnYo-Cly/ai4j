# 首聊可复制代码合同

Task Contract: harness-task/v1
Task Package Index: required

## 目标

docs-site 和 `ai4j-app-builder` 的首聊示例由本地 JUnit/docs 构建门禁保护，用户复制的第一段 Java/Spring Boot 代码不再只靠人工阅读校对。

## 范围

- 做什么：为普通 Java 首聊对象链和 Spring Boot starter 注入链路增加确定性 smoke tests；同步 docs-site 和 `ai4j-app-builder` 示例说明；更新 Feature/Regression/Cadence 记录。
- 不做什么：不调用真实 provider，不要求 API Key，不扩写 RAG/MCP/Agent/FlowGram 示例，不改变 SDK 公共 API。
- 主要风险：文档代码片段和测试代码可能继续分叉；通过在文档中指明受保护的回归命令来降低后续漂移。

## 预算选择

选择预算：standard

选择理由：涉及两个 Java 模块、docs-site、用户侧 skill 和回归台账，超出 simple 文档修补，但不需要 complex artifact pack 或外部资料摄取。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/Configuration.java | 确认普通 Java 默认 OkHttpClient 行为 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/platform/openai/chat/OpenAiChatService.java | 确认首聊请求链和响应读取路径 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/AiConfigAutoConfiguration.java | 确认 starter 如何配置 `AiService` 和 OkHttpClient | coordinator / reviewer |
| C-004 | public-doc | TARGET:docs-site/docs/start-here/five-minute-first-chat.md | 用户首聊入口文档 | coordinator / reviewer |
| C-005 | public-doc | TARGET:skills/ai4j-app-builder/references/recipes.md | agent 辅助用户接入时会引用的 recipe | coordinator / reviewer |

## 步骤

1. 增加 `ai4j` 普通 Java 首聊本地 smoke test。
2. 增加 `ai4j-spring-boot-starter` starter 首聊注入 smoke test。
3. 同步 docs-site 和 `ai4j-app-builder` recipe，说明首聊示例对应的本地验证命令。
4. 更新 Feature SSoT、Regression SSoT、Cadence Ledger 和任务材料。
5. 运行 targeted regression 并提交 review packet。

## 验收标准

- [ ] `mvn -pl ai4j -Dtest=<new-test>,ConfigurationTest -DskipTests=false test` 通过。
- [ ] `mvn -pl ai4j-spring-boot-starter -Dtest=<new-test> -DskipTests=false test` 通过。
- [ ] `docs-site` 的 `npm run typecheck` 和 `npm run build` 通过。
- [ ] `docs/05-TEST-QA/Regression-SSoT.md` 与 `docs/05-TEST-QA/Cadence-Ledger.md` 记录本次证据。
- [ ] `npx --yes coding-agent-harness status --json .` 通过。

## 工作树（Worktree）

- 路径：same checkout
- 分支：current branch
- Worker owner：coordinator
- Worker handoff commit required：no
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：范围集中、无并行 worker、当前工作区干净。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：需要真实 provider/API Key 或公共 API 重构时停止。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：`review.md`
- Reviewer：self + human confirmation
- No-finding 要求：无 open material finding。

## 关联

- 相关 Regression Gate：RG-001、RG-005、RG-008
- 审查报告：`coding-agent-harness/planning/tasks/2026-06-06-item-885d365a/review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`2026-06-06-5-c6e2fa16`

## 模块关联（启用模块并行时填写）

- Module：core-sdk、spring-starter、docs-site
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle closeout
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`
