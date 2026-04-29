---
sidebar_position: 6
---

# Provider Profile 与模型切换

在 `Coding Agent` 里，provider profile 不是“几个命令的集合”，而是一套可被切换、可被回退、并且会影响当前 session runtime 的连接绑定。

如果只把它理解成一个 JSON 模板，会漏掉两个关键事实：

- profile 解析要和 `workspace.json.activeProfile`、`modelOverride`、CLI override 一起看
- 某些 `/provider`、`/model` 操作会直接重建当前 session runtime，而不是只改配置文件

---

## 1. 先看 profile 参与生效链的真实位置

当前主链可以压成：

```text
providers.json + workspace.json
  -> CliProviderConfigManager.resolve(...)
  -> CliResolvedProviderConfig
  -> DefaultCodingCliAgentFactory.resolveProtocol(...)
  -> createModelClient(...)
  -> CodingAgentBuilder.build()
```

然后在运行期，CLI/TUI 或 ACP 下的命令又会把这条链重新触发：

```text
/provider ... 或 /model ...
  -> 改 providers.json / workspace.json 或 session config
  -> 重新解析 effective provider config
  -> switchSessionRuntime(...) / applyModelChange(...)
  -> 当前 session runtime 重绑
```

所以 provider profile 在当前实现里不是“静态资产说明”，而是 live binding 的一部分。

---

## 2. 一个 profile 实际只保存五个字段

源码入口在：

- `ai4j-cli/.../provider/CliProviderProfile`

字段非常克制：

- `provider`
- `protocol`
- `model`
- `baseUrl`
- `apiKey`

这意味着 profile 只描述一件事：

- 如何形成一套可复用的 provider 连接参数

它不负责保存：

- 当前仓库是否启用
- MCP server 开关
- skills / agents 目录
- compact / approval / session 策略

这些属于其他控制面。

---

## 3. `defaultProfile`、`activeProfile`、`effectiveProfile` 必须分开理解

### `defaultProfile`

保存在：

- `~/.ai4j/providers.json`

含义是：

- 机器级默认 profile

### `activeProfile`

保存在：

- `<workspace>/.ai4j/workspace.json`

含义是：

- 当前仓库希望绑定哪个 profile

### `effectiveProfile`

存在于：

- `CliResolvedProviderConfig`

含义是：

- 当前解析后真正参与求值的 profile 名称

当前回退顺序是：

1. 工作区 `activeProfile` 存在且可解析
2. 全局 `defaultProfile` 存在且可解析
3. 否则无 profile，继续靠 env / properties / 默认值补齐

这意味着：

- `activeProfile` 写了一个不存在的名字，并不会神奇生效
- 它会回落，而不是“假装成功”

---

## 4. 显式 `--provider` 会把不匹配 profile 踢出解析链

这是最容易被忽略、但很重要的实现细节。

`CliProviderConfigManager.resolve(...)` 在遇到显式 provider override 时，会先做 profile 对齐：

- `alignProfileWithProvider(activeProfile, explicitProvider)`
- `alignProfileWithProvider(defaultProfile, explicitProvider)`

结果是：

- 如果当前工作区激活的是 `zhipu` profile
- 你却显式传了 `--provider openai`

那么这个 `zhipu` profile 不会继续贡献它的：

- `baseUrl`
- `model`
- `apiKey`

这是当前实现用来避免“provider 切了，但还继承另一个 provider 的老参数”的关键保护。

---

## 5. `/provider` 命令不只是写文件

CLI/TUI 路径的主要逻辑在：

- `CodingCliSessionRunner`

ACP 路径也有镜像实现，在：

- `AcpJsonRpcServer`

当前这两条路径都支持：

- `provider use`
- `provider save`
- `provider add`
- `provider edit`
- `provider default`
- `provider remove`

但它们的运行后果并不一样。

---

## 6. `/provider use <name>` 的真实后果

这条命令不是“给下次启动做准备”，而是会立刻影响当前会话。

CLI/TUI 路径当前会：

1. 校验 profile 是否存在
2. 写 `workspace.json.activeProfile`
3. 重新解析 runtime options
4. 调 `switchSessionRuntime(...)`
5. 用新的 provider 绑定重建当前 session 宿主层

ACP 路径也有同等语义，只是由 `AcpJsonRpcServer.switchToProviderProfile(...)` 走自己的会话重绑链。

所以：

- `/provider use` 是 live switch
- 不是 deferred config

---

## 7. `/provider save <name>` 保存的是“当前有效运行态”

`/provider save <name>` 不是简单复制手写参数。

它保存的是当前运行中的有效 provider 状态，也就是：

- 当前 `provider`
- 当前 `protocol`
- 当前 `model`
- 当前 `baseUrl`
- 当前 `apiKey`

这很适合把一次已经验证成功的运行态沉淀成 profile。

另外一个小细节是：

- 如果全局还没有 `defaultProfile`
- 当前实现会把第一次保存的 profile 直接设成默认

---

## 8. `/provider add` 和 `/provider save` 不是一回事

### `/provider save <name>`

语义是：

- 从当前运行态保存

### `/provider add <name> ...`

语义是：

- 从显式字段创建一个新 profile

这条命令如果没显式给 `--protocol`，仍然会按：

- provider
- baseUrl

去推导默认协议。

所以它更像“声明式创建”，不是“快照当前 runtime”。

---

## 9. `/provider edit` 什么时候会重绑当前 session

`/provider edit <name> ...` 不会无脑重建当前 runtime。

当前实现里，只有当你编辑的是：

