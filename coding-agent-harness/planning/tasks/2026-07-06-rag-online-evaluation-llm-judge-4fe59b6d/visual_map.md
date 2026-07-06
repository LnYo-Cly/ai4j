# Visual Map / 可视化图谱

Visual Map Contract: v1.0

本文件是任务图表集合，不只是阶段路线图。只有对人或 agent 理解任务有实际帮助的图才放进来。

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示执行阶段和依赖关系 | yes | `task_plan.md` | no |
| MAP-02 | sequence | 展示 online judge 不在 `search(...)` 内自动执行 | yes | `RagOnlineEvaluator.java` | no |

## 阶段关系图（Phase Graph）

```mermaid
flowchart LR
  INIT01["INIT-01 范围与上下文
kind=init"] --> EXEC01["EXEC-01 实现切片
kind=execution"]
  EXEC01 --> GATE01["GATE-01 验证与收口
kind=gate"]
```

## 阶段表（Phase Table，表头供 checker 解析）

| Phase ID | Kind | Depends On | State | Completion | Output | Required Evidence | Exit Command | Actor | Evidence Status | Blocking Risk | Owner / Handoff |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| INIT-01 | init | none | done | 100 | 任务边界已清楚到可以执行 | `task_plan.md` | `harness task-start 2026-07-06-rag-online-evaluation-llm-judge-4fe59b6d` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | Online judge API、trace 字段、AiService 入口、测试和 docs-site 已完成 | diff、`progress.md` commands | `harness task-phase 2026-07-06-rag-online-evaluation-llm-judge-4fe59b6d EXEC-01 --state done --completion 100 --evidence present` | agent | present | none | coordinator |
| GATE-01 | gate | EXEC-01 | done | 100 | PR 合并后完成任务 closeout | progress update、walkthrough、PR merge evidence | `harness task-complete 2026-07-06-rag-online-evaluation-llm-judge-4fe59b6d --message "<summary>" .` | agent | present | waiting PR/CI | coordinator |

允许的 `State`：`planned`, `in_progress`, `review`, `blocked`, `done`, `skipped`。

允许的 `Evidence Status`：`missing`, `partial`, `present`, `waived`。

允许的 `Kind`：`init`, `execution`, `gate`。

允许的 `Actor`：`agent`, `human`, `coordinator`。

`Completion` 使用 `0..100` 的整数；`done` 应为 `100`，`planned` 应为 `0`，`skipped` 不计入 dashboard 总完成度。dashboard 的实现完成度只由非 skipped 的 `execution` 阶段计算；`init` 和 `gate` 阶段表达生命周期门禁、下一步命令和责任人，不拉低实现完成度。

## 支持性图表（Supporting Maps）

### MAP-02：RAG online judge 调用时序

```mermaid
sequenceDiagram
  participant App as App code
  participant Rag as RagService
  participant Chat as Chat/Responses answer generation
  participant Eval as RagOnlineEvaluator
  participant Judge as RagJudge

  App->>Rag: search(RagQuery)
  Rag-->>App: RagResult(query, hits, context, trace)
  App->>Chat: generate answer with rag.context
  Chat-->>App: answer
  App->>Eval: evaluate(ragResult, answer)
  Eval->>Judge: judge(RagJudgeRequest)
  Judge-->>Eval: RagJudgeEvaluation
  Eval-->>App: evaluation and trace.judgeEvaluation
```
