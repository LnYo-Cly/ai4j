# P1-B Agent Blueprint to AgentFactory - 执行规划

## 目标

在 P1-A `AgentBlueprint` DTO/loader/validator 已稳定的基础上，新增最小可用 `AgentFactory`，把单 Agent Blueprint 映射为 `AgentBuilder` / `Agent`。

## 关键边界

- Factory 不读取 provider token。
- Factory 不根据 `profile` 自动访问本机配置。
- Factory 不安装插件、不扫描插件目录。
- Factory 不创建真实 sandbox。
- Factory 不做 CLI `ai4j run agent.yaml`。
- Factory 不做 Team/Workflow graph DSL。

## 设计原则

P1-B 采用 host-supplied resolver 模式：

```text
AgentBlueprint
  -> AgentFactory
  -> AgentFactoryContext / resolvers
  -> AgentBuilder
  -> Agent
```

Factory 负责确定性映射和错误报告；真实模型客户端、工具注册表、扩展注册表、权限策略和 runtime 由 host 提供。

## 最小 API 切片

建议新增：

- `AgentFactory`
- `AgentFactoryContext`
- `AgentFactoryException`
- 可选 resolver 接口：`AgentModelClientResolver`、`AgentToolRegistryResolver`、`AgentPermissionPolicyResolver`、`ExtensionRegistryResolver`

P1-B 至少支持：

| Blueprint 字段 | 映射目标 | 说明 |
| --- | --- | --- |
| `model.model` | `AgentBuilder.model(...)` | 不创建 model client；只设置请求 model 名称。 |
| `model.options.temperature` | `AgentBuilder.temperature(...)` | 支持 Number / numeric String。 |
| `model.options.topP` | `AgentBuilder.topP(...)` | 支持 Number / numeric String。 |
| `model.options.maxOutputTokens` | `AgentBuilder.maxOutputTokens(...)` | 支持整数。 |
| `instructions.system` | `AgentBuilder.systemPrompt(...)` | system prompt。 |
| `instructions.developer` | `AgentBuilder.instructions(...)` | developer/agent instructions。 |
| `workflow.mode=react` | `Agents.react()` / `ReActRuntime` | 默认 react。 |
| `workflow.mode=codeact` | `Agents.codeAct()` / `CodeActRuntime` | codeact runtime。 |
| `workflow.maxTurns` | `AgentOptions.maxSteps` | 映射为最大步骤/轮数。 |
| `tools[].ref` | host resolver 或 allowlist | P1-B 可支持 explicit registry injection，不强制实现 tool name lookup。 |
| `plugins[]` | host extension resolver | 可选；无 resolver 时保留明确异常或忽略 disabled plugin。 |
| `session.memory.enabled=false` | memory supplier | 先保持默认 memory；不强制新增 NullMemory，避免影响运行语义。 |
| `sandbox` | 不执行 | enabled=true 但无 sandbox runtime 时应保留明确 unsupported exception 或 warning。 |

## 错误策略

- 先运行 `AgentBlueprintValidator`；存在 error 时抛 `AgentFactoryException`，包含 validation report。
- 缺少 host-required `AgentModelClient` 时抛稳定错误，不访问环境变量。
- Unsupported field mapping 不静默执行危险行为；比如 `sandbox.enabled=true` 在 P1-B 应明确不创建 sandbox。

## 验证

- 新增 `AgentBlueprintFactoryTest`，使用 fake `AgentModelClient`，不访问网络。
- 覆盖：react/codeact runtime mapping、system/developer instructions、model options、validation failure、missing model client、sandbox unsupported guard。
- Targeted：`mvn -pl ai4j-agent -am "-Dtest=AgentBlueprintFactoryTest" -DskipTests=false -DfailIfNoTests=false test`
- Broad：`mvn -pl ai4j-agent -am -DskipTests=false test`
- Docs：`docs-site` build。

## 后续

- P1-C CLI `ai4j run agent.yaml`。
- P2 Sandbox SPI 后再让 `sandbox` 字段真正绑定 provider。
