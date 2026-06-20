---
sidebar_position: 5
---

# 配置体系

`Coding Agent` 的配置层不是“把 provider 和 API Key 填进去”就结束了。

当前实现里，配置真正控制的是三件事：

- 这次 session 最终连哪个 provider / protocol / model
- 这些值分别从 CLI、环境、全局资产、工作区绑定中的哪一层来
- 某个配置修改后，是只改 JSON，还是会重建当前 session runtime

如果要按源码理解这套系统，最值得直接看的入口是：

- `ai4j-cli/.../provider/CliProviderConfigManager`
- `ai4j-cli/.../mcp/CliMcpConfigManager`
- `ai4j-cli/.../config/CliWorkspaceConfig`
- `ai4j-cli/.../factory/DefaultCodingCliAgentFactory`
- `ai4j-cli/.../runtime/CodingCliSessionRunner`
- `ai4j-cli/.../acp/AcpJsonRpcServer`

---

## 1. 先看真实配置链

CLI / TUI 路径里，一次配置解析大致会经过这条链：

```text
Ai4jCli / CodeCommand
  -> CodeCommandOptionsParser.parse(...)
  -> CliProviderConfigManager.resolve(...)
  -> CliMcpConfigManager.resolve(...)
  -> DefaultCodingCliAgentFactory.resolveProtocol(...)
  -> buildWorkspaceContext(...)
  -> buildCodingOptions(...)
  -> CodingAgentBuilder.build()
```

ACP 路径也是同一套基础规则，只是入口从 `AcpCommand` / `AcpJsonRpcServer` 进入，而不是终端 runner。

这条链有个很重要的后果：

- provider / protocol / model 的“生效值”不是单个文件直接给出的
- 它是解析器、工作区配置、全局配置和运行期 override 共同算出来的结果

---

## 2. 当前配置面实际分成三层

### 2.1 全局 provider 资产

路径：

```text
~/.ai4j/providers.json
```

由 `CliProviderConfigManager.globalProvidersPath()` 决定。

它存的是：

- `defaultProfile`
- `profiles`

也就是“这台机器上有哪些可复用 provider profile”，而不是某个仓库当前正在绑定哪一个。

### 2.2 全局 MCP 定义

路径：

```text
~/.ai4j/mcp.json
```

由 `CliMcpConfigManager.globalMcpPath()` 决定。

它回答的是：

- 这台机器上定义了哪些 MCP server

它不直接回答：

- 当前仓库启用了哪些 MCP server

### 2.3 工作区绑定

路径：

```text
<workspace>/.ai4j/workspace.json
```

`CliProviderConfigManager` 和 `CliMcpConfigManager` 都会落到这个文件。

它当前承载的是仓库级绑定和开关，例如：

- `activeProfile`
- `modelOverride`
- `enabledMcpServers`
- `skillDirectories`
- `agentDirectories`
- `experimentalSubagentsEnabled`
- `experimentalAgentTeamsEnabled`

这里的核心思想是：

- 全局文件存“资产定义”
- workspace 文件存“本仓选择与局部覆盖”

---

## 3. `workspace.json` 不是运行期状态目录

当前 `<workspace>/.ai4j/` 里通常不只一个文件：

```text
<workspace>/.ai4j/
  workspace.json
  sessions/
  teams/
    state/
    mailbox/
```

要把它们明确分开：

- `workspace.json`：人工维护的仓库级配置
- `sessions/`：session 快照和事件账本
- `teams/state`：团队运行态快照
- `teams/mailbox`：成员消息流水

也就是说：

- `workspace.json` 是 control plane
- `sessions/`、`teams/` 是 runtime artifact

这点如果混淆，就很容易把“持久化状态”误解成“静态配置”。

---

## 4. provider 解析不是整体 merge，而是逐字段求值

`CliProviderConfigManager.resolve(...)` 不是把几份 JSON 先合成一份大对象再读。

它对每个字段都单独走一遍优先级链。

### 4.1 `provider`

当前顺序是：

1. CLI 显式 `--provider`
2. `activeProfile.provider`
3. `defaultProfile.provider`
4. `AI4J_PROVIDER`
5. `ai4j.provider`
6. 默认值 `openai`

### 4.2 `baseUrl`

当前顺序是：

1. CLI 显式 `--base-url`
2. `activeProfile.baseUrl`
3. `defaultProfile.baseUrl`
4. `AI4J_BASE_URL`
5. `ai4j.base-url`

### 4.3 `protocol`

当前顺序是：

