# Findings - AI4J Agent SDK architecture enhancement planning

## 规划发现

| ID | Severity | Finding | Evidence | Required Action | Status |
| --- | --- | --- | --- | --- | --- |
| F-001 | P2 | 不应新增 `AgentHost` / `Host Kernel` / `ai4j-runtime` 主概念；应收敛到现有 `ai4j-agent`。 | 当前已有 `ai4j-agent` Maven 模块和 Agent/Runtime/Session/Memory/Tool/Event/Workflow/Team/Trace 基础。 | 后续任务以增强 `ai4j-agent` 为主，避免过度拆分。 | open-follow-up |
| F-002 | P2 | `AgentSession` 应从薄包装升级为长程 Agent 任务运行态容器。 | 当前规划记录和现有 `AgentSession` 结构。 | 后续开 P0 Session/Memory/Compact 设计实现任务。 | open-follow-up |
| F-003 | P2 | Memory / Compact / Context 需要参考成熟 agent 设计并结构化。 | 讨论结论：SessionEventLog、MemoryStore、ContextProjector、CompactPolicy 分层。 | 后续开 memory/compact schema 和接口任务。 | open-follow-up |
| F-004 | P2 | Sandbox 应作为 `AgentSession` 运行环境绑定和插件扩展点，不应只是普通 tool。 | 讨论结论：无 sandbox 本地执行，有 sandbox 执行型工具自动进沙箱。 | 后续开 SandboxProvider SPI 设计任务。 | open-follow-up |
| F-005 | P3 | Agent Blueprint 可作为差异化功能，但必须分阶段落地。 | YAML Blueprint 规划。 | P1 先做单 Agent schema/loader/validator。 | open-follow-up |

## 残余问题

- Remote Agent Runner 是否作为独立 Maven 模块，待 P0/P1 完成后再定。
- Sandbox provider 示例是否官方提供，待插件生态稳定后再定。
- docs-site 如何呈现该路线，需单独文档任务处理。
