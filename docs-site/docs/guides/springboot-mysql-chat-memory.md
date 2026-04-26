---
sidebar_position: 1
---

# Spring Boot + MySQL：多轮聊天与 ChatMemory 持久化

这是一条最适合业务系统起步的链路：

- 直接使用 `ai4j-spring-boot-starter`
- 用 `JdbcChatMemory` 把会话落到 MySQL
- 让同一个 `sessionId` 在多次请求之间自动续上上下文

如果你现在还不需要完整 `Agent runtime`，而只是想先把“多轮聊天 + 会话持久化”做好，这页就是最短路径。

## 1. 适用场景

适合：

- Web 聊天页
- 企业问答助手
- 多轮客服机器人
- 同一用户会话需要跨实例恢复

不适合：

- 需要自动工具循环和复杂推理状态
- 需要 planning / handoff / trace / code execution

这类场景应直接看：

- [Memory 记忆管理与压缩策略](/docs/agent/memory-management)

## 2. 依赖

最常见的 Spring Boot 组合如下：

```xml
<dependencies>
  <dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-spring-boot-starter</artifactId>
    <version>2.1.0</version>
  </dependency>

  <dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

如果你项目里已经用了 `ai4j-bom`，这里可以不再单独写版本。

## 3. `application.yml`

下面用 OpenAI 作为示例模型服务，你也可以换成 DeepSeek、Doubao、Ollama 等其它已接入 provider。

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/ai4j?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456

ai:
  openai:
    api-key: ${OPENAI_API_KEY}
    api-host: https://api.openai.com/
```

这一页的重点不在 Spring Session，而在 `JdbcChatMemory`：

- `DataSource` 由 Spring Boot 提供
- `JdbcChatMemory` 直接复用这个 `DataSource`
- 默认会自动建表 `ai4j_chat_memory`

## 4. 先封装一个会话 Memory 工厂

```java
import io.github.lnyocly.ai4j.memory.ChatMemory;
import io.github.lnyocly.ai4j.memory.JdbcChatMemory;
import io.github.lnyocly.ai4j.memory.JdbcChatMemoryConfig;
import io.github.lnyocly.ai4j.memory.MessageWindowChatMemoryPolicy;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class ChatMemoryFactory {

    private final DataSource dataSource;

    public ChatMemoryFactory(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public ChatMemory create(String sessionId) {
        return new JdbcChatMemory(
                JdbcChatMemoryConfig.builder()
                        .dataSource(dataSource)
                        .sessionId(sessionId)
                        .policy(new MessageWindowChatMemoryPolicy(20))
                        .build()
        );
    }
}
```

这里用了一个非常实用的默认值：

- 只保留最近 20 条 memory item

这样做的好处是：

- 不需要一上来就做复杂压缩
- 可以先控制上下文成本
- 行为稳定，可预期

## 5. 聊天服务

```java
import io.github.lnyocly.ai4j.memory.ChatMemory;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import org.springframework.stereotype.Service;

@Service
public class ChatSessionService {

    private final AiService aiService;
    private final ChatMemoryFactory chatMemoryFactory;

    public ChatSessionService(AiService aiService, ChatMemoryFactory chatMemoryFactory) {
        this.aiService = aiService;
        this.chatMemoryFactory = chatMemoryFactory;
    }

    public String chat(String sessionId, String userMessage) throws Exception {
        IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
        ChatMemory memory = chatMemoryFactory.create(sessionId);

        if (memory.getItems().isEmpty()) {
            memory.addSystem("你是一个简洁、准确的 AI4J 产品助手。");
        }

        memory.addUser(userMessage);

        ChatCompletion request = ChatCompletion.builder()
                .model("gpt-4o-mini")
                .messages(memory.toChatMessages())
                .build();

        ChatCompletionResponse response = chatService.chatCompletion(request);
        String answer = response.getChoices().get(0).getMessage().getContent().getText();

        memory.addAssistant(answer);
        return answer;
    }
}
```

这里最关键的是两行：

```java
memory.addUser(userMessage);
memory.toChatMessages();
```

它们意味着：

- 当前轮用户输入会先写入持久化 memory
- 发给模型的是“本轮 + 历史轮次”的完整上下文

返回后再执行：

```java
memory.addAssistant(answer);
```

这样下一轮请求来时，这条回答已经在库里了。

## 6. 提供一个最小 Controller

```java
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatSessionService chatSessionService;

    public ChatController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @PostMapping("/message")
    public Map<String, Object> message(@RequestHeader("X-Session-Id") String sessionId,
                                       @RequestBody Map<String, String> body) throws Exception {
        String answer = chatSessionService.chat(sessionId, body.get("message"));
        return Collections.<String, Object>singletonMap("answer", answer);
    }
}
```

推荐用请求头、Cookie 或网关层 token 来确定 `sessionId`，不要让前端随意拼一个公共固定值。

## 7. 验证方式

第一次请求：

```bash
curl -X POST "http://127.0.0.1:8080/chat/message" ^
  -H "Content-Type: application/json" ^
  -H "X-Session-Id: demo-session-001" ^
  -d "{\"message\":\"请用一句话介绍 AI4J\"}"
```

第二次请求继续同一个 `sessionId`：

```bash
curl -X POST "http://127.0.0.1:8080/chat/message" ^
  -H "Content-Type: application/json" ^
  -H "X-Session-Id: demo-session-001" ^
  -d "{\"message\":\"再补一段关于 MCP 的说明\"}"
```

如果第二轮回答能承接第一轮语境，说明 `JdbcChatMemory` 已经生效。

## 8. 同一份 ChatMemory 也能给 Responses 用

如果你后面要把基础对话切到 `Responses`，不用重新维护另一套 history：

```java
import io.github.lnyocly.ai4j.platform.openai.response.entity.Response;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import io.github.lnyocly.ai4j.service.IResponsesService;
import io.github.lnyocly.ai4j.service.PlatformType;

IResponsesService responsesService = aiService.getResponsesService(PlatformType.DOUBAO);

ChatMemory memory = chatMemoryFactory.create(sessionId);
memory.addUser("请继续总结刚才的重点");

ResponseRequest request = ResponseRequest.builder()
        .model("doubao-seed-1-8-251228")
        .input(memory.toResponsesInput())
        .build();

Response response = responsesService.create(request);
```

也就是说：

- `ChatMemory` 不是只能给 Chat API 用
- 同一份会话上下文可以同时兼容 `Chat` 和 `Responses`

## 9. 上线建议

- `sessionId` 最好绑定用户或设备，不要纯前端自生成后长期信任
- 如果你只是普通聊天，优先使用 `MessageWindowChatMemoryPolicy`
- 如果会话非常长，再考虑更上层的摘要压缩或迁移到 `AgentMemory`
- `JdbcChatMemory` 解决的是上下文持久化，不替你做会话治理、鉴权和分库分表

## 10. 继续阅读

1. [ChatMemory：基础会话上下文](/docs/ai-basics/chat/chat-memory)
2. [ChatMemory 会话管理模式](/docs/ai-basics/chat/chat-memory-session-management)
3. [Memory 记忆管理与压缩策略](/docs/agent/memory-management)

