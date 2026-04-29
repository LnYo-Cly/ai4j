---
sidebar_position: 5
---

# 配置体系

`Coding Agent` 的配置层不是简单的“填 provider 和 API Key”，而是一套控制面：

- 当前 session 用哪个 provider / protocol / model
- 这些设置来自哪一层
- 哪些配置是全局资产，哪些是工作区绑定，哪些只是本次运行覆盖
- 修改后是否只改配置，还是会触发当前 session runtime 重绑

要把这些问题讲清楚，最值得直接看的源码不是文档示例，而是：

- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/provider/CliProviderConfigManager.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/mcp/CliMcpConfigManager.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/config/CliWorkspaceConfig.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/factory/DefaultCodingCliAgentFactory.java`

## 1. 这套配置系统实际分成三层

### 1.1 全局 provider 资产

路径：

- `~/.ai4j/providers.json`

由 `CliProviderConfigManager.globalProvidersPath()` 决定。

它保存：

- `defaultProfile`
- `profiles`

也就是“长期可复用的 provider 连接资产”，而不是某个仓库当前正在用什么。

### 1.2 全局 MCP 注册表

路径：

- `~/.ai4j/mcp.json`

由 `CliMcpConfigManager.globalMcpPath()` 决定。

它保存的是：

- 全局有哪些 MCP server 定义可用

它不直接决定“当前仓库启用了哪些 server”。启用状态仍然由工作区配置控制。

### 1.3 工作区绑定

路径：

- `<workspace>/.ai4j/workspace.json`

由 `CliProviderConfigManager.workspaceConfigPath()` 和 `CliMcpConfigManager.workspaceConfigPath()` 共用。

它保存的不是 provider 资产本身，而是当前仓库的绑定和局部覆盖，例如：

- `activeProfile`
- `modelOverride`
- `enabledMcpServers`
- `skillDirectories`
- `agentDirectories`
- `experimentalSubagentsEnabled`
- `experimentalAgentTeamsEnabled`

## 2. `workspace.json` 不是全局配置的副本

当前工作区会同时存在配置文件和运行期产物：

```text
<workspace>/.ai4j/
  workspace.json
  sessions/
  teams/
    state/
    mailbox/
