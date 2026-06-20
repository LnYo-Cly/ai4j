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

## 2026-06-20 规划刷新新增发现

| ID | Severity | Finding | Evidence | Required Action | Status |
| --- | --- | --- | --- | --- | --- |
| F-006 | P2 | AI4J 的差异化不应是“大而全”，而应是低接入成本、Agent 组装体验、插件生态、CLI/TUI 和远端 agent 产品化能力。 | `references/ai4j-agent-sdk-complete-planning-refresh.md` 第 3 节 | docs-site 和后续 roadmap 文档避免夸张“大厂/企业采用”口吻，聚焦开发者可用性。 | open-follow-up |
| F-007 | P2 | Harness 不宜完整内化进 `ai4j-cli`，否则会显著增加 CLI 维护成本。 | `references/ai4j-agent-sdk-complete-planning-refresh.md` 第 9.2 节 | CLI 只做 Harness 检测、任务状态展示和 dashboard/task packet 桥接。 | open-follow-up |
| F-008 | P1 | Sandbox 必须区分本地 permission sandbox 和真实远端 sandbox，不能设计成普通 shell tool。 | `references/ai4j-agent-sdk-complete-planning-refresh.md` 第 7 节 | P2 Sandbox SPI 任务中提供 fake provider 和 session binding tests。 | open-follow-up |
| F-009 | P2 | 第三方插件生态需要覆盖 Tool/Command/Prompt/Skill/Guardrail/Hook/Memory/Compact/SandboxProvider，而不只是工具注册。 | `references/ai4j-agent-sdk-complete-planning-refresh.md` 第 4 节 | P0-C 先实现 optional lifecycle hooks，不强迫老插件升级。 | open-follow-up |

## 2026-06-20 执行级路线图新增发现

| ID | Severity | Finding | Evidence | Required Action | Status |
| --- | --- | --- | --- | --- | --- |
| F-010 | P1 | 任何对标 Pi 插件/TUI 的实现必须先做公开资料或本仓实测调研，不能凭印象复刻。 | `references/ai4j-agent-sdk-execution-roadmap-and-research-gates.md` 第 4 节 | 后续开 R0-PI research digest，再进入 CLI/TUI 或插件 API 设计。 | open-follow-up |
| F-011 | P2 | P1-B/P1-C 已合并，当前实际下一步应启动 P2 Sandbox SPI，而不是继续新增总规划。 | P1-B PR #109 merge commit `908e410f946563dd204caad2cb3bcb0430edfd96`; P1-C PR #110 merge commit `384edd11424884e308c047f7e2a4b20997e95e49` | 新建 P2-A Sandbox SPI model task，先实现 fake provider 与 Java 8 SPI 合同。 | open-follow-up |
| F-012 | P2 | Approval / Permission Policy 是 sandbox 前置缝隙；本地 permission sandbox 和真实远端 sandbox 需要在 P0-D 先分清。 | `references/ai4j-agent-sdk-execution-roadmap-and-research-gates.md` 第 5、7 节 | 新建 P0-D planning/implementation task。 | open-follow-up |
| F-013 | P2 | docs-site 需要按“每个能力能不能用起来”重构，不只是 roadmap 标记。 | `references/ai4j-agent-sdk-execution-roadmap-and-research-gates.md` 第 11 节 | 每个能力页补：问题、最小示例、API/YAML、关系、限制、FAQ、下一步。 | open-follow-up |
| F-014 | P3 | 远端 Agent Runner 是产品化差异点，但必须满足 P0-P4 门禁后再决定是否新增模块。 | `references/ai4j-agent-sdk-execution-roadmap-and-research-gates.md` 第 10 节 | P5 只做协议合同和决策任务，不作为 P0/P1 阻塞项。 | open-follow-up |

## 2026-06-20 最终集成规划新增发现

| ID | Severity | Finding | Evidence | Required Action | Status |
| --- | --- | --- | --- | --- | --- |
| F-015 | P1 | 当前不应继续扩散总规划；真实下一步是 P2-A Sandbox SPI model。 | `references/ai4j-agent-sdk-integrated-implementation-plan-2026-06-20.md` 第 14 节；PR #110 已合并 | 创建 P2-A worktree/branch/task package，先做 Sandbox SPI model + fake provider tests。 | open-follow-up |
| F-016 | P2 | P2/P3/P4/P5 依赖关系必须保持顺序，否则会把 Sandbox、Coding tools、CLI 和 Runner 过早耦合。 | `references/ai4j-agent-sdk-integrated-implementation-plan-2026-06-20.md` 第 5-8 节 | Sandbox 先 fake provider + SPI，再 coding routing，再 CLI UX，最后 Runner 决策。 | open-follow-up |
| F-017 | P2 | CLI/TUI 增强应继续 Java + JLine + renderer abstraction，不应为了视觉效果过早引入 Ink/React 双栈。 | `references/ai4j-agent-sdk-integrated-implementation-plan-2026-06-20.md` 第 7 节 | P4 任务先做布局、slash command、reply rendering 和 Harness bridge 的 Java 侧抽象。 | open-follow-up |
