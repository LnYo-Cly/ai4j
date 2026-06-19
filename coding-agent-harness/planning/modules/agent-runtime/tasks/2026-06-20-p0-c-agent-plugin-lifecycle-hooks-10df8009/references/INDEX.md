# 任务参考资料索引

仅在任务需要外部资料、跨仓上下文、reviewer 输入包或生成参考材料时使用。不要把无关背景资料堆进来。

| ID | Type | Path | Summary | Used By |
| --- | --- | --- | --- | --- |
| REF-001 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/Ai4jExtension.java | 第三方插件入口；P0-C 必须保持老插件兼容 | coordinator |
| REF-002 | code | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/runtime/ExtensionRuntimeState.java | 当前插件贡献物聚合点；lifecycle hooks 应纳入同一 snapshot 流程 | coordinator |
| REF-003 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java | Base/ReAct runtime 的 turn/model/tool 触发点 | coordinator |
| REF-004 | report | TARGET:coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/references/ai4j-agent-sdk-complete-planning-refresh.md | P0-P5 总规划；本任务承接 P0-C | coordinator / reviewer |
| REF-005 | report | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-c-agent-plugin-lifecycle-hooks-10df8009/references/p0-c-agent-plugin-lifecycle-hooks-plan.md | P0-C 可执行设计、取舍和验证计划 | coordinator / reviewer / future worker |

## 使用规则

- 每条参考资料都要说明用途，否则不要登记。
- 外部链接需要写清访问日期或版本线索，避免后续复查时语境漂移。
- reviewer 或 worker 只应读取与其 scope 相关的条目。
