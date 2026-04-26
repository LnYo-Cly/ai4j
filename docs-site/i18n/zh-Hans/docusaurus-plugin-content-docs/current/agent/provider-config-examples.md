---
sidebar_position: 5
---

# Provider 配置样例

本文档给出当前 `ai4j-cli` coding-agent 场景下最常用的三类配置样例：

- Zhipu
- OpenAI 官方
- OpenAI-compatible 自定义 `baseUrl`

所有示例都使用占位符，不要把真实密钥提交进仓库。

以下命令示例默认已经通过安装脚本拿到了 `ai4j` 命令；如果你仍想从源码运行，只需要把 `ai4j` 替换成 `java -jar .\\ai4j-cli\\target\\ai4j-cli-<version>-jar-with-dependencies.jar`。

---

## 1. Zhipu（Coding Endpoint）

### 1.1 启动命令

```powershell
ai4j tui `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

### 1.2 profile 样例

```json
{
  "provider": "zhipu",
  "protocol": "chat",
  "model": "glm-4.7",
  "baseUrl": "https://open.bigmodel.cn/api/coding/paas/v4",
  "apiKey": "${ZHIPU_API_KEY}"
}
```

### 1.3 说明

- 当前 Zhipu 在 `ai4j-cli` 里走 `chat`
- `baseUrl` 建议填写 coding endpoint
- 如果你省略 `--protocol`，默认也会落到 `chat`

---

## 2. OpenAI 官方

### 2.1 one-shot 样例

```powershell
ai4j code `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --prompt "Explain the project structure"
```

### 2.2 profile 样例

```json
{
  "provider": "openai",
  "protocol": "responses",
  "model": "gpt-5-mini",
  "apiKey": "${OPENAI_API_KEY}"
}
```

### 2.3 说明

- 当前 OpenAI 官方 host 默认会落到 `responses`
- 如果你不传 `baseUrl`，CLI 会把它视为官方 OpenAI host
- 如果你显式写 `--protocol chat`，CLI 也会照配执行，但当前推荐官方 OpenAI 优先使用 `responses`

---

## 3. OpenAI-compatible 自定义 `baseUrl`

这里指：

- provider 名仍然使用 `openai`
- 但请求实际发往一个兼容 OpenAI API 的自定义地址

典型例子包括一些兼容层或第三方平台。

### 3.1 启动命令

```powershell
ai4j code `
  --provider openai `
  --protocol chat `
  --model deepseek-chat `
  --base-url https://api.deepseek.com `
  --workspace .
```

### 3.2 profile 样例

```json
{
  "provider": "openai",
  "protocol": "chat",
  "model": "deepseek-chat",
  "baseUrl": "https://api.deepseek.com",
  "apiKey": "${DEEPSEEK_API_KEY}"
}
```

### 3.3 说明

- 当前只要是 `openai` + 自定义 `baseUrl`，默认协议就会落到 `chat`
- 这是本地路由规则，不是在线探测
- 如果第三方平台本身要求别的路径格式，请以对方兼容层文档为准

---

## 4. `providers.json` 完整样例

```json
{
  "defaultProfile": "zhipu-main",
  "profiles": {
    "zhipu-main": {
      "provider": "zhipu",
      "protocol": "chat",
      "model": "glm-4.7",
      "baseUrl": "https://open.bigmodel.cn/api/coding/paas/v4",
      "apiKey": "${ZHIPU_API_KEY}"
    },
    "openai-main": {
      "provider": "openai",
      "protocol": "responses",
      "model": "gpt-5-mini",
      "apiKey": "${OPENAI_API_KEY}"
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

---

## 5. `workspace.json` 样例

```json
{
  "activeProfile": "zhipu-main",
  "modelOverride": "glm-4.7-plus"
}
```

这表示：

- 当前仓库默认使用 `zhipu-main`
- 但模型临时覆盖成 `glm-4.7-plus`

---

## 6. 推荐做法

- 官方 OpenAI：优先 `responses`
- Zhipu coding endpoint：使用 `chat`
- OpenAI-compatible 自定义 host：优先 `chat`
- 长期稳定配置沉淀到 `providers.json`
- 仓库级模型试验只写进 `workspace.json`

---

## 7. 不推荐的做法

- 在仓库里提交真实 API key
- 把临时测试模型直接改成全局默认 profile
- 把 OpenAI-compatible 自定义 host 错配成官方 OpenAI 的 `responses` 默认思路

