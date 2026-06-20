# Findings - P2-A Sandbox SPI model

| ID | Severity | Finding | Evidence | Required Action | Status |
| --- | --- | --- | --- | --- | --- |
| F-001 | P2 | Sandbox 必须保持 provider-neutral，不能在 P2-A 绑定 Docker/K8s/CubeSandbox/E2B 等具体实现。 | `io.github.lnyocly.ai4j.agent.sandbox` 只包含 SPI/DTO；fake provider 在 test 内联。 | 后续真实 provider 由插件或业务方实现。 | closed |
| F-002 | P2 | Sandbox 不应替代 Permission Policy。 | docs-site `agent/sandbox-spi.md` 第 5 节；P0-D policy 已存在。 | P3/P4 接入时仍先过 permission/approval gate。 | open-follow-up |
| F-003 | P2 | P2-A 不做 AgentSession binding，避免 snapshot/secret 边界混乱。 | 本任务没有修改 `agent.session`。 | P2-B 单独设计非敏感 binding 摘要和 event log。 | open-follow-up |
| F-004 | P2 | DTO 必须防御性拷贝，避免 provider/test 调用者篡改内部状态。 | `AgentSandboxSpiModelTest.shouldUseDefensiveCopies`。 | 保持 builder/copy/getter copy 约定。 | closed |