- 当前 `effectiveProfile`

才会触发 session runtime 重绑。

如果你只是改一个当前并未生效的 profile，它只会更新配置文件，不会影响当前会话。

这点非常重要，因为它保证了：

- profile 库可以被后台维护
- 而不会每改一项都打断当前 session

---

## 10. `/provider remove` 和 `/provider default` 的边界不同

### `/provider remove <name>`

删除的不只是一个 map entry。

当前实现还会联动清理：

- `providers.json.defaultProfile`
- `workspace.json.activeProfile`

前提是它们正好指向这个被删除的 profile。

### `/provider default <name|clear>`

它只影响全局默认绑定。

不会直接覆盖：

- 某个工作区已经声明的 `activeProfile`

也就是说：

- `default` 影响“没有仓库级绑定的情况”
- `active` 才是仓库级强绑定

---

## 11. `/model` 和 profile 是刻意分层的

`/model <name>` 不会改 profile。

它写的是：

- `workspace.json.modelOverride`

而 `CliProviderConfigManager.resolve(...)` 的优先级又规定：

- `modelOverride` 高于 profile 里的 `model`

这带来的工程意义很明确：

- profile 保存长期稳定的 provider + baseline model
- 仓库级实验模型使用 `modelOverride`
- 成熟后再回写到 profile

这能避免：

- 一次仓库试验污染所有仓库共享的 profile

---

## 12. ACP 里的 `/provider`、`/model` 不是缩水版

ACP 当前也支持：

- `providers`
- `provider`
- `model`

这些命令在 `AcpSlashCommandSupport` 中被保留，并由 `AcpJsonRpcServer` 里的运行时命令处理器执行。

其中：

- `/provider ...` 仍然会走切 profile、保存 profile、编辑 profile 的逻辑
- `/model ...` 仍然会通过 `applyModelChange(...)` 改变当前 ACP session 后续 turns 的有效模型

所以 ACP 不是只能“看状态”，它也能在宿主会话内直接切 provider/model，只是表现形式变成了 headless slash command。

---

## 13. 协议默认值和协议支持边界要分两层看

### 13.1 默认协议推导

当前 `CliProtocol.defaultProtocol(...)` 的规则是：

- `openai` + 官方 host -> `responses`
- `openai` + 自定义兼容 host -> `chat`
- `doubao` / `dashscope` -> `responses`
- 其他 provider -> `chat`

### 13.2 真正允许 `responses` 的 provider

当前 runtime 只明确支持：

- `openai`
- `doubao`
- `dashscope`

这条限制在 CLI/TUI 和 ACP 路径都有校验。

所以别把下面两件事混成一件：

- “默认协议会怎么推”
- “这个 provider 组合运行时到底放不放行”

---

## 14. 三类最常见 profile 形态

### OpenAI 官方 host

```json
{
  "provider": "openai",
  "protocol": "responses",
  "model": "gpt-5-mini",
  "apiKey": "${OPENAI_API_KEY}"
}
```

### OpenAI-compatible 自定义 host

```json
{
  "provider": "openai",
  "protocol": "chat",
  "model": "deepseek-chat",
  "baseUrl": "https://api.deepseek.com",
  "apiKey": "${DEEPSEEK_API_KEY}"
}
```

这里最重要的不是字段形式，而是：

- `provider=openai` 不等于必须走 `responses`

### Zhipu / 自定义 coding endpoint

```json
{
  "provider": "zhipu",
  "protocol": "chat",
  "model": "glm-4.7",
  "baseUrl": "https://open.bigmodel.cn/api/coding/paas/v4",
  "apiKey": "${ZHIPU_API_KEY}"
}
```

这种 profile 的关键是：

- provider、protocol、baseUrl 三者必须相互对齐

---

## 15. 推荐工作流

更稳的工作方式通常是：

1. 全局维护少量稳定 profile，例如 `openai-main`、`zhipu-main`
2. 每个仓库只绑定一个 `activeProfile`
3. 仓库级模型试验优先走 `/model`
4. profile 真正稳定后，再回写或编辑全局 profile

这相当于把：

- 机器级可复用资产
- 仓库级局部实验

拆成两层治理。

---

## 16. 最容易踩坑的 5 个点

### 16.1 显式切 provider 后还以为旧 profile 会继续兜底

当前实现会主动切断不匹配 profile 的字段继承。

### 16.2 把 `/model` 当成 profile 编辑

它改的是 `workspace.json.modelOverride`，不是 profile。

### 16.3 用 OpenAI-compatible host 却沿用官方 OpenAI 协议预期

自定义 `baseUrl` 会改变默认协议推导。

### 16.4 编辑了非当前 effective profile，却期待当前 session 立即变化

当前不会。

### 16.5 把 API Key 固化进仓库

profile 可以保存 key 字段，但实际团队使用中仍应优先让敏感值走环境变量或本地受控配置。

---

## 17. 这页最该记住的结论

- profile 是 provider runtime binding，不是普通 JSON 模板
- `activeProfile`、`defaultProfile`、`effectiveProfile` 语义不同
- 显式 `--provider` 会让不匹配的 profile 退出解析链
- `/provider use`、ACP 下的 provider 切换都会重绑当前 session runtime
- `/model` 是仓库级 override，不是 profile 编辑

---

## 18. 继续阅读

1. [配置体系](/docs/coding-agent/configuration)
2. [命令参考](/docs/coding-agent/command-reference)
3. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
4. [ACP 集成](/docs/coding-agent/acp-integration)