1. CLI 显式 `--protocol`
2. `activeProfile.protocol`
3. `defaultProfile.protocol`
4. `AI4J_PROTOCOL`
5. `ai4j.protocol`
6. 如果为空或 `auto`，再走 `CliProtocol.defaultProtocol(...)`

### 4.4 `model`

当前顺序是：

1. CLI 显式 `--model`
2. `workspace.json.modelOverride`
3. `activeProfile.model`
4. `defaultProfile.model`
5. `AI4J_MODEL`
6. `ai4j.model`

这里最重要的一点是：

- `modelOverride` 的优先级高于 profile 自带的 model

这允许一个仓库固定绑定某个 provider profile，但单独试验另一套模型，而不污染全局 profile。

### 4.5 `apiKey`

当前顺序是：

1. CLI 显式 `--api-key`
2. `activeProfile.apiKey`
3. `defaultProfile.apiKey`
4. `AI4J_API_KEY`
5. `ai4j.api.key`
6. provider-specific env，例如 `OPENAI_API_KEY`

这说明当前实现同时支持：

- 通用密钥入口
- provider 专用环境变量入口

---

## 5. 显式 `--provider` 会影响 profile 是否还参与解析

这是当前实现里一个很容易忽略、但非常关键的细节。

`CliProviderConfigManager.resolve(...)` 在遇到显式 `providerOverride` 时，会先做：

- `alignProfileWithProvider(activeProfile, explicitProvider)`
- `alignProfileWithProvider(defaultProfile, explicitProvider)`

这意味着：

- 如果当前 `activeProfile` 是 `zhipu`
- 但你显式传了 `--provider openai`

那么这个 `zhipu` profile 不会继续拿来给 `baseUrl`、`model`、`apiKey` 兜底。

它会被视为“不再匹配当前 provider”。

这个行为很合理，因为否则会出现：

- provider 已切成 `openai`
- 但还偷偷继承了另一个 provider profile 的 URL 或 key

那会让运行结果非常不可预测。

---

## 6. `activeProfile`、`defaultProfile`、`effectiveProfile` 不是同一件事

`CliResolvedProviderConfig` 会同时保留：

- `activeProfile`
- `defaultProfile`
- `effectiveProfile`

它们分别表示：

- `activeProfile`：工作区想绑定的 profile 名
- `defaultProfile`：机器级全局默认 profile 名
- `effectiveProfile`：当前上下文里最终真的拿来参与解析的 profile 名

`effectiveProfile` 的规则是：

- 优先用工作区 `activeProfile`，前提是它真的存在
- 否则回落到全局 `defaultProfile`，前提是它也真的存在
- 否则为 `null`

所以：

- 工作区写了一个不存在的 profile 名，不会强行生效
- 系统会回落，而不是静默伪造一个 profile

---

## 7. 读出来的配置会先被标准化

当前 `loadProvidersConfig()`、`loadWorkspaceConfig()`、`loadGlobalConfig()` 都不是“原样 parse JSON 就结束”。

它们会立刻做 normalize。

常见效果包括：

- 去掉字段前后空白
- 空字符串归一成 `null`
- 空白 profile 名被移除
- 不存在的 `defaultProfile` 被清掉
- `enabledMcpServers`、`skillDirectories`、`agentDirectories` 去空、去重、保序
- MCP 的 `http` transport 被归一成 `streamable_http`

所以这套系统更接近“受控配置模型”，不是“任意 JSON 存储”。

---

## 8. protocol 默认值有本地推导规则，不是动态探测

`CliProtocol.defaultProtocol(provider, baseUrl)` 当前是静态规则：

- `openai` 且 `baseUrl` 为空或包含 `api.openai.com` -> `responses`
- `openai` 且用了自定义兼容 host -> `chat`
- `doubao` / `dashscope` -> `responses`
- 其他 provider -> `chat`

这背后并没有远端 capability probe。

也就是说：

- 默认协议是本地推导结果
- 不是运行时向 provider 询问“你支持哪种协议”

---

## 9. “默认能推出来”不等于“运行时一定允许”

当前 `DefaultCodingCliAgentFactory.resolveProtocol(...)` 和 ACP 路径里的 provider/protocol 校验，还会继续做一层运行时限制。

现阶段 `responses` 明确只允许：

- `openai`
- `doubao`
- `dashscope`

因此当前有两层判断：

1. 推导默认协议
2. 校验当前 provider 是否支持这个协议

这两层在实现上是分开的，所以排障时要分清：

- 是“默认值推错了”
- 还是“默认值虽然推出来了，但 runtime 拒绝这组组合”

---

## 10. `experimental*` 当前是 default-on

`CliWorkspaceConfig` 里有两个很容易被忽略的布尔开关：

