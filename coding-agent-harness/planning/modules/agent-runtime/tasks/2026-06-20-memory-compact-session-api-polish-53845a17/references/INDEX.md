# 任务参考资料索引

| ID | Type | Path | Summary | Used By |
| --- | --- | --- | --- | --- |
| REF-001 | repo-guidance | TARGET:AGENTS.md | Java 8、Harness 任务目录、Regression SSoT / Cadence Ledger 更新和 worktree 规则。 | coordinator / reviewer |
| REF-002 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentSession.java | 新增 session compact API 的宿主类。 | coordinator / reviewer |
| REF-003 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/compact | `CompactPolicy`、`CompactResult`、`StructuredSummaryCompactPolicy` 现有契约。 | coordinator / reviewer |
| REF-004 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/context | `ContextBudget`、`ContextReport`、`DefaultContextProjector` 现有契约。 | coordinator / reviewer |
| REF-005 | docs | TARGET:docs-site/docs/agent/memory-compact-context.md | docs-site 新 API 示例和解释。 | coordinator / reviewer |
| REF-006 | docs | TARGET:docs-site/docs/agent/session-runtime.md | Session runtime 文档同步。 | coordinator / reviewer |

## 使用规则

- reviewer 先读 `task_plan.md`、`review.md` 和 REF-002..REF-006。
- 不需要外部资料；本任务不使用用户提供的 provider token。
