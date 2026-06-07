# core sdk invocation contract audit

Task Contract: harness-task/v1
Task Package Index: required

## 目标

审计 AI4J Core SDK 当前真实调用合同，形成后续 API 升级前必须遵守的设计结论。

## 范围

- 做什么：读取 `Configuration`、`AiService`、`AiServiceRegistry`、`IChatService`、provider chat service、Tool/MCP、RAG、Memory、Spring starter 和 start-here 文档，输出 `design.md`。
- 不做什么：不新增 Java API、不恢复 lightweight `ChatClient`、不修改业务源码、不改 docs-site 正文、不推送远程。
- 主要风险：再次把“方便首聊”的想法误包装成错误的公共 API。

## 预算选择

选择预算：standard

选择理由：本任务是设计审计，不改业务代码，但需要记录发现、决策、验证和审查。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/Configuration.java | 当前 provider、HTTP、vector、MCP 配置聚合入口 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiService.java | 单实例服务工厂和能力入口 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiServiceRegistry.java | 多实例 provider/profile 路由入口 | coordinator / reviewer |
| C-004 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/IChatService.java | Chat service 最小合同 | coordinator / reviewer |
| C-005 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/platform/openai/chat/OpenAiChatService.java | Chat tool loop、stream、ToolUtil 接入证据 | coordinator / reviewer |
| C-006 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/tool/ToolUtil.java | Tool/MCP 统一调度中心 | coordinator / reviewer |
| C-007 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/rag/DefaultRagService.java | RAG 是独立服务合同，不是 Chat 内联链 | coordinator / reviewer |
| C-008 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/memory/ChatMemory.java | Memory 投影到 Chat/Responses 的边界 | coordinator / reviewer |
| C-009 | code | TARGET:ai4j-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/AiConfigAutoConfiguration.java | Spring Boot 注入入口 | coordinator / reviewer |

## 步骤

1. 读取 core/starter/doc 入口，确认真实调用链。
2. 识别现有能力已经合理的边界和不应新增的 facade。
3. 写出 `design.md`，给出后续升级建议和禁区。
4. 运行文本扫描、`git diff --check`、`harness status --json .`。
5. 更新 findings、progress、review、walkthrough 并提交审查。

## 验收标准

- [ ] `design.md` 存在并包含现状、判断、禁止项、可选升级方向。
- [ ] `findings.md` 记录源码事实和 accepted decisions。
- [ ] 对外路径不再推荐 removed `ChatClient`。
- [ ] `git diff --check` 通过。
- [ ] `harness status --json .` 通过或仅有本任务未提交 dirty warning。

## 工作树（Worktree）

- 路径：same checkout
- 分支：main
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：设计审计只写当前 task package，无并行代码实现。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：不适用
- Stop Condition 摘要：若需要新增公开 API，必须停在设计结论并等待用户确认。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：`review.md`
- Reviewer：self
- No-finding 要求：review.md 无重要发现。

## 关联

- 相关 Regression Gate：governance/design-only；不触发 Java executable gate
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：`5865b00 fix: remove lightweight chat client facade`

## 模块关联（启用模块并行时填写）

- Module：不适用
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：n/a
- Registry update needed：不适用
- Harness Ledger update needed：task package and review path
- Closeout / Regression update needed：walkthrough only; Regression SSoT 不适用
