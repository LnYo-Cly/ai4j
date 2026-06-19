# 任务参考资料索引

仅在任务需要外部资料、跨仓上下文、reviewer 输入包或生成参考材料时使用。不要把无关背景资料堆进来。

| ID | Type | Path | Summary | Used By |
| --- | --- | --- | --- | --- |
| REF-001 | repo-guidance | TARGET:AGENTS.md | repo-wide monorepo / Java 8 / Harness / regression rules | coordinator / reviewer |
| REF-002 | module-note | TARGET:AGENT.md | agent runtime architecture note | coordinator |
| REF-003 | standard | TARGET:docs/11-REFERENCE/engineering-standard.md | engineering and module boundary constraints | coordinator / reviewer |
| REF-004 | standard | TARGET:docs/11-REFERENCE/testing-standard.md | testing evidence and regression expectations | coordinator / reviewer |
| REF-005 | roadmap | TARGET:coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/references/ai4j-agent-sdk-execution-roadmap-and-research-gates.md | places P0-D in P0-P5 agent SDK roadmap | coordinator |
| REF-006 | docs | TARGET:docs-site/docs/agent/tools-and-registry.md | existing public explanation that ToolExecutor is the execution/permission boundary | coordinator |
| REF-007 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolExecutor.java | minimal execution contract | coordinator |
| REF-008 | design | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-d-agent-approval-and-permission-policy-95b57bb5/references/p0-d-agent-approval-permission-policy-plan.md | task-local design and sequencing record | coordinator / reviewer |

## 使用规则

- 每条参考资料都要说明用途，否则不要登记。
- 外部链接需要写清访问日期或版本线索，避免后续复查时语境漂移。
- reviewer 或 worker 只应读取与其 scope 相关的条目。
