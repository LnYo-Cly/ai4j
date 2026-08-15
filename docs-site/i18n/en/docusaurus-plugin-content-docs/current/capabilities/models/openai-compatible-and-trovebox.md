---
sidebar_label: OpenAI-compatible / TroveBox
title: "OpenAI-compatible and TroveBox Relay Platform Configuration"
description: "How to configure OpenAI-compatible relay platforms (including TroveBox) in AI4J: plain Java and Spring Boot single/multi-profile setup, endpoint path resolution, and common 401/404 troubleshooting."
tags: [integration]
---

# OpenAI-compatible and TroveBox Relay Platform Configuration

Many relay platforms reuse the OpenAI-style Chat Completions, Embeddings, or Responses protocols. Wiring these into AI4J does not require building a new provider: integrate as the `OPENAI` platform and point `apiHost` at the relay platform's base URL.

TroveBox is an AI API relay platform. Its entry point is [https://codex.trovebox.online/](https://codex.trovebox.online/) and it can be configured as OpenAI-compatible.

## 1. Plain Java Configuration

```java
OpenAiConfig openAiConfig = new OpenAiConfig();
openAiConfig.setApiKey(System.getenv("OPENAI_API_KEY"));
openAiConfig.setApiHost("https://codex.trovebox.online/");

Configuration configuration = new Configuration();
configuration.setOpenAiConfig(openAiConfig);

AiService aiService = new AiService(configuration);
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
```

This is still AI4J's real object chain:

```text
Configuration -> AiService -> IChatService -> ChatCompletion -> ChatCompletionResponse
```

You are only swapping the OpenAI-compatible endpoint inside `Configuration`, not using a separate SDK.

## 2. Spring Boot Single-Instance Configuration

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
    api-host: https://codex.trovebox.online/
```

Business code still injects `AiService`:

```java
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
```

## 3. Spring Boot Multi-Profile Configuration

If a single application needs to connect to official OpenAI, TroveBox, or other OpenAI-compatible endpoints at the same time, use `ai.platforms`:

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

Then retrieve the service by id:

```java
IChatService chatService = aiServiceRegistry.getChatService("trovebox-low-cost");
```

The `id` is your business routing name, not the provider name. `platform: openai` means this profile uses the OpenAI-compatible protocol adapter.

## 4. How to Determine the endpoint path

AI4J's `OpenAiConfig` uses the following defaults:

| Capability | Default path |
| --- | --- |
| Chat | `v1/chat/completions` |
| Embedding | `v1/embeddings` |
| Responses | `v1/responses` |
| Image | `v1/images/generations` |

:::warning endpoint path configuration
If the relay platform requires different paths, explicitly configure the corresponding field, such as `chat-completion-url` or `embedding-url`. Do not put both the base URL and the full path in `api-host`, to avoid concatenation errors.
:::

## 5. Common Errors

| Symptom | Check first |
| --- | --- |
| `401` | Are you using the key assigned by the platform, not the official OpenAI key |
| `404` | Whether `api-host` and the endpoint path duplicate or miss the `/v1/...` segment |
| Model does not exist | Whether the relay platform supports the model name you passed in |
| Spring Boot profile cannot be retrieved | Whether the `id` matches the one in `aiServiceRegistry.getChatService(id)` |

## 6. Next Steps

- Plain Java integration: see [Quickstart for Java](/docs/getting-started/quickstart-java)
- Spring Boot integration: see [Quickstart for Spring Boot](/docs/getting-started/quickstart-spring-boot)
- Multi-instance entry point: see [Service Entry and Registry](/docs/capabilities/service-entry)
