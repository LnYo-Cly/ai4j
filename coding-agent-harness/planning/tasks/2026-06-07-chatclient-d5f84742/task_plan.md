# 轻量 ChatClient 首聊门面

Task Contract: harness-task/v1
Task Package Index: required

## 目标

在 `ai4j` core SDK 中提供轻量 `ChatClient` 首聊门面，并让 docs-site / ai4j-app-builder 的 Plain Java 首聊入口优先展示该低成本路径。

## 范围

- 做什么：新增 `ChatClient` facade、覆盖本地 MockWebServer 单元测试、同步首聊文档与 skill recipe、更新 harness/SSoT/回归证据。
- 不做什么：不重命名或删除 `AiService` / `IChatService`，不改变 provider DTO，不把高级能力包装成第二套 SDK。
- 主要风险：过度包装会造成与完整对象链割裂；文档只展示短路径可能隐藏进阶扩展点；docs-site build 在 Windows 上可能受 Node heap 或文件锁影响。

## 预算选择

选择预算：standard

选择理由：变更跨 core SDK、docs-site 与 skill recipe，但 API 面很小，验证可通过本地单元测试与现有 docs/build gate 完成。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiService.java | 确认现有服务工厂和 provider 边界，避免 ChatClient 改变底层合同。 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/platform/openai/chat/entity | 确认请求/响应 DTO 字段，保证文本抽取不猜测。 | coordinator / reviewer |
| C-003 | public-doc | TARGET:docs-site/docs/start-here | 首聊入口需要优先展示新低成本路径。 | coordinator / reviewer |
| C-004 | public-doc | TARGET:skills/ai4j-app-builder/references/recipes.md | 用户 skill 的 Plain Java recipe 需要同步，避免 agent 继续生成旧对象链作为首选。 | coordinator / reviewer |
| C-005 | governance | TARGET:docs/05-TEST-QA/Regression-SSoT.md; TARGET:docs/05-TEST-QA/Cadence-Ledger.md | 判断并记录 RG-001/RG-007/RG-008 证据。 | coordinator / reviewer |

## 步骤

1. 更新 task package 与 Feature SSoT，固定方案 A 的实现范围和验收标准。
2. 在 `ai4j` core SDK 新增 `ChatClient`，提供 OpenAI 快速构造、简化 `chat`、底层对象访问和原始 response 方法。
3. 增加本地单元测试，覆盖 provider double 请求、鉴权 header、文本抽取、空 key / 空 prompt 参数校验。
4. 同步 docs-site 首聊页面、README/入口材料和 `ai4j-app-builder` recipe，使短路径为 Plain Java 首选，完整对象链为进阶路径。
5. 执行 RG-001/RG-007/RG-008，更新 Regression SSoT、Cadence Ledger、progress、review 和 walkthrough。

## 验收标准

- [x] `ChatClient.openAi(System.getenv("OPENAI_API_KEY")).chat("gpt-4o-mini", "...")` 是可复制的 Java 8 写法。
- [x] 本地测试证明 `ChatClient` 使用 OpenAI chat completions 路径、设置 Bearer key，并能从标准 response 中返回 assistant 文本。
- [x] 进阶路径未被移除：用户仍可拿到 `Configuration`、`AiService`、`IChatService` 或调用返回 `ChatCompletionResponse` 的方法。
- [x] docs-site 与 `ai4j-app-builder` 不再把完整对象链作为 Plain Java 首聊唯一入口。
- [x] RG-001、RG-007、RG-008 证据记录完成。

## 工作树（Worktree）

- 路径：same checkout
- 分支：main
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：改动集中在一个 core facade、少量文档和治理记录；无并行写入收益，且当前工作树干净。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：如果需要破坏既有对象链、引入新 provider 合同或升级 Java baseline，则暂停确认。

## 审查判定

- 是否需要对抗性审查：否，采用 self-check + harness review packet；人工确认仍由 GATE-02 处理。
- 若是，报告文件：`review.md`
- Reviewer：self + human
- No-finding 要求：无 open material finding；人工确认前不得 close。

## 关联

- 相关 Regression Gate：RG-001、RG-007、RG-008
- 审查报告：`coding-agent-harness/planning/tasks/2026-06-07-chatclient-d5f84742/review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`TASKS/2026-06-06-item-885d365a` 提供首聊可复制代码合同背景；其人审确认独立处理。

## 模块关联（启用模块并行时填写）

- Module：core-sdk、docs-site
- Step：不适用
- Module Plan：`coding-agent-harness/planning/modules/core-sdk/module_plan.md`; `coding-agent-harness/planning/modules/docs-site/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：core-sdk/docs-site touched; no module-plan status change expected
- Harness Ledger update needed：task lifecycle commands and governance rebuild after review
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`; `docs/05-TEST-QA/Cadence-Ledger.md`; walkthrough
