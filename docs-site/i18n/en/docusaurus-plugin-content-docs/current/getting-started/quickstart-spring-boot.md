---
title: "Quickstart for Spring Boot"
description: "The shortest path to wire AI4J into a Spring Boot project: pull in the starter, write a minimal application.yml, inject the AiService Bean, and ship a complete controller that sends the first model request."
tags: [how-to]
---

# Quickstart for Spring Boot

This page gives a Spring Boot project the shortest path to success. For plain Java projects, use [Quickstart for Java](/docs/getting-started/quickstart-java) directly.

The main module covered here is:

- `ai4j-spring-boot-starter/`

The goal is not to walk through the entire SDK, but to first confirm that the starter can place `AiService` into your Spring container and send the first model request through an HTTP endpoint.

## 1. What this path verifies

Once it runs green, you will have confirmed:

- The starter dependency has entered the application module
- The `ai.*` configuration can be read
- `AiService` is injectable as a Bean
- The application can create an `IChatService`
- A controller can return model text

It deliberately does not cover:

- Tool / Function Call
- MCP
- RAG
- Agent runtime
- Production-grade rate limiting, audit, and exception mapping

## 2. Minimal dependency

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-spring-boot-starter</artifactId>
  <version>2.4.2</version>
</dependency>
```

If your project also pulls in `ai4j-agent`, `ai4j-coding`, the FlowGram starter, and other modules, use `ai4j-bom` to align versions.

## 3. Minimal configuration

`application.yml`:

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
```

Keep the key in an environment variable:

```powershell
$env:OPENAI_API_KEY="sk-..."
```

:::danger Never commit keys
Never write a real key in `application.yml`. Config committed to the repository should only keep placeholders like `${OPENAI_API_KEY}`.
:::

## 4. The Spring Boot object chain

```text
ai.* config
    -> auto-configuration
        -> AiService Bean
            -> IChatService
                -> ChatCompletionResponse
```

The starter binds the configuration to AI4J's `Configuration` and exposes `AiService` to the Spring container. Business code only needs to inject `AiService`.

## 5. Service

```java
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import org.springframework.stereotype.Service;

@Service
public class AiChatService {
    private final AiService aiService;

    public AiChatService(AiService aiService) {
        this.aiService = aiService;
    }

    public String chatOnce(String userInput) throws Exception {
        IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
        ChatCompletion request = ChatCompletion.builder()
                .model("gpt-4o-mini")
                .message(ChatMessage.withUser(userInput))
                .build();

        ChatCompletionResponse response = chatService.chatCompletion(request);
        return response.getChoices().get(0).getMessage().getContent().getText();
    }
}
```

## 6. Controller

```java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
public class AiChatController {
    private final AiChatService chatService;

    public AiChatController(AiChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String q) throws Exception {
        return chatService.chatOnce(q);
    }
}
```

## 7. Verification

After starting the application, call:

```bash
curl "http://localhost:8080/ai/chat?q=%E7%94%A8%E4%B8%80%E5%8F%A5%E8%AF%9D%E4%BB%8B%E7%BB%8D%20AI4J"
```

On success you will see a piece of model-returned text. This result confirms the starter wiring chain is in place.

## 8. The regression contract for this page's example

The starter first-chat injection chain on this page is protected by a test in this repository:

```bash
mvn -pl ai4j-spring-boot-starter -Dtest=AiServiceFirstChatAutoConfigurationTest -DskipTests=false test
```

This command does not hit a real provider. It verifies that `ai.openai.api-key` and `ai.openai.api-host` can be bound into `AiService`'s `Configuration`, and that the `IChatService` for `PlatformType.OPENAI` can be created. A real request still requires a valid key, network access, and an available model.

## 9. OpenAI-compatible / TroveBox configuration

TroveBox or other OpenAI-compatible relay platforms do not need a new provider. For a single-instance scenario, just change `api-host`:

```yaml
ai:
  openai:
    api-key: ${TROVEBOX_API_KEY}
    api-host: https://codex.trovebox.online/
```

The business code stays the same:

```java
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
```

For the full writeup, see [OpenAI-compatible and TroveBox](/docs/capabilities/models/openai-compatible-and-trovebox).

## 10. Multiple provider profiles

When one application needs multiple provider sets or multiple relay platforms, use `ai.platforms` and `AiServiceRegistry`:

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

```java
IChatService chatService = aiServiceRegistry.getChatService("trovebox-low-cost");
```

The `id` is the business routing name. The `platform` decides which underlying provider adapter AI4J uses.

## 11. Common failure points

| Symptom | Check first |
| --- | --- |
| `AiService` cannot be injected | Whether the starter dependency is in the application module and Spring Boot has scanned the auto-configuration |
| API Key is empty | Whether the current launch process has the `OPENAI_API_KEY` environment variable |
| Authentication failed | Whether the key, host, and model name match the current provider |
| Request timeout | Network, proxy, and provider reachability |
| Controller returns an exception stack | Catch and log the provider error in the service layer first, then decide whether to build a unified exception mapping |
| Registry cannot find the profile | Whether `ai.platforms[].id` matches the id used in code |

## 12. When not to use the starter

In these scenarios you can use `ai4j` directly:

- Your project is not Spring Boot
- You are writing a Java library and do not want to depend on the Spring container
- You just want to send a single request in a CLI, test, or small tool
- You want full control over `Configuration` and `OkHttpClient`

## 13. After it runs green

- For the underlying `Chat` call semantics: see [Core SDK / Model Access / Chat](/docs/capabilities/models/chat)
- To configure more providers: see [Spring Boot / Configuration Reference](/docs/integrations/spring-boot/configuration-reference)
- To extend Beans or reuse business services: see [Spring Boot / Bean Extension](/docs/integrations/spring-boot/bean-extension)
- For Tool / Function Call: see [First Tool Call](/docs/getting-started/first-tool-call)
