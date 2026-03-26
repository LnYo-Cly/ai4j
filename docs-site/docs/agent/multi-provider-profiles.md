---
sidebar_position: 3
---

# 多 Provider Profile 实战

本文档聚焦 `ai4j-cli` 当前已经实现的多 provider profile 工作流：

- 全局保存 provider profile
- workspace 引用当前 profile
- workspace 单独覆盖 model
- 当前 session 内即时切换 provider / model

---

## 1. 配置文件位置

### 1.1 全局配置

```text
~/.ai4j/providers.json
```

用来保存所有可复用 profile。

### 1.2 工作区配置

```text
<workspace>/.ai4j/workspace.json
```

用来保存：

- 当前工作区引用哪个 profile
- 当前工作区是否有 model override

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
      "apiKey": "OPENAI_API_KEY"
    },
    "zhipu-main": {
      "provider": "zhipu",
      "protocol": "chat",
      "model": "glm-4.7",
      "baseUrl": "https://open.bigmodel.cn/api/coding/paas/v4",
      "apiKey": "ZHIPU_API_KEY"
    }
  }
}
```

### 2.2 `workspace.json`

```json
{
  "activeProfile": "zhipu-main",
  "modelOverride": "glm-4.7-plus"
}
```

这意味着：

- 当前 workspace 默认跑 `zhipu-main`
- 但 effective model 不再是 profile 里的 `glm-4.7`
- 而是 workspace override 的 `glm-4.7-plus`

---

## 3. 解析优先级

CLI 当前按以下顺序解析 runtime：

1. CLI 显式参数
2. workspace 配置
3. active profile
4. default profile
5. 环境变量 / system properties
6. 内建默认值

这样做的直接结果是：

- 全局 profile 负责沉淀“长期可复用的 provider runtime”
- workspace 负责声明“当前仓库到底用哪个 profile”
- CLI 参数负责当前一次运行的显式覆盖

---

## 4. 创建与编辑 profile

### 4.1 保存当前 runtime

```text
/provider save zhipu-main
```

把当前会话的 provider / protocol / model / baseUrl / apiKey 保存成 profile。

### 4.2 显式新建 profile

```text
/provider add zhipu-main --provider zhipu --model glm-4.7 --base-url https://open.bigmodel.cn/api/coding/paas/v4
```

可选参数：

```text
/provider add <profile-name> --provider <name> [--protocol <chat|responses>] [--model <name>] [--base-url <url>] [--api-key <key>]
```

如果不传 `--protocol`，CLI 会根据 provider/baseUrl 推导默认协议，并把结果保存为显式值。

### 4.3 编辑已有 profile

```text
/provider edit zhipu-main --model glm-4.7-plus
/provider edit openai-main --protocol responses
/provider edit zhipu-main --clear-api-key
```

完整语法：

```text
/provider edit <profile-name> [--provider <name>] [--protocol <chat|responses>] [--model <name>|--clear-model] [--base-url <url>|--clear-base-url] [--api-key <key>|--clear-api-key]
```

---

## 5. 切换 profile 与 model

### 5.1 切换当前 workspace profile

```text
/provider use zhipu-main
```

效果：

- 写入 `<workspace>/.ai4j/workspace.json`
- 更新 `activeProfile`
- 立即重建当前 session runtime

### 5.2 设置全局默认 profile

```text
/provider default zhipu-main
/provider default clear
```

效果：

- 写入 `~/.ai4j/providers.json`
- 影响没有 workspace activeProfile 的工作区

### 5.3 model override

```text
/model glm-4.7-plus
/model reset
```

效果：

- `/model <name>`：写入 workspace modelOverride，并立即重建当前 session runtime
- `/model reset`：清空 override，回退到 profile model

---

## 6. 协议规则

当前 CLI 对用户只暴露：

- `chat`
- `responses`

省略 `--protocol` 时，默认规则是：

- `openai` + 官方 host -> `responses`
- `openai` + 自定义兼容 `baseUrl` -> `chat`
- `doubao` / `dashscope` -> `responses`
- 其他 provider -> `chat`

历史配置中若保存的是 `auto`：

- 新版本不会继续对用户暴露 `auto`
- 旧 `providers.json` 会在加载时自动归一化成显式协议并写回

---

## 7. 当前推荐工作流

比较稳妥的实践是：

1. 先在全局层沉淀常用 profile，例如 `openai-main`、`zhipu-main`
2. 每个 workspace 只引用一个 activeProfile
3. 需要临时切模型时，用 `/model <name>` 做 workspace override
4. 如果这个模型切换会长期保留，再回头更新 profile

这样可以避免：

- 把“仓库临时测试模型”误写进全局默认 profile
- 在多个 workspace 间相互污染模型设置

---

## 8. 当前边界

需要明确几件事：

- profile 默认协议仍是本地规则推导，不是在线探测
- `/model` 候选主要来自本地 runtime/config，不会实时拉远端官方模型目录
- `responses` 当前只在部分 provider 上启用
- 这套机制优先解决 CLI coding-agent 使用场景，不是通用配置中心

