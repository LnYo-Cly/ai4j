# AI4J Agent SDK architecture enhancement planning

## Task ID

`2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312`

## 创建日期

2026-06-20

## 一句话结果

形成一份可执行的 `ai4j-agent` 架构增强路线图，覆盖 Session、Memory、Compact、插件生命周期、Sandbox、远端 Agent Runner 与 Agent Blueprint。

## 完成后能得到什么

完成后，下一轮 agent 或开发者可以直接读取本任务包，明确 `ai4j-agent` 不再新增 `AgentHost` / `ai4j-runtime` 主概念，而是在现有 Maven 模块内增强通用 Agent SDK 能力；同时知道哪些能力属于 P0/P1/P2 后续实施任务，哪些只保留为远期产品化方向。本任务将作为后续代码任务、docs-site 任务和插件生态任务的架构输入。

## 交付物

- 可见产物：`references/ai4j-agent-sdk-enhancement-plan.md`
- 修改位置：`coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/`
- 验证证据：Harness task package 自检、`status --json`、任务审查材料

## 第一眼应该看什么

1. `references/ai4j-agent-sdk-enhancement-plan.md`
2. `task_plan.md`
3. `visual_map.md`
4. `review.md`

## 边界

- 范围内：记录架构规划、实施优先级、模块边界和后续任务拆分建议。
- 范围外：不改 Java 生产代码，不引入 Maven 模块，不改 docs-site，不实现 sandbox provider。
- 停止条件：如果规划需要转入具体代码实现，应另开 Harness 任务并重新确认范围。

## 完成判断

- [ ] 规划文件覆盖 Session / Memory / Compact / Plugin Lifecycle / Sandbox / Runner / Blueprint。
- [ ] 任务计划明确 P0-P5 实施顺序。
- [ ] Review 文件记录本轮规划的证据与残余风险。
- [ ] Harness 状态检查无 failure。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

补齐任务计划、审查材料和 Harness 验证记录。

## 2026-06-20 规划刷新

本任务已补充完整规划刷新稿：`references/ai4j-agent-sdk-complete-planning-refresh.md`。后续 agent 应优先阅读该文件，因为它在初版规划基础上补充了：

- AI4J 与 Spring AI / LangChain4j / AgentScope Java 的差异化定位。
- 插件生态的能力边界和第三方插件接入方向。
- `AgentSession` / Memory / Compact / ModelContext 的分层。
- YAML Agent Blueprint 的分阶段设计。
- 本地 permission sandbox 与真实远端 sandbox 的差异。
- 远端 Agent Runner 的产品化边界。
- CLI/TUI 体验、JLine 选择和 Harness 轻量桥接原则。
- 当前下一步应继续 P0-B Memory Compact Context Projector。

## 2026-06-20 执行级路线图补充

本任务新增执行级路线图：`references/ai4j-agent-sdk-execution-roadmap-and-research-gates.md`。后续 agent 应先读该文件，因为它把规划落到可执行队列：

- R0 调研门禁：Pi、Codex/Claude Code/OpenCode、Spring AI/LangChain4j/AgentScope Java、Sandbox provider。
- P0 内核：P0-A/P0-B/P0-C 状态、P0-D Approval / Permission Policy。
- P1 YAML Agent Blueprint。
- P2 Sandbox SPI。
- P3 `ai4j-coding` sandbox routing。
- P4 CLI/TUI 与 Harness 轻量桥接。
- P5 远端 Agent Runner 决策。
- docs-site 同步要求与“不要做的事”。

当前实际下一步：先收尾 `.worktrees/feature/agent-plugin-lifecycle-hooks` 的 P0-C worktree；不要重复开新的总规划任务。
