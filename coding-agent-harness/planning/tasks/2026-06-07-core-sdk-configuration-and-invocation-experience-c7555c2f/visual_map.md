# Visual Map / 可视化图谱

Visual Map Contract: v1.0

本文件是任务图表集合，不只是阶段路线图。只有对人或 agent 理解任务有实际帮助的图才放进来。

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示设计任务阶段和门禁 | yes | `task_plan.md` | no |
| MAP-02 | decision | 展示升级设计分层和拒绝项 | yes | `task_plan.md`; 前置审计 | no |

## 阶段关系图（Phase Graph）

```mermaid
flowchart LR
  INIT01["INIT-01 范围与上下文\nkind=init"] --> EXEC01["EXEC-01 实现切片\nkind=execution"]
  EXEC01 --> GATE01["GATE-01 Agent 提交审查\nkind=gate"]
  GATE01 --> GATE02["GATE-02 人工审查确认\nkind=gate"]
```

## 阶段表（Phase Table，表头供 checker 解析）

| Phase ID | Kind | Depends On | State | Completion | Output | Required Evidence | Exit Command | Actor | Evidence Status | Blocking Risk | Owner / Handoff |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| INIT-01 | init | none | done | 100 | 任务计划和执行策略已确认 | `task_plan.md`; `execution_strategy.md` | `harness task-start 2026-06-07-core-sdk-configuration-and-invocation-experience-c7555c2f` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | Core SDK 配置与调用体验升级设计 | `design.md`; `findings.md`; `progress.md` | `harness task-phase 2026-06-07-core-sdk-configuration-and-invocation-experience-c7555c2f EXEC-01 --state done --completion 100 --evidence present` | agent | present | API change requires separate approval | coordinator |
| GATE-01 | gate | EXEC-01 | planned | 0 | Agent Review Submission | `review.md`、progress update、lesson routing | `harness task-review 2026-06-07-core-sdk-configuration-and-invocation-experience-c7555c2f --message "<summary>"` | agent | missing | none | coordinator |
| GATE-02 | gate | GATE-01 | planned | 0 | Human Review Confirmation | review packet 和人工确认 | dashboard workbench confirmation | human | missing | Agent 不能代办人工确认 | human |

允许的 `State`：`planned`, `in_progress`, `review`, `blocked`, `done`, `skipped`。

允许的 `Evidence Status`：`missing`, `partial`, `present`, `waived`。

允许的 `Kind`：`init`, `execution`, `gate`。

允许的 `Actor`：`agent`, `human`, `coordinator`。

`Completion` 使用 `0..100` 的整数；`done` 应为 `100`，`planned` 应为 `0`，`skipped` 不计入 dashboard 总完成度。dashboard 的实现完成度只由非 skipped 的 `execution` 阶段计算；`init` 和 `gate` 阶段表达生命周期门禁、下一步命令和责任人，不拉低实现完成度。

## 支持性图表（Supporting Maps）

## MAP-02 - 升级分层

```mermaid
flowchart TD
  Goal["降低 Java 接入成本"] --> Keep["保留对象链主合同"]
  Keep --> Config["配置体验\nConfiguration helpers / AiConfig binding"]
  Keep --> Registry["多 provider/profile\nAiServiceRegistry 增强"]
  Keep --> Compatible["中转平台\nOpenAI-compatible baseUrl/profile"]
  Keep --> Recipes["组合范式\nChat + Tool/MCP + RAG + Memory recipes"]
  Goal --> Reject["拒绝隐藏式大门面"]
  Reject --> NoChatClient["不恢复 ChatClient.openAi"]
  Reject --> NoMegaChain["不新增 Ai4j.chat().rag().tools().call"]
  Config --> Later["后续实现任务"]
  Registry --> Later
  Compatible --> Later
  Recipes --> Later
```
