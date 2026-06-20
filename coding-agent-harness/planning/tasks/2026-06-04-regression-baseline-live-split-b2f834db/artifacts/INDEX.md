# 任务产物索引

仅在任务产生较多证据或大体量产物时使用，例如命令输出、截图、fixture、生成报告、review transcript、导出的数据文件等。核心任务文件只引用这里的 ID，不粘贴长输出。

| ID | Type | Path | Summary | Produced By |
| --- | --- | --- | --- | --- |
| ART-001 | diff | TARGET:coding-agent-harness/governance/regression/Regression-SSoT.md | 证明 SSoT 已拆分 local-required、live-provider-opt-in、credential-release-opt-in，并新增残余 R-006/R-007。 | coordinator |
| ART-002 | diff | TARGET:coding-agent-harness/governance/regression/Cadence-Ledger.md | 证明 Cadence 表已补必跑 gate、opt-in gate、节奏和最低证据深度。 | coordinator |
| ART-003 | command | TARGET:. | `npx --yes coding-agent-harness status --json .`，期望 status pass、0 failures、0 warnings。 | coordinator |

## 使用规则

- 路径必须可复查；临时终端输出应先保存为稳定文件再登记。
- 产物如果包含敏感信息，先脱敏或改为记录复查方式，不要提交原始敏感内容。
- 与 `review.md`、`progress.md`、walkthrough 互相引用时，使用 `ART-xxx` ID。