```

要把它们分开理解：

- `workspace.json`：声明这个仓库的默认绑定与开关
- `sessions/`：session 快照、事件账本等运行期持久化结果
- `teams/state`：Team runtime 的结构化状态
- `teams/mailbox`：成员消息流水

也就是说，`sessions/` 和 `teams/` 是 runtime artifact，不是 hand-written config。

## 3. provider 解析链不是一个统一顺序，而是按字段分别解析

`CliProviderConfigManager.resolve(...)` 不会先做一个“大 merge”，而是对每个字段分别找值。

### 3.1 `provider`

解析顺序：

1. CLI 显式 `--provider`
2. `activeProfile.provider`
3. `defaultProfile.provider`
4. 环境变量 `AI4J_PROVIDER`
5. system property `ai4j.provider`
6. 内建默认值 `openai`

### 3.2 `baseUrl`

解析顺序：

1. CLI 显式 `--base-url`
2. `activeProfile.baseUrl`
3. `defaultProfile.baseUrl`
4. 环境变量 `AI4J_BASE_URL`
5. system property `ai4j.base-url`

### 3.3 `protocol`

解析顺序：

1. CLI 显式 `--protocol`
2. `activeProfile.protocol`
3. `defaultProfile.protocol`
4. 环境变量 `AI4J_PROTOCOL`
5. system property `ai4j.protocol`
6. 如果为空或 `auto`，再由 `CliProtocol.resolveConfigured(...)` 结合 provider + baseUrl 推导默认协议

### 3.4 `model`

解析顺序：

1. CLI 显式 `--model`
2. `workspace.json` 中的 `modelOverride`
3. `activeProfile.model`
4. `defaultProfile.model`
5. 环境变量 `AI4J_MODEL`
6. system property `ai4j.model`

这里最容易忽略的一点是：

- `modelOverride` 的优先级高于 profile 里的 model

所以工作区可以稳定绑定某个 profile，同时对模型做局部试验，而不污染全局 profile。

### 3.5 `apiKey`

解析顺序：

1. CLI 显式 `--api-key`
2. `activeProfile.apiKey`
3. `defaultProfile.apiKey`
4. 环境变量 `AI4J_API_KEY`
5. system property `ai4j.api.key`
6. provider-specific env，例如 `OPENAI_API_KEY`、`DOUBAO_API_KEY`

这说明密钥注入并不只认一个环境变量名字，而是支持全局通用入口和 provider 专用入口两层。

## 4. `activeProfile`、`defaultProfile`、`effectiveProfile` 不是同一个概念

`CliResolvedProviderConfig` 会同时保留三类字段：

- `activeProfile`
- `defaultProfile`
- `effectiveProfile`

它们的区别是：

- `activeProfile`：工作区声明想用哪个 profile
- `defaultProfile`：全局默认 profile
- `effectiveProfile`：在当前上下文里真正生效的 profile 名称

`effectiveProfile` 的解析规则是：

- 如果工作区 `activeProfile` 存在且在全局 profiles 里能找到，优先用它
- 否则，如果全局 `defaultProfile` 存在且能找到，用它
- 否则为 `null`

这意味着：

- 工作区里写了一个不存在的 `activeProfile`，不会神奇生效
- 系统会回落到全局默认，或继续走环境变量 / 默认值

## 5. profile 和 workspace 配置在读取时会被标准化

`CliProviderConfigManager.loadProvidersConfig()` 和 `loadWorkspaceConfig()` 都会在读出后做 normalize。

直接后果包括：

- profile 名和字段值会被 trim
- 空字符串会被清成 `null`
- 不存在的 `defaultProfile` 会被清掉
- 空白 profile 名会被移除
- `enabledMcpServers` / `skillDirectories` / `agentDirectories` 会被去空、去重、保序

这意味着配置系统不是“原样读取 JSON”，而是在读写时维护一套受控格式。

## 6. 协议默认值不是任意选择，而是有本地推导规则

`CliProtocol.defaultProtocol(provider, baseUrl)` 当前默认规则是：

- `openai` 且 baseUrl 为空或包含 `api.openai.com` -> `responses`
- `openai` 且使用自定义兼容 host -> `chat`
- `doubao` / `dashscope` -> `responses`
- 其他 provider -> `chat`

这是一套本地推导规则，不是远端 capability probe。

更重要的是，`responses` 不是所有 provider 都支持。当前 `ai4j-cli` 明确限制：

- 只有 `openai`、`doubao`、`dashscope` 允许 `responses`

这个限制同时存在于：

- `DefaultCodingCliAgentFactory.assertSupportedProtocol(...)`
- `CodingCliSessionRunner.isSupportedProviderProtocol(...)`

所以“协议默认推成 responses”和“runtime 真正允许 responses”是两层判断，但当前实现两层都对齐在这三家 provider 上。

## 7. `experimental*` 开关当前是 default-on

`CliWorkspaceConfig` 里的：

- `experimentalSubagentsEnabled`
- `experimentalAgentTeamsEnabled`

如果为 `null`，当前 `DefaultCodingCliAgentFactory` 的判断是：

- 视为 `true`

对应源码：

- `isExperimentalSubagentsEnabled(...)`
- `isExperimentalAgentTeamsEnabled(...)`

这意味着工作区不写这两个字段时，系统默认会把相关实验性 agent tool surface 打开。

它们控制的是：

- 是否把 subagent / agent-teams 对应能力注入运行时

而不是 provider/profile 本身。

## 8. 配置变更不一定只是改文件，可能会重绑当前 session

这是 `Coding Agent` 配置层和普通 SDK 配置文档最大的不同。

像下面这些动作，不只是保存 JSON：

- `/provider use`
- 编辑当前生效 profile

它们会：

1. 改写对应配置文件
2. 调用解析链重新算出新的 runtime options
3. 重建当前 session runtime

因此这套配置系统本质上是一个 live control plane，而不只是静态配置说明。

## 9. 什么时候该把值写到哪一层

可以按职责来判断：

- 多个仓库复用的 provider 资产：写进 `providers.json`
- 某个仓库默认用哪套 profile：写进 `workspace.json.activeProfile`
- 某个仓库临时试验模型：写进 `workspace.json.modelOverride`
- 哪些 MCP server 可以存在：写进 `mcp.json`
- 某个仓库当前启用哪些 MCP server：写进 `workspace.json.enabledMcpServers`
- 临时单次试验：用 CLI 参数覆盖
- 密钥和环境切换：优先用环境变量或 system properties

## 10. 这页没有覆盖的另一半：运行行为参数

这页讲的是“连谁”和“从哪里解析”；另一半是“怎么跑”。

真正控制 compact、auto-continue、tool-result micro compact、stop condition 的是：

- `CodingAgentOptions`

也就是说：

- provider/profile 配置解决目标模型与连接问题
- `CodingAgentOptions` 解决 outer loop 与上下文管理策略问题

这两层不要混写。

## 11. 继续阅读

1. [Provider Profile 与模型切换](/docs/coding-agent/provider-profiles)
2. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
3. [命令参考](/docs/coding-agent/command-reference)
4. [Compact 与 Checkpoint 机制](/docs/coding-agent/compact-and-checkpoint)
5. [MCP 与 ACP](/docs/coding-agent/mcp-and-acp)
