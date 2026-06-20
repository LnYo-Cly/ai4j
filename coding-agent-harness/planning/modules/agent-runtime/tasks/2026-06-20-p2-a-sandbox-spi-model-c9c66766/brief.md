# P2-A Sandbox SPI model

## Task ID

`MODULES/agent-runtime/2026-06-20-p2-a-sandbox-spi-model-c9c66766`

## 创建日期

2026-06-20

## 一句话结果

在 `ai4j-agent` 中新增 Java 8 兼容的 Sandbox SPI model，为后续真实远端 sandbox provider、AgentSession binding、`ai4j-coding` routing 和 CLI `/sandbox` 体验提供稳定合同。

## 完成后能得到什么

- `io.github.lnyocly.ai4j.agent.sandbox` 包含 provider/session/spec/command/result/artifact/event/status/exception 合同。
- 有 deterministic fake provider 测试证明合同可用、DTO 防御性拷贝、session close/cancel 语义有效。
- docs-site 有 `Agent Sandbox SPI` 技术页，并在 Agent SDK Roadmap / sidebar 可见。
- Regression SSoT / Cadence Ledger 记录 P2-A targeted、broad agent 和 docs build 证据。

## 边界

范围内：Sandbox SPI model、fake provider tests、docs-site 技术文档、回归记录、Harness 任务材料。

范围外：真实 Docker/K8s/CubeSandbox/E2B provider、AgentSession sandbox binding、extension plugin provider contribution、`ai4j-coding` shell/file/git/browser routing、CLI `/sandbox` command。

## 完成判断

- [x] Sandbox SPI 类型存在且 Java 8 编译通过。
- [x] `AgentSandboxSpiModelTest` 覆盖 fake provider/session/command/result/artifact/event/defensive-copy/close 行为。
- [x] `mvn -pl ai4j-agent -am "-Dtest=AgentSandboxSpiModelTest" -DskipTests=false -DfailIfNoTests=false test` 通过。
- [x] `mvn -pl ai4j-agent -am -DskipTests=false test` 通过。
- [x] `npm --prefix docs-site run build` 通过。
- [x] Regression SSoT / Cadence Ledger 已更新。

## 当前下一步

提交 PR、等待 CI、merge 后清理 `.wt/p2a`，然后进入 P2-B AgentSession sandbox binding 或 P2-C Sandbox plugin contribution。
