# P5 Remote Agent Runner SPI contract - Brief

## 目标

在 `ai4j-agent` 中新增 Remote Agent Runner 的最小 Java 8 SPI 合同，让 AI4J 后续可以支持“Agent loop 本身运行在远端 sandbox / VM / container / hosted workspace”这一产品化方向。

## 范围

- 做什么：新增 `io.github.lnyocly.ai4j.agent.runner` 包，提供 provider/session/spec/request/result/event/status/exception/listener 合同；添加 fake runner deterministic tests；更新 docs-site 技术文档和路线图。
- 不做什么：不新增 Maven 模块；不接真实云 runner；不接真实 sandbox provider；不读取、保存或使用 provider token；不改变现有本地 Agent 执行路径；不实现 CLI attach/logs/create。
- 主要风险：Runner 容易过早产品化，本任务只做 contract-first + fake-testable，不承诺托管平台。

## 成功标准

- `AgentRunnerProvider` / `AgentRunnerSession` 能表达远端 runner 创建、run、stream、cancel、artifact、close。
- `AgentRunnerSpec` 能携带非敏感 profile/workspace/blueprint/sandboxSpec/labels/config。
- `AgentRunnerResult` 能携带 `AgentResult`、output、error、duration、artifact 和 events。
- fake runner 测试证明 contract 可用且 DTO defensive copy 稳定。
- docs-site 有独立 `Remote Agent Runner SPI` 页，并从 Agent roadmap/sidebar 可达。
