# P2-A Sandbox SPI model

Task Contract: harness-task/v1
Task Package Index: required

## 目标

为 `ai4j-agent` 增加最小、稳定、Java 8 兼容的 Sandbox SPI model，让后续真实 sandbox、coding tool routing、CLI `/sandbox` 和远端 Agent Runner 有统一合同。

## 范围

- 做什么：新增 `io.github.lnyocly.ai4j.agent.sandbox` SPI/DTO；新增 fake provider deterministic tests；更新 docs-site 和回归记录。
- 不做什么：不创建真实 VM/容器/浏览器环境；不把 Blueprint 自动绑定真实 sandbox；不实现 `ai4j-coding` routing；不做 CLI `/sandbox`。
- 主要风险：过早把 provider 细节写死；本任务通过 provider-neutral DTO 和 fake provider test 控制风险。

## 上下文包

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/permission | Permission Policy 是 sandbox 前置 gate | developer/reviewer |
| C-002 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/blueprint/AgentBlueprintSandbox.java | Blueprint 已有 sandbox declaration，但 P2-A 不自动创建 sandbox | developer/reviewer |
| C-003 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/session | P2-B 才会做 session binding，本任务只记录下一步边界 | developer/reviewer |
| C-004 | docs | TARGET:docs-site/docs/agent/approval-permission-policy.md | 说明 permission 与 sandbox 的边界 | docs reviewer |

## 步骤

1. 创建 worktree：`.wt/p2a`，分支 `feature/agent-sandbox-spi-model`。
2. 新增 Sandbox SPI model：`SandboxProvider`、`SandboxSession`、`SandboxSpec`、`SandboxCommand`、`SandboxResult`、`SandboxArtifact`、`SandboxEvent`、`SandboxEventType`、`SandboxStatus`、`SandboxException`。
3. 新增 `AgentSandboxSpiModelTest` fake provider tests。
4. 更新 docs-site：`agent/sandbox-spi`、Agent sidebar、Agent SDK roadmap。
5. 更新 `docs/05-TEST-QA/Regression-SSoT.md` 与 `docs/05-TEST-QA/Cadence-Ledger.md`。
6. 运行 targeted/broad/docs/Harness checks。
7. 提交、PR、CI、merge、清理 worktree。

## 验收标准

- [x] SPI 不依赖真实外部 sandbox provider。
- [x] DTO 有 builder/getter/copy 或 defensive-copy 行为。
- [x] fake provider tests 覆盖 supports/create/execute/cancel/close/artifact/event。
- [x] docs-site 讲清楚“permission policy 决定能不能执行，sandbox provider 决定在哪里执行”。
- [x] 回归记录覆盖 P2-A 证据。

## 工作树

- 路径：`.wt/p2a`
- 分支：`feature/agent-sandbox-spi-model`
- Worker owner：coordinator
- Coordinator integration branch：main

## 审查判定

- 是否需要对抗性审查：否；P2-A 是小型 SPI/model + deterministic tests。
- Reviewer：self + CI。
- No-finding 要求：无真实外部 provider、无 secret、无 Maven 模块新增、Java 8 兼容。

## 关联

- 相关 Regression Gate：RG-002、RG-008、SRB-055。
- 前置任务：P0-D Permission Policy、P1-A/P1-B/P1-C Blueprint。
- 后续任务：P2-B AgentSession sandbox binding、P2-C Sandbox plugin contribution、P3 coding sandbox routing。
