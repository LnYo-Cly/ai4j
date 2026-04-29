---
sidebar_position: 6
---

# Provider Profile 与模型切换

Provider profile 在 `Coding Agent` 里不是“几个命令的集合”，而是一套可切换的 runtime binding。

真正的控制链是：

- 全局 `providers.json` 保存可复用 profile 资产
- 工作区 `workspace.json` 选择当前仓库的 `activeProfile`
- `CliProviderConfigManager.resolve(...)` 解析出 `effectiveProfile`
- `/provider use`、编辑当前生效 profile 等动作会触发当前 session runtime 重绑

这也是为什么 provider/profile 文档不能只写“如何新增一个 JSON 配置”，而必须把解析链和运行后果讲清楚。

## 1. 一个 profile 实际上保存什么

源码入口：

- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/provider/CliProviderProfile.java`

字段非常少：

- `provider`
- `protocol`
- `model`
- `baseUrl`
- `apiKey`

也就是说，profile 只表达一件事：

- 如何构造一套稳定可复用的 provider runtime 参数

它不保存：

- 当前工作区是否启用
- MCP server 开关
- skills 目录
- compact / outer loop 参数

这些都属于别的控制面。

## 2. `defaultProfile`、`activeProfile`、`effectiveProfile`

这三个名字很像，但语义不同。

### 2.1 `defaultProfile`

保存在：

- `~/.ai4j/providers.json`

表示：

- 当工作区没有显式绑定 profile 时，全局默认选谁

### 2.2 `activeProfile`

保存在：

- `<workspace>/.ai4j/workspace.json`

表示：

- 这个仓库当前声明想用哪个 profile

### 2.3 `effectiveProfile`

存在于：

- `CliResolvedProviderConfig`

表示：

- 本次解析后真正生效的 profile 名字

它的回退顺序是：

1. 工作区 `activeProfile` 存在且可解析
2. 全局 `defaultProfile` 存在且可解析
3. 否则无 profile，继续靠环境变量和默认值补齐

## 3. `/provider` 命令不是纯文件操作

真正的控制逻辑主要在：

- `CodingCliSessionRunner`
- `AcpJsonRpcServer`

特别是 `CodingCliSessionRunner` 中这些方法：

- `switchToProviderProfile(...)`
- `saveCurrentProviderProfile(...)`
- `addProviderProfile(...)`
- `editProviderProfile(...)`
- `removeProviderProfile(...)`
- `setDefaultProviderProfile(...)`

### 3.1 `/provider use <name>`

这条命令会：

1. 校验 profile 是否存在
2. 把 `workspace.json.activeProfile` 改成目标值
3. 重新解析 runtime options
4. `switchSessionRuntime(...)` 重绑当前 session

它的意义不是“下次启动再生效”，而是：

- 立刻切换当前仓库的当前 session runtime

### 3.2 `/provider save <name>`

它保存的不是手写参数，而是“当前运行中的有效 provider 状态”，包括：

- 当前 provider
- 当前 protocol
- 当前 model
- 当前 baseUrl
- 当前 apiKey

如果全局还没有 `defaultProfile`，当前实现会把第一次保存的 profile 直接设为默认。

### 3.3 `/provider add <name> ...`

它是“从显式字段创建 profile”，而不是“保存当前状态”。

如果你没传 `--protocol`，实现会根据：

- provider
- baseUrl

推导默认协议。

### 3.4 `/provider edit <name> ...`

它只改已有 profile。

一个特别重要的实现细节是：

- 只有当你编辑的是当前 `effectiveProfile` 时，session 才会被重绑

否则只是改配置文件，不会影响当前正在跑的 runtime。

### 3.5 `/provider remove <name>`

删除 profile 后，如果它同时是：

- 全局默认 profile
- 当前工作区 active profile

对应字段都会被清掉。

也就是说，删除不仅是从 map 里去掉一项，还会联动清理依赖它的绑定关系。

### 3.6 `/provider default <name|clear>`

只改全局默认绑定，不会直接替某个工作区写 `activeProfile`。

这决定了它的作用边界：

- 影响没有工作区显式绑定的仓库
- 不会覆盖已经声明了 `activeProfile` 的仓库

## 4. `/model` 和 profile 的关系

`/model <name>` 不是改 profile，而是写：

- `workspace.json.modelOverride`

解析优先级上，`modelOverride` 高于 profile 里的 model。

所以更稳的工作流通常是：

1. profile 保存长期稳定的 provider + protocol + baseline model
2. 仓库级模型试验只写 `modelOverride`
3. 试验结果确认长期保留后，再回头更新 profile

这样能避免：

- 仓库级试验污染全局 profile
- 多个仓库互相覆盖模型选择

## 5. 协议默认值和支持边界

### 5.1 默认协议推导

当前 `CliProtocol.defaultProtocol(...)` 的规则是：

- `openai` + 官方 host -> `responses`
- `openai` + 自定义兼容 host -> `chat`
- `doubao` / `dashscope` -> `responses`
- 其他 provider -> `chat`

### 5.2 真正允许使用 `responses` 的 provider

当前 `ai4j-cli` 明确限制：

- 只有 `openai`
- `doubao`
- `dashscope`

支持 `responses`

其余 provider 即使你手动指定 `responses`，也会被当前实现拒绝，并报：

- `Provider <name> does not support responses protocol in ai4j-cli yet`

这条限制同时存在于：

- session 侧命令校验
- agent factory 侧 runtime 构建校验

因此这里不是“建议”，而是当前实现的硬边界。

## 6. 三种常见 profile 形态

### 6.1 OpenAI 官方

```json
{
  "provider": "openai",
  "protocol": "responses",
  "model": "gpt-5-mini",
  "apiKey": "${OPENAI_API_KEY}"
}
```

适合：

- 官方 OpenAI host
- 希望直接走 responses 语义

### 6.2 OpenAI-compatible 自定义 host

```json
{
  "provider": "openai",
  "protocol": "chat",
  "model": "deepseek-chat",
  "baseUrl": "https://api.deepseek.com",
  "apiKey": "${DEEPSEEK_API_KEY}"
}
```

这里最容易误判的是：

- `provider` 写 `openai` 不代表一定应该用 `responses`

当前实现里，只要 `baseUrl` 不是官方 OpenAI host，默认就会往 `chat` 推。

### 6.3 Zhipu / 自定义 coding endpoint

```json
{
  "provider": "zhipu",
  "protocol": "chat",
  "model": "glm-4.7",
  "baseUrl": "https://open.bigmodel.cn/api/coding/paas/v4",
  "apiKey": "${ZHIPU_API_KEY}"
}
```

这种 profile 的关键不是字段数量，而是：

- provider、protocol 和 baseUrl 三者必须对齐

## 7. 推荐工作流

更稳的做法通常是：

1. 在全局先沉淀少量稳定 profile，例如 `openai-main`、`zhipu-main`
2. 每个仓库只绑定一个 `activeProfile`
3. 仓库内模型试验优先用 `/model`
4. 只有当某个组合稳定复用时，再回写到 profile

这样可以把：

- 全局可复用资产
- 仓库级局部试验

明确拆开。

## 8. 不推荐的做法

- 把真实 API Key 写进仓库
- 把一次临时模型试验直接改成全局默认 profile
- 把 OpenAI-compatible host 当成官方 OpenAI responses host 来理解
- 用 profile 去承载本应属于 workspace 的局部开关

## 9. 继续阅读

1. [配置体系](/docs/coding-agent/configuration)
2. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
3. [命令参考](/docs/coding-agent/command-reference)
4. [MCP 与 ACP](/docs/coding-agent/mcp-and-acp)
