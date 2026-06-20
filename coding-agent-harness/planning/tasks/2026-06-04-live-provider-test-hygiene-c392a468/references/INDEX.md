# 任务参考资料索引

| ID | Type | Path | Summary | Used By |
| --- | --- | --- | --- | --- |
| REF-001 | code | TARGET:pom.xml | 继承模块的 Surefire category/profile 配置入口。 | coordinator/reviewer |
| REF-002 | code | TARGET:ai4j/pom.xml | 核心模块不继承 root POM，需要独立 live-provider profile。 | coordinator/reviewer |
| REF-003 | code | TARGET:ai4j/src/test/java; TARGET:ai4j-agent/src/test/java; TARGET:ai4j-coding/src/test/java | live provider tests、fixture tests、credential handling 的实际来源。 | coordinator/reviewer |
| REF-004 | governance | TARGET:docs/11-REFERENCE/testing-standard.md; TARGET:coding-agent-harness/governance/regression | live-provider opt-in runbook 与回归 gate 单一事实源。 | coordinator/reviewer |

## 使用规则

- 本任务没有外部资料。
- reviewer 先读 `review.md`、`progress.md`、`artifacts/INDEX.md`，再按需追溯本索引路径。
