---
title: "Quickstart for Java"
description: "The shortest path to integrate AI4J into a plain Java / Maven project: from dependencies and environment variables to your first synchronous Chat request, with copy-ready Configuration→AiService→IChatService closed-loop code and success criteria."
tags: [how-to]
---

# Quickstart for Java

This page is the official shortest integration path for plain Java / Maven projects. It walks a complete minimal closed loop from dependencies and API keys through to your first model call.

The main module this page maps to:

- `ai4j/`

The goal is well-defined: no Spring, no Agent, no RAG — just get the first synchronous `Chat` request working in a `main` method or a test method.

## 1. What this path verifies

Once it runs, you can confirm at minimum:

- The Maven dependency declaration is correct
- The API Key is read from an environment variable
- `Configuration` can carry the provider configuration
- `AiService` can create an `IChatService` for the target provider
- The first synchronous `Chat` request returns a `ChatCompletionResponse` and the text can be read

It does not address, for now:

- Spring Boot auto-configuration
- Tool / Function Call
- `Responses`
- MCP
- Agent runtime
- RAG / VectorStore

## 2. Minimal dependency

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j</artifactId>
  <version>2.4.2</version>
</dependency>
```

If your project will pull in multiple AI4J modules at the same time, use `ai4j-bom` to align versions; a first chat does not require the BOM up front.

## 3. Set the environment variable

PowerShell:

```powershell
$env:OPENAI_API_KEY="sk-..."
```

Bash:

```bash
export OPENAI_API_KEY="sk-..."
```

:::danger Never commit secrets
Do not write API keys into Java files, Git-tracked configuration files, or README examples.
:::

## 4. The shortest chain for the first chat

For a plain Java first chat, start with this concrete object chain:

```text
Configuration
    -> AiService
        -> IChatService
            -> ChatCompletion
                -> ChatCompletionResponse
```

`Configuration` holds the provider configuration; `AiService` is the service factory; `IChatService` handles the `Chat` request; `ChatCompletion` is the request object; `ChatCompletionResponse` is the response object.

## 5. Copy-ready code

```java
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;

public class Ai4jFirstChat {
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Missing OPENAI_API_KEY");
        }

        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiKey(apiKey);

        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(openAiConfig);

        AiService aiService = new AiService(configuration);
        IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

        ChatCompletion request = ChatCompletion.builder()
                .model("gpt-4o-mini")
                .message(ChatMessage.withUser("Introduce AI4J in one sentence"))
                .build();

        ChatCompletionResponse response = chatService.chatCompletion(request);
        String text = response.getChoices().get(0).getMessage().getContent().getText();
        System.out.println(text);
    }
}
```

## 6. When to keep extending the object chain

The following cases still extend along this same object chain:

- You need a custom `OkHttpClient`
- You need streaming output
- You need multimodal, Tool / Function Call, MCP, or RAG
- You need to read usage, finish reason, or tool calls from `ChatCompletionResponse`
- You need to manage multiple provider profiles or an `AiServiceRegistry` within one application

:::warning Do not switch to a hidden entry point
Do not, to "save a few lines", switch to a non-existent `ChatClient` or a hidden `Ai4j.chat()`. AI4J's current strength is that this object chain can keep carrying Tool, MCP, RAG, Memory, Responses, and a custom network stack.
:::

## 7. About `OkHttpClient`

AI4J's `Configuration` already ships with an `OkHttpClient` by default; a plain Java first chat does not require you to create the client manually.

Only in the following cases do you need to call `configuration.setOkHttpClient(...)`:

- You need to configure a proxy
- You need to tune timeouts
- You need to add interceptors
- You need to reuse the application's existing connection pool or network stack

For the first chat, prefer the default client to reduce the number of moving parts.

## 8. The regression contract for this page's example

The plain Java first-chat object chain on this page is protected by repository tests:

```bash
mvn -pl ai4j -Dtest=FirstChatCopyableCodeTest,ConfigurationTest -DskipTests=false test
```

This is not a live provider test. It uses a local HTTP double to verify the complete object chain, the request path, the authentication header, and text retrieval from `ChatCompletionResponse`, and uses `ConfigurationTest` to lock down the default `OkHttpClient` behavior. Real model quality, quota, network, and provider availability remain external conditions when you run your first chat.

## 9. What to change when switching providers

| What you switch to | What needs to change |
| --- | --- |
| OpenAI-compatible endpoint / TroveBox | `OpenAiConfig#setApiHost(...)`, model name, API Key |
| DeepSeek | Use `DeepSeekConfig`, and switch to `PlatformType.DEEPSEEK` |
| Moonshot | Use `MoonshotConfig`, and switch to `PlatformType.MOONSHOT` |
| DashScope | Use `DashScopeConfig`, and switch to `PlatformType.DASHSCOPE` |
| Ollama | Use `OllamaConfig`, and switch to `PlatformType.OLLAMA` |

The rule: the provider config class and the `PlatformType` must match.

OpenAI-compatible relay platforms typically still use `OpenAiConfig` and `PlatformType.OPENAI`:

```java
OpenAiConfig openAiConfig = new OpenAiConfig();
openAiConfig.setApiKey(System.getenv("TROVEBOX_API_KEY"));
openAiConfig.setApiHost("https://codex.trovebox.online/");
```

For the full configuration guide, see [OpenAI-compatible and TroveBox relay platform configuration](/docs/start-here/openai-compatible-and-trovebox).

## 10. Success criteria

| Checkpoint | Success meaning |
| --- | --- |
| The project compiles | Dependencies and imports are correct |
| No `Missing OPENAI_API_KEY` | The current runtime can read the key |
| No provider authentication error | The API Key, host, and model name are basically valid |
| Non-empty text output | The first synchronous `Chat` chain is established |

If you only want a local compile check first and do not want to call a real provider, write a unit test to verify object construction and dependency resolution; a real model request still requires a valid key and network.

## 11. After it runs

- To integrate with Spring Boot: see [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
- To use TroveBox or another relay platform: see [OpenAI-compatible and TroveBox](/docs/start-here/openai-compatible-and-trovebox)
- To understand `Chat` in depth: see [Core SDK / Model Access / Chat](/docs/core-sdk/model-access/chat)
- To let the model call local functions: see [First Tool Call](/docs/start-here/first-tool-call)
- To see the full capability map: see [Feature Map](/docs/start-here/feature-map)

→ API Javadoc: [`AiService`](https://javadoc.io/doc/io.github.lnyo-cly/ai4j/2.4.2/io/github/lnyocly/ai4j/service/factory/AiService.html) · [`IChatService`](https://javadoc.io/doc/io.github.lnyo-cly/ai4j/2.4.2/io/github/lnyocly/ai4j/service/IChatService.html)
