# Visual Map / 可视化图谱

Visual Map Contract: v1.0

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示执行阶段和依赖关系 | yes | `task_plan.md` | no |
| MAP-02 | architecture | 展示 extension tool 进入 Agent / Coding Agent 的路由 | yes | `ai4j-agent/**`; `ai4j-coding/**` | no |

## 阶段关系图（Phase Graph）

```mermaid
flowchart LR
  INIT01["INIT-01 范围与上下文\nkind=init"] --> EXEC01["EXEC-01 Runtime adapter + docs\nkind=execution"]
  EXEC01 --> VERIFY01["VERIFY-01 Targeted regression\nkind=execution"]
  VERIFY01 --> GATE01["GATE-01 Agent 提交审查\nkind=gate"]
  GATE01 --> GATE02["GATE-02 人工审查确认\nkind=gate"]
```

## 阶段表（Phase Table，表头供 checker 解析）

| Phase ID | Kind | Depends On | State | Completion | Output | Required Evidence | Exit Command | Actor | Evidence Status | Blocking Risk | Owner / Handoff |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| INIT-01 | init | none | done | 100 | 任务计划和执行策略已确认 | `task_plan.md`; `execution_strategy.md` | `harness task-start 2026-06-09-ai4j-extension-runtime-adapter-wave-3-e94c61c5` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | Agent / Coding Agent extension adapter、测试和 docs-site 插件包页 | diff; `progress.md` | `harness task-phase 2026-06-09-ai4j-extension-runtime-adapter-wave-3-e94c61c5 EXEC-01 --state done --completion 100 --evidence present` | agent | present | none | coordinator |
| VERIFY-01 | execution | EXEC-01 | done | 100 | Java targeted tests、package smoke、docs-site typecheck/build 已通过 | command evidence in `progress.md` | n/a | agent | present | none | coordinator |
| GATE-01 | gate | VERIFY-01 | done | 100 | Agent Review Submission | `review.md`、progress update、lesson routing | `harness task-review 2026-06-09-ai4j-extension-runtime-adapter-wave-3-e94c61c5 --message "<summary>"` | agent | present | pending lifecycle command | coordinator |
| GATE-02 | gate | GATE-01 | planned | 0 | Human Review Confirmation | review packet 和人工确认 | `harness review-confirm 2026-06-09-ai4j-extension-runtime-adapter-wave-3-e94c61c5 --confirm 2026-06-09-ai4j-extension-runtime-adapter-wave-3-e94c61c5` | human | missing | Agent 不能代办人工确认 | human |

允许的 `State`：`planned`, `in_progress`, `review`, `blocked`, `done`, `skipped`。

允许的 `Evidence Status`：`missing`, `partial`, `present`, `waived`。

允许的 `Kind`：`init`, `execution`, `gate`。

允许的 `Actor`：`agent`, `human`, `coordinator`。

## 支持性图表（Supporting Maps）

### MAP-02 Runtime Adapter

```mermaid
flowchart TB
  PluginJar["third-party plugin jar"] --> ServiceLoader["ServiceLoader Ai4jExtension"]
  ServiceLoader --> Registry["ExtensionRegistry"]
  Registry --> Enable["enable(extensionId)"]
  Enable --> Expose["exposeTool(toolName)"]
  Expose --> Snapshot["ExtensionRuntimeSnapshot"]
  Snapshot --> Adapter["ExtensionAgentTools"]
  Adapter --> AgentRegistry["AgentToolRegistry"]
  Adapter --> AgentExecutor["ToolExecutor"]
  AgentRegistry --> AgentLoop["Agent loop"]
  AgentExecutor --> AgentLoop
  AgentRegistry --> CodingSession["Coding Agent session"]
  AgentExecutor --> CodingSession
```
