# Visual Map / 可视化图谱

Visual Map Contract: v1.0

本文件是任务图表集合，不只是阶段路线图。只有对人或 agent 理解任务有实际帮助的图才放进来。

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示本规划任务生命周期 | yes | `task_plan.md` | no |
| MAP-02 | architecture | 展示 AI4J Agent SDK 后续分层 | yes | `references/agent-sdk-architecture-enhancement-plan.md` | no |
| MAP-03 | roadmap | 展示后续任务依赖顺序 | yes | `references/agent-sdk-architecture-enhancement-plan.md` | no |

## 阶段关系图（Phase Graph）

```mermaid
flowchart LR
  INIT01["INIT-01 范围与上下文\nkind=init"] --> EXEC01["EXEC-01 规划落盘\nkind=execution"]
  EXEC01 --> GATE01["GATE-01 Agent 提交审查\nkind=gate"]
  GATE01 --> GATE02["GATE-02 人工审查确认\nkind=gate"]
```

## 阶段表（Phase Table，表头供 checker 解析）

| Phase ID | Kind | Depends On | State | Completion | Output | Required Evidence | Exit Command | Actor | Evidence Status | Blocking Risk | Owner / Handoff |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| INIT-01 | init | none | done | 100 | 任务计划和执行策略已确认 | `task_plan.md`; `execution_strategy.md` | `harness task-start 2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | AI4J Agent SDK 架构增强规划已写入 task package | `references/agent-sdk-architecture-enhancement-plan.md`; `findings.md`; `progress.md` | `harness task-phase 2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81 EXEC-01 --state done --completion 100 --evidence present` | agent | present | final status not run yet | coordinator |
| GATE-01 | gate | EXEC-01 | done | 100 | Agent Review Submission | `review.md`; progress update; lesson routing | `harness task-review 2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81 --message "AI4J Agent SDK architecture enhancement roadmap ready for review"` | agent | present | must pass harness status first | coordinator |
| GATE-02 | gate | GATE-01 | planned | 0 | Human Review Confirmation | review packet 和人工确认 | `harness review-confirm 2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81 --confirm 2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81` | human | missing | Agent 不能代办人工确认 | human |

允许的 `State`：`planned`, `in_progress`, `review`, `blocked`, `done`, `skipped`。

允许的 `Evidence Status`：`missing`, `partial`, `present`, `waived`。

允许的 `Kind`：`init`, `execution`, `gate`。

允许的 `Actor`：`agent`, `human`, `coordinator`。

`Completion` 使用 `0..100` 的整数；`done` 应为 `100`，`planned` 应为 `0`，`skipped` 不计入 dashboard 总完成度。dashboard 的实现完成度只由非 skipped 的 `execution` 阶段计算；`init` 和 `gate` 阶段表达生命周期门禁、下一步命令和责任人，不拉低实现完成度。

## 支持性图表（Supporting Maps）

### MAP-02：架构分层

```mermaid
flowchart TB
  Core["ai4j\nProvider / Chat / Responses / RAG / MCP / Vector"]
  Agent["ai4j-agent\nRuntime / Session / Memory / Blueprint / Plugin Hooks / Sandbox SPI / Runner SPI"]
  Ext["ai4j-extension-api\nManifest / ServiceLoader / Resources / Enable-Expose Gate"]
  Coding["ai4j-coding\nWorkspace Tools / Shell / Patch / Sandbox Tool Routing"]
  Cli["ai4j-cli\nTUI / Slash Commands / Provider-Model Switch / Install UX"]
  Docs["docs-site\n真实 API 文档 / Cookbook / 插件教程"]

  Core --> Agent
  Ext --> Agent
  Agent --> Coding
  Coding --> Cli
  Agent --> Cli
  Agent --> Docs
  Coding --> Docs
  Cli --> Docs
```

### MAP-03：后续任务依赖

```mermaid
flowchart LR
  P2B["0. 完成 P2-B\nAgentSession sandbox binding"] --> Backlog["1. Backlog reconciliation\n核对 P0/P1/P2 状态"]
  Backlog --> Memory["2. Memory/Compact\nSession API polish"]
  Backlog --> Plugin["3. Plugin contribution\ncontract expansion"]
  Backlog --> Blueprint["4. Blueprint docs/schema\ncompatibility hardening"]
  P2B --> CodingSandbox["5. ai4j-coding\nsandbox tool routing"]
  CodingSandbox --> CliSandbox["6. CLI /sandbox /memory /compact"]
  CodingSandbox --> Runner["7. Remote Agent Runner SPI"]
  CliSandbox --> Install["8. One-command install\nprototype"]
  Memory --> Docs["9. docs-site completeness pass"]
  Plugin --> Docs
  Blueprint --> Docs
  Runner --> Docs
```
