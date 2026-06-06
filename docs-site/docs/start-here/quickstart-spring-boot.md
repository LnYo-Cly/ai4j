# Quickstart for Spring Boot

这页给 Spring Boot 项目一条最短成功路径。如果你想先同时看普通 Java 和 Spring Boot 的总览版，先读 [5 分钟首聊](/docs/start-here/five-minute-first-chat)。

本页对应的主模块是：

- `ai4j-spring-boot-starter/`

目标不是讲完整 SDK，而是先确认 starter 已经能把 `AiService` 放进你的 Spring 容器，并通过一个 HTTP 接口发出第一条模型请求。

## 1. 这条路径会验证什么

跑通后，你会确认：

- starter 依赖已经进入应用模块
- `ai.*` 配置能被读取
- `AiService` 已经作为 Bean 可注入
- 应用能创建 `IChatService`
- 一个 controller 能返回模型文本

它暂时不解决：

- 多 provider profiles
- Tool / Function Call
- MCP
- RAG
- Agent runtime
- 生产级限流、审计和异常映射

## 2. 最小依赖

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-spring-boot-starter</artifactId>
  <version>2.3.0</version>
</dependency>
```

如果你的项目同时引入 `ai4j-agent`、`ai4j-coding`、FlowGram starter 等模块，再用 `ai4j-bom` 统一版本。

## 3. 最小配置

`application.yml`：

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
```

密钥仍然放在环境变量里：

```powershell
$env:OPENAI_API_KEY="sk-..."
```

不要在 `application.yml` 里写真实 key。提交到仓库的配置只能保留 `${OPENAI_API_KEY}` 这类占位。

## 4. Spring Boot 对象链

```text
ai.* config
    -> auto-configuration
        -> AiService Bean
            -> IChatService
                -> ChatCompletionResponse
```

starter 负责把配置绑定到 AI4J 的 `Configuration`，并把 `AiService` 暴露给 Spring 容器。业务代码只需要注入 `AiService`。

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

## 7. 验证

启动应用后调用：

```bash
curl "http://localhost:8080/ai/chat?q=%E7%94%A8%E4%B8%80%E5%8F%A5%E8%AF%9D%E4%BB%8B%E7%BB%8D%20AI4J"
```

成功时你会看到一段模型返回文本。这个结果说明 starter 接入链路已经成立。

## 8. 常见失败点

| 现象 | 先检查什么 |
| --- | --- |
| `AiService` 无法注入 | starter 依赖是否在应用模块里，Spring Boot 是否扫描到自动配置 |
| API Key 为空 | 当前启动进程是否有 `OPENAI_API_KEY` 环境变量 |
| 鉴权失败 | key、host、模型名是否匹配当前 provider |
| 请求超时 | 网络、代理、provider 可访问性 |
| controller 返回异常栈 | 先在 service 层捕获并记录 provider 错误，再决定是否做统一异常映射 |

## 9. 什么时候不用 starter

下面这些场景可以直接用 `ai4j`：

- 你的项目不是 Spring Boot
- 你在写一个 Java library，不希望依赖 Spring 容器
- 你只想在 CLI、测试或小工具里发一次请求
- 你要完全自己管理 `Configuration` 和 `OkHttpClient`

## 10. 跑通之后

- 想看底层 `Chat` 调用语义：看 [First Chat](/docs/start-here/first-chat)
- 想配置更多 provider：看 [Spring Boot / Configuration Reference](/docs/spring-boot/configuration-reference)
- 想扩展 Bean 或复用业务服务：看 [Spring Boot / Bean Extension](/docs/spring-boot/bean-extension)
- 想做 Tool / Function Call：看 [First Tool Call](/docs/start-here/first-tool-call)
