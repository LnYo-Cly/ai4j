# 任务参考资料索引

仅在任务需要外部资料、跨仓上下文、reviewer 输入包或生成参考材料时使用。不要把无关背景资料堆进来。

| ID | Type | Path | Summary | Used By |
| --- | --- | --- | --- | --- |
| REF-001 | code | TARGET:coding-agent-harness/governance/regression/Regression-SSoT.md | tracked v2 回归 gate 和残余事实源。 | coordinator / reviewer |
| REF-002 | code | TARGET:coding-agent-harness/governance/regression/Cadence-Ledger.md | tracked v2 cadence、触发表和批次日志。 | coordinator / reviewer |
| REF-003 | code | TARGET:.github/workflows/java-regression.yml | Java PR package smoke 和 module matrix 的真实执行入口。 | coordinator / reviewer |
| REF-004 | code | TARGET:docs-site/package.json | docs-site build/typecheck 脚本事实源。 | coordinator / reviewer |
| REF-005 | code | TARGET:ai4j-flowgram-webapp-demo/package.json | FlowGram webapp lint/type/build 脚本事实源。 | coordinator / reviewer |

## 使用规则

- 每条参考资料都要说明用途，否则不要登记。
- 外部链接需要写清访问日期或版本线索，避免后续复查时语境漂移。
- reviewer 或 worker 只应读取与其 scope 相关的条目。
