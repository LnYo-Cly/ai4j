# 任务参考资料索引

| ID | Type | Path | Summary | Used By |
| --- | --- | --- | --- | --- |
| REF-001 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionRegistry.java | 确认 discover / enable / expose / snapshot 合同 | coordinator |
| REF-002 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java | 确认 Agent builder 和 tool loop 接入点 | coordinator |
| REF-003 | code | TARGET:ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingAgentBuilder.java | 确认 Coding Agent builder 和 session tool routing 接入点 | coordinator |
| REF-004 | docs | TARGET:docs-site/docs/core-sdk/extension/overview.md | 保持插件包、provider extension、model/service extension、HTTP SPI 的边界一致 | coordinator |
| REF-005 | planning | TARGET:coding-agent-harness/planning/tasks/2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f/references/ai4j-extension-system-design.md | 复用前置插件生态规划结论 | coordinator |

## 使用规则

- 本任务没有外部 source pack。
- 外部网页调研不在本轮范围内；当前实现基于仓库内已完成的 Wave 1 / Wave 2 设计和代码。
