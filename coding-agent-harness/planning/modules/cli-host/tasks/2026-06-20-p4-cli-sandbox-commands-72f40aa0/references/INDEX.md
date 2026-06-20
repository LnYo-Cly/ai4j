# 任务参考资料索引

仅在任务需要外部资料、跨仓上下文、reviewer 输入包或生成参考材料时使用。不要把无关背景资料堆进来。

| ID | Type | Path | Summary | Used By |
| --- | --- | --- | --- | --- |
| REF-001 | repo-guidance | TARGET:AGENTS.md | monorepo/Harness 任务边界、Java 8 和 regression 更新规则。 | coordinator / reviewer |
| REF-002 | reference | TARGET:docs/11-REFERENCE/testing-standard.md | CLI/TUI 与 docs-site 触发的测试入口。 | coordinator / reviewer |
| REF-003 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java | slash dispatch、runtime rebind、status/help/palette 的主实现点。 | coordinator |
| REF-004 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java | root/action completion 和 command palette 的主实现点。 | coordinator |
| REF-005 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/factory/DefaultCodingCliAgentFactory.java | CLI 创建 `CodingAgentBuilder` 并接入 sandbox 的位置。 | coordinator |
| REF-006 | code | TARGET:ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingAgentBuilder.java | P3 `.sandbox(SandboxSession)` 与 `SandboxShellCommandExecutor` 已存在。 | coordinator |
| REF-007 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox | 当前 Sandbox SPI 边界：provider/create/session/execute，无通用 attach。 | coordinator |
| REF-008 | task-plan | TARGET:coding-agent-harness/planning/modules/cli-host/tasks/2026-06-20-p4-cli-sandbox-commands-72f40aa0/references/cli-sandbox-command-plan.md | P4 `/sandbox` 详细命令合同、实现接缝、测试矩阵和 out-of-scope。 | coordinator / reviewer |

## 使用规则

- 每条参考资料都要说明用途，否则不要登记。
- 外部链接需要写清访问日期或版本线索，避免后续复查时语境漂移。
- reviewer 或 worker 只应读取与其 scope 相关的条目。
