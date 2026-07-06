# AI4J dynamic workflow host runtime - Findings

## 研究发现

### 当前可复用的 host surface
- `ai4j-agent` 已经有 `AgentWorkflow`、`SequentialWorkflow`、`StateGraphWorkflow`、`WorkflowContext` 这些 workflow 基础件，可以直接承载 host-side 编排，而不必先发明一套新 runtime。
- `BaseAgentRuntime`、`ToolCallDecision`、`SandboxProvider`、`SandboxSession` 说明 host 侧已经有 tool routing 和 sandbox 边界，dynamic workflow 更像是把这些现成能力串成一条受控执行链。

### JS runtime 不是默认前提
- 代码库里确实有 `NashornCodeExecutor` / `CodeActRuntime` 的 ES5 路径，但那只是可选参照，不应该默认把 dynamic workflow 再包成一个“必须先有 JS 引擎”的任务。
- 这次更适合先把 envelope 映射为 Java-native workflow 编排层；只有当 envelope 的 script/global 语义无法稳定表达时，再考虑受控脚本适配层。

### 插件 contract 已经给出边界
- 外部插件的 README / payload 约定已经明确：`type = ai4j.dynamic_workflow.request`，`workflowSpecVersion = ai4j.dynamic-workflow/v1`，`hostAction` 分为 `execute_dynamic_workflow` 和 `synthesize_dynamic_workflow`。
- `argumentsRaw` 有 64 KiB cap，这意味着 host 侧的 parser / dispatcher 必须对截断和 malformed payload 有明确拒绝路径。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 |
|------|------|------|----------|
| host runtime location | `ai4j-agent` | 和 workflow / sandbox / session 的所有权一致 | core SDK / plugin repo |
| 首选执行模型 | Java-native workflow compiler / dispatcher | 先把 host contract 收紧，避免直接跳到 JS 引擎 | 直接执行 JS workflow script |
| shared API changes | 最小化 | 先验证现有 extension API 是否足够 | 大范围扩 extension API |
| docs boundary | docs-site 说明 host / plugin 分工 | 让插件生态和 runtime 责任边界可读 | 只留在对话里 |
