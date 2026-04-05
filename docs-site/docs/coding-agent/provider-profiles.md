---
sidebar_position: 6
---

# Provider Profile 与模型切换

如果你希望在多个模型提供商、多个仓库和多套运行时之间稳定切换，真正关键的不是启动命令本身，而是 `provider profile + workspace 绑定 + model override` 这套工作流。

这一页专门讲清楚这件事。

---

## 1. 两个配置文件，各自负责什么

### 1.1 `~/.ai4j/providers.json`

全局保存所有可复用的 provider profile。

它适合沉淀：

- 长期使用的 provider 连接
- 团队约定的模型名
- 默认 profile

### 1.2 `<workspace>/.ai4j/workspace.json`

工作区只保存“当前仓库到底用哪套配置”。

它适合保存：

- `activeProfile`
- `modelOverride`
- `enabledMcpServers`
- `skillDirectories`

---

## 2. 一个完整示例

### 2.1 `providers.json`

```json
{
  "defaultProfile": "openai-main",
  "profiles": {
    "openai-main": {
      "provider": "openai",
      "protocol": "responses",
      "model": "gpt-5-mini",
      "apiKey": "${OPENAI_API_KEY}"
    },
    "zhipu-main": {
      "provider": "zhipu",
      "protocol": "chat",
      "model": "glm-4.7",
      "baseUrl": "https://open.bigmodel.cn/api/coding/paas/v4",
      "apiKey": "${ZHIPU_API_KEY}"
    },
    "deepseek-compatible": {
      "provider": "openai",
      "protocol": "chat",
      "model": "deepseek-chat",
      "baseUrl": "https://api.deepseek.com",
      "apiKey": "${DEEPSEEK_API_KEY}"
    }
  }
}
```

### 2.2 `workspace.json`

```json
{
  "activeProfile": "zhipu-main",
  "modelOverride": "glm-4.7-plus",
  "enabledMcpServers": ["fetch", "mysql-dev"]
}
```

这表示：

- 当前仓库默认跑 `zhipu-main`
- 但 effective model 已经被工作区覆盖成 `glm-4.7-plus`
- 当前仓库还额外启用了 `fetch` 和 `mysql-dev`

---

## 3. 常用命令

### 3.1 新建 profile

```text
/provider add <profile-name> --provider <name> [--protocol <chat|responses>] [--model <name>] [--base-url <url>] [--api-key <key>]
```

示例：

```text
/provider add zhipu-main --provider zhipu --model glm-4.7 --base-url https://open.bigmodel.cn/api/coding/paas/v4
```

### 3.2 保存当前运行时

```text
/provider save <profile-name>
```

适合把当前已经验证可用的 provider / protocol / model 保存下来。

### 3.3 编辑已有 profile

```text
/provider edit <profile-name> [--provider <name>] [--protocol <chat|responses>] [--model <name>|--clear-model] [--base-url <url>|--clear-base-url] [--api-key <key>|--clear-api-key]
```

示例：

```text
/provider edit zhipu-main --model glm-4.7-plus
/provider edit openai-main --protocol responses
/provider edit zhipu-main --clear-api-key
```

### 3.4 切换当前仓库使用的 profile

```text
/provider use <profile-name>
```

效果：

- 写入 `<workspace>/.ai4j/workspace.json`
- 更新 `activeProfile`
- 立即重建当前 session runtime

### 3.5 设置全局默认 profile

```text
/provider default <profile-name>
/provider default clear
```

效果：

- 写入 `~/.ai4j/providers.json`
- 影响没有 `activeProfile` 的工作区

### 3.6 模型覆盖

```text
/model <name>
/model reset
```

效果：

- `/model <name>`：只对当前仓库写入 `modelOverride`
- `/model reset`：清空覆盖，回到 profile 中声明的模型

---

## 4. 默认协议规则

当前 CLI 只对用户暴露两种协议：

- `chat`
- `responses`

如果没有显式指定 `--protocol` 或 profile 中的 `protocol`，默认规则是：

- `openai` + 官方 OpenAI host：`responses`
- `openai` + 自定义兼容 `baseUrl`：`chat`
- `doubao` / `dashscope`：`responses`
- 其他 provider：`chat`

这是一套本地路由规则，不是远端 capability probe。

---

## 5. 三类最常见配置样例

### 5.1 OpenAI 官方

```json
{
  "provider": "openai",
  "protocol": "responses",
  "model": "gpt-5-mini",
  "apiKey": "${OPENAI_API_KEY}"
}
```

推荐：

- 官方 OpenAI host 优先使用 `responses`
- 不写 `baseUrl` 时，CLI 会按官方 host 处理

### 5.2 Zhipu Coding Endpoint

```json
{
  "provider": "zhipu",
  "protocol": "chat",
  "model": "glm-4.7",
  "baseUrl": "https://open.bigmodel.cn/api/coding/paas/v4",
  "apiKey": "${ZHIPU_API_KEY}"
}
```

推荐：

- Zhipu coding endpoint 走 `chat`
- `baseUrl` 使用 coding endpoint，而不是普通对话入口

### 5.3 OpenAI-compatible 自定义 Host

```json
{
  "provider": "openai",
  "protocol": "chat",
  "model": "deepseek-chat",
  "baseUrl": "https://api.deepseek.com",
  "apiKey": "${DEEPSEEK_API_KEY}"
}
```

推荐：

- `provider` 仍然可以用 `openai`
- 只要 `baseUrl` 是兼容层而不是官方 OpenAI host，通常就按 `chat` 理解

---

## 6. 推荐工作流

比较稳妥的实践是：

1. 先在全局沉淀稳定 profile，例如 `openai-main`、`zhipu-main`
2. 每个仓库只绑定一个 `activeProfile`
3. 模型试验只通过 `/model <name>` 写到 `workspace.json`
4. 确认这个试验结果会长期保留后，再回头更新 profile

这样可以避免：

- 把仓库级临时试验误写成全局默认
- 多个仓库互相污染模型设置

---

## 7. 不推荐的做法

- 把真实 API Key 直接提交进仓库
- 把临时测试模型直接改成全局默认 profile
- 把 OpenAI-compatible 自定义 host 当成官方 OpenAI `responses` 规则来理解

---

## 8. 继续阅读

1. [配置体系](/docs/coding-agent/configuration)
2. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
3. [命令参考](/docs/coding-agent/command-reference)