- `experimentalSubagentsEnabled`
- `experimentalAgentTeamsEnabled`

当前 `DefaultCodingCliAgentFactory` 的判断是：

- 字段为 `null` 时，按 `true` 处理

所以不写这两个字段，不是关闭实验能力，而是默认打开。

它们控制的不是 provider 连接，而是：

- 是否把实验性 subagent tool surface
- 是否把 experimental team tool surface

注入当前 runtime。

---

## 11. 哪些改动会触发当前 session runtime 重绑

这是 `Coding Agent` 配置体系和普通 SDK 配置说明最大的区别。

在当前 CLI/TUI 路径里，像下面这些命令并不只是改文件：

- `/provider use`
- `/provider add|edit|default|remove`
- `/model`
- `/stream on|off`
- `/experimental ...`
- `/mcp enable|disable|pause|resume|retry|remove`

这些操作通常会走：

```text
修改配置或内存状态
  -> resolveConfiguredRuntimeOptions(...)
  -> switchSessionRuntime(...)
  -> 用新 options / 新 MCP runtime 重建当前 session 宿主层
```

所以这套配置系统本质上是 live control plane。

不是“保存配置，等下次重启再说”。

---

## 12. ACP 里哪些配置是可变的

ACP 模式不是把 CLI 配置能力全部复制一遍。

当前 `AcpJsonRpcServer` 对外暴露的 `configOptions` 只有两项：

- `mode`
- `model`

对应方法是：

- `session/set_mode`
- `session/set_config_option`

其中：

- `mode` 控制 ACP 会话里的审批模式
- `model` 控制后续 turns 的有效模型

这说明 ACP 当前更偏“会话期控制”，而不是“完整配置文件编辑器”。

---

## 13. 当前哪些值根本不在 `workspace.json` 里

很多人会直觉觉得：既然是工作区绑定，`baseUrl` 和 `apiKey` 也该落进去。

但当前并不是这样。

`workspace.json` 主要承载：

- profile 绑定
- model override
- MCP enablement
- skill / agent roots
- experimental 开关

而下面这些值没有作为 workspace 持久字段存在：

- `apiKey`
- `baseUrl`
- provider profile 详细内容

它们仍然属于：

- CLI override
- env / property
- 全局 profile 资产

这个边界是有意设计的，因为把敏感凭证和 repo 绑定混在一起风险很高。

---

## 14. 最常见的失败路径

### 14.1 `activeProfile` 写了不存在的名字

结果：

- 不会按你想的 profile 生效
- 会回落到 `defaultProfile` 或环境变量链

### 14.2 显式 `--provider` 后，原 profile 不再匹配

结果：

- 旧 profile 的 `baseUrl` / `model` / `apiKey` 可能不再参与求值

### 14.3 自定义 OpenAI 兼容 host，却忘了协议默认会变

结果：

- `openai + custom baseUrl` 默认更偏 `chat`，不是官方 OpenAI host 下的 `responses`

### 14.4 以为改了配置文件就一定要重启整个 CLI

结果：

- 很多 slash command 路径实际上会直接重建当前 session runtime
- 不理解这点时，会误判“为什么配置马上生效了”

### 14.5 把运行策略和连接配置写成一锅

结果：

- `provider/profile/model` 的问题和 `auto-compact` / outer loop / tool approval 的问题混在一起，排障会很痛苦

---

## 15. 哪些东西不属于这页

这页讲的是：

- “连谁”
- “从哪里解析”
- “改了之后 runtime 会不会重绑”

它不讲另一组 equally important 的运行策略参数：

- auto-compact
- compact window
- reserve tokens
- keep recent tokens
- approval behavior
- outer loop stop / continue behavior

这些更多属于：

- `CodingAgentOptions`
- session / runtime 行为层

不要把 provider/profile 配置和 loop / compact 策略混成一页心智模型。

---

## 16. 这页最该记住的结论

- 当前配置面分成全局 provider 资产、全局 MCP 定义、工作区绑定三层
- 配置解析是逐字段求值，不是整体 merge
- 显式 `--provider` 会使不匹配的 profile 退出解析链
- `modelOverride` 的优先级高于 profile model
- protocol 默认值是本地规则推导，不是远端探测
- 很多配置命令会直接重建当前 session runtime，而不是等下次启动再生效

---

## 17. 继续阅读

1. [Provider Profile 与模型切换](/docs/coding-agent/provider-profiles)
2. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
3. [命令参考](/docs/coding-agent/command-reference)
4. [MCP 对接](/docs/coding-agent/mcp-integration)
5. [Compact 与 Checkpoint 机制](/docs/coding-agent/compact-and-checkpoint)
