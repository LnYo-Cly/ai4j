# P2-B AgentSession sandbox binding

## Task ID

`2026-06-20-p2-b-agentsession-sandbox-binding-e8175553`

## 创建日期

2026-06-20

## 一句话结果

AgentSession now records non-sensitive sandbox binding summaries in snapshot/store/event log.

## 完成后能得到什么

本任务完成后，`ai4j-agent` 的 `AgentSession` 可以绑定一个 sandbox session 的非敏感摘要，并在 snapshot、restore、in-memory store copy 和 event log 中保持一致。后续 `ai4j-coding` 可以基于这个摘要把 shell/file/browser 等工具路由到 sandbox，`ai4j-cli` 可以展示当前 sandbox 状态并提供 `/sandbox` 体验，而不需要保存 provider config、token、secret label 或真实 sandbox provider 细节。

## 交付物

- 可见产物：`AgentSessionSandboxBinding`、`AgentSession` sandbox binding API、snapshot/store/restore 支持、sandbox event types、回归测试和 docs-site 更新。
- 修改位置：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/session/**`、`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/trace/AgentEventType.java`、`ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentSessionSandboxBindingTest.java`、`docs-site/docs/agent/sandbox-spi.md`、`docs-site/docs/agent/sdk-roadmap.md`、`docs/05-TEST-QA/**`。
- 验证证据：targeted `AgentSessionSandboxBindingTest`、broad `mvn -pl ai4j-agent -am -DskipTests=false test`、`npm --prefix docs-site run build`、`git diff --check`、Harness status。

## 第一眼应该看什么

1. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/session/AgentSessionSandboxBinding.java`
2. `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentSessionSandboxBindingTest.java`
3. `docs-site/docs/agent/sandbox-spi.md`
4. `progress.md` 中 targeted/broad/docs/Harness evidence
5. `review.md` 中 Agent Review Submission 与 residual risk

## 边界

- 范围内：session 级 sandbox binding 摘要、snapshot/store/restore、事件记录、敏感 label/config 过滤、docs-site 和回归治理更新。
- 范围外：不实现真实 sandbox provider；不让插件贡献 provider；不接 `ai4j-coding` file/shell/git/browser routing；不实现 CLI/TUI `/sandbox`；不保存 `SandboxSpec.config` 或任何 secrets/tokens。
- 停止条件：如果需要接入真实 sandbox、外部 provider、插件注册机制、CLI/TUI 交互或远端 runner，必须停止并另开 P2-C/P3/P4/P5 任务。

## 完成判断

- [x] `AgentSession` 能 bind/update/clear sandbox binding，并记录 `SANDBOX_BOUND`、`SANDBOX_UPDATED`、`SANDBOX_CLEARED` event。
- [x] `AgentSessionSnapshot` 和 `InMemoryAgentSessionStore` 能保存/复制/恢复 sandbox binding。
- [x] `AgentSessionSandboxBinding` 不保存 `SandboxSpec.config`，并过滤 secret/token/key/password/passwd/credential/cookie/authorization 等敏感 label key。
- [x] targeted 和 broad `ai4j-agent` 回归通过，docs-site build 通过。
- [x] docs-site 和 regression/cadence 记录 P2-B 边界与验证证据。

## 执行合同

- Owner：coordinator
- 生命周期状态：审查中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`，并进入 `ready-to-confirm` review queue。

## 当前下一步

修复本 brief 模板残留后，重新运行 `git diff --check` 与 `npx --yes coding-agent-harness status --json .`；若 Harness 通过，则提交材料修复并推送 PR。
