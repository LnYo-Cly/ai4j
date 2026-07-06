# References / 参考资料

## Required reads / 必读材料

| ID | Type | Path | Why it matters |
| --- | --- | --- | --- |
| R-001 | external-doc | G:\My_Project\java\ai4j-plugin-dynamic-workflow\README.md | plugin envelope、globals、host boundary 和 demo 语义 |
| R-002 | external-code | G:\My_Project\java\ai4j-plugin-dynamic-workflow\src\main\java\io\github\lnyocly\ai4j\plugin\dynamicworkflow\DynamicWorkflowExtension.java | tool / command / skill / prompt 的 contract |
| R-003 | external-code | G:\My_Project\java\ai4j-plugin-dynamic-workflow\src\main\java\io\github\lnyocly\ai4j\plugin\dynamicworkflow\DynamicWorkflowPayloads.java | envelope schema、version、truncate 规则 |
| R-004 | target-code | ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/AgentWorkflow.java | workflow 执行抽象入口 |
| R-005 | target-code | ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/SequentialWorkflow.java | 线性编排复用点 |
| R-006 | target-code | ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/StateGraphWorkflow.java | 图编排复用点 |
| R-007 | target-code | ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/WorkflowContext.java | workflow globals / state bag |
| R-008 | target-code | ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java | tool routing / sandbox bridge |
| R-009 | target-code | ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/CodeActRuntime.java | JS runtime 兼容性参照，仅在必要时回看 |
| R-010 | target-doc | docs/11-REFERENCE/engineering-standard.md | 需要 touch shared contract 时的工程约束 |
| R-011 | target-doc | docs/11-REFERENCE/testing-standard.md | 需要补测试 / smoke 时的验证规范 |

## Notes

- 这份索引先聚焦 host/runtime 和 plugin contract，不把 plugin 仓库的其他历史决策混进来。
- 如果后续证明 `ai4j-extension-api` 需要最小补丁，再把对应文件补进这里。
