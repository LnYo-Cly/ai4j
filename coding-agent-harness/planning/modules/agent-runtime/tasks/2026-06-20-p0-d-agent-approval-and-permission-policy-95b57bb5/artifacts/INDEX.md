# 任务产物索引

仅在任务产生较多证据或大体量产物时使用，例如命令输出、截图、fixture、生成报告、review transcript、导出的数据文件等。核心任务文件只引用这里的 ID，不粘贴长输出。

| ID | Type | Path | Summary | Produced By |
| --- | --- | --- | --- | --- |
| ART-001 | report | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-d-agent-approval-and-permission-policy-95b57bb5/references/p0-d-agent-approval-permission-policy-plan.md | P0-D permission/approval policy design boundary, API shape, tests and residuals | coordinator |
| ART-002 | report | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-d-agent-approval-and-permission-policy-95b57bb5/visual_map.md | Phase graph, execution-chain architecture map and decision-state map | coordinator |

## 使用规则

- 路径必须可复查；临时终端输出应先保存为稳定文件再登记。
- 产物如果包含敏感信息，先脱敏或改为记录复查方式，不要提交原始敏感内容。
- 与 `review.md`、`progress.md`、walkthrough 互相引用时，使用 `ART-xxx` ID。
