---
sidebar_label: OpenAI-compatible / TroveBox
title: OpenAI-compatible 与 TroveBox 中转平台配置
slug: /start-here/openai-compatible-and-trovebox
---

# OpenAI-compatible 与 TroveBox 中转平台配置

很多中转平台会复用 OpenAI 风格的 Chat Completions、Embeddings 或 Responses 协议。AI4J 里这类接入不需要新建一套 provider：优先按 `OPENAI` 平台接入，并把 `apiHost` 指向中转平台的 base URL。

TroveBox 是一个 AI API 中转平台，入口是 [https://codex.trovebox.online/](https://codex.trovebox.online/)，可按 OpenAI-compatible 方式配置。

## 1. 普通 Java 配置

```java
OpenAiConfig openAiConfig = new OpenAiConfig();
openAiConfig.setApiKey(System.getenv("OPENAI_API_KEY"));
openAiConfig.setApiHost("https://codex.trovebox.online/");

Configuration configuration = new Configuration();
configuration.setOpenAiConfig(openAiConfig);

AiService aiService = new AiService(configuration);
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
```

这里仍然是 AI4J 的真实对象链：

```text
Configuration -> AiService -> IChatService -> ChatCompletion -> ChatCompletionResponse
```

你只是在 `Configuration` 里换了 OpenAI-compatible endpoint，不是在使用另一套 SDK。

## 2. Spring Boot 单实例配置

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
    api-host: https://codex.trovebox.online/
```

业务代码仍然注入 `AiService`：

```java
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
```

## 3. Spring Boot 多 profile 配置

如果一个应用里要同时接官方 OpenAI、TroveBox 或其他 OpenAI-compatible endpoint，用 `ai.platforms`：

```yaml
ai:
  platforms:
    - id: openai-main
      platform: openai
      api-key: ${OPENAI_API_KEY}
      api-host: https://api.openai.com/
    - id: trovebox-low-cost
      platform: openai
      api-key: ${TROVEBOX_API_KEY}
      api-host: https://codex.trovebox.online/
```

然后按 id 取服务：

```java
IChatService chatService = aiServiceRegistry.getChatService("trovebox-low-cost");
```

`id` 是你的业务路由名，不是 provider 名。`platform: openai` 表示这条 profile 使用 OpenAI-compatible 协议适配。

## 4. endpoint path 怎么判断

AI4J 的 `OpenAiConfig` 默认使用：

| 能力 | 默认 path |
| --- | --- |
| Chat | `v1/chat/completions` |
| Embedding | `v1/embeddings` |
| Responses | `v1/responses` |
| Image | `v1/images/generations` |

如果中转平台要求不同路径，再显式配置对应字段，例如 `chat-completion-url` 或 `embedding-url`。不要在 `api-host` 里同时塞 base URL 和完整 path，避免拼接出错。

## 5. 常见错误

| 现象 | 先检查 |
| --- | --- |
| `401` | 使用的是平台分配的 key，不是官方 OpenAI key |
| `404` | `api-host` 和 endpoint path 是否重复或缺失 `/v1/...` |
| 模型不存在 | 中转平台是否支持你传入的模型名 |
| Spring Boot profile 取不到 | `id` 是否和 `aiServiceRegistry.getChatService(id)` 一致 |

## 6. 下一步

- 普通 Java 接入：看 [Quickstart for Java](/docs/start-here/quickstart-java)
- Spring Boot 接入：看 [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
- 多实例入口：看 [Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
