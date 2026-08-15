---
title: "Spring Boot Quickstart"
description: "The shortest path to a working AI4J integration in Spring Boot: add the starter, configure ai.*, inject AiService, and issue your first ChatCompletion."
tags: [how-to]
---

# Spring Boot Quickstart

If you haven't yet got your first Spring Boot request working, start here.

## 1. The shortest success path

This page verifies exactly four things:

1. Pull in `ai4j-spring-boot-starter`
2. Configure `ai.*` in `application.yml`
3. Inject `AiService`
4. Issue your first `ChatCompletion`

If these four steps hold, it means:

- The starter is in your project
- Auto-configuration has taken effect
- Configuration binding is working
- A usable AI4J Bean is already sitting in the container

## 2. Minimum dependency

The minimum dependency is just:

- `ai4j-spring-boot-starter`

This path assumes the `Core SDK` has already been brought into the container by the starter; you don't need to manually reassemble the lower-level entry points.

## 3. Minimum configuration

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
```

If your network environment requires a proxy, add `ai.okhttp.*` as well.

The core judgment at this step is not "have you memorized every field", but rather:

- Whether the configuration actually enters the Spring environment
- Whether the provider's minimum required fields are in place

## 4. Minimum invocation

```java
@Autowired
private AiService aiService;

public String chatOnce(String userInput) throws Exception {
    IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
    ChatCompletion req = ChatCompletion.builder()
            .model("gpt-4o-mini")
            .message(ChatMessage.withUser(userInput))
            .build();
    return chatService.chatCompletion(req)
            .getChoices().get(0).getMessage().getContent().getText();
}
```

What this code actually verifies is:

- Spring has injected `AiService`
- `AiService` can resolve a service by `PlatformType`
- The first model request already runs inside a business Bean

## 5. Where to go next once it works

Continue in this order:

1. [Auto Configuration](/docs/integrations/spring-boot/auto-configuration)
2. [Configuration Reference](/docs/integrations/spring-boot/configuration-reference)
3. [Bean Extension](/docs/integrations/spring-boot/bean-extension)
4. [Common Patterns](/docs/integrations/spring-boot/common-patterns)

If you're not yet clear on the underlying capability boundaries, go back and fill in:

1. [Core SDK / Overview](/docs/capabilities/overview)
2. [Core SDK / Service Entry and Registry](/docs/capabilities/service-entry)
3. [Core SDK / Model Access](/docs/capabilities/models/overview)

## 6. What to check first when it doesn't work

Check in this order:

1. Whether the starter was actually pulled in
2. Whether the `ai.*` configuration is being read by Spring
3. Whether the API Key / network / proxy are working
4. Whether you can't get the Bean at all, or you can get the Bean but the request fails

If it's a wiring-stage problem, revisit:

- [Troubleshooting](/docs/production/troubleshooting)
- [Configuration Reference](/docs/integrations/spring-boot/configuration-reference)
