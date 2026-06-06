---
sidebar_position: 4
sidebar_label: 5 分钟首聊
title: 5 分钟跑通第一条 AI 请求
slug: /start-here/five-minute-first-chat
---

# 5 分钟跑通第一条 AI 请求

这页只做一件事：让你在最短路径里确认 AI4J 已经能从你的 Java 应用发出第一条模型请求，并读到返回文本。

如果你还不确定要做 RAG、MCP、Agent 还是 FlowGram，先不要从那些能力开始。先跑通 `Chat`，再升级。

## 你会得到什么

| 你现在的项目 | 走哪条路径 | 跑通后说明什么 |
| --- | --- | --- |
| 普通 Java / Maven 项目 | 引入 `ai4j`，使用 `ChatClient.openAi(...)` | 依赖、密钥、provider 配置、模型请求、响应文本都成立 |
| Spring Boot 项目 | 引入 `ai4j-spring-boot-starter`，通过配置注入 `AiService` | starter、配置绑定、Bean 注入和 HTTP 入口都成立 |
| 想让 agent 帮你接入 | 安装 `$ai4j-app-builder` | agent 会按项目结构选择依赖、写最小代码，并给验证命令 |

## 前置条件

- JDK `8+`
- Maven 项目
- 一个可用 provider 的 API Key
- 不把 API Key 写进源码、README、截图或 Git 跟踪配置

本页默认用 OpenAI-compatible Chat 举例。如果你使用 DeepSeek、Moonshot、DashScope、Ollama 等 provider，主链路相同，只需要换对应 config 和 `PlatformType`。

## 1. 设置密钥

PowerShell:

```powershell
$env:OPENAI_API_KEY="sk-..."
```

Bash:

```bash
export OPENAI_API_KEY="sk-..."
```

代码里只读取 `System.getenv("OPENAI_API_KEY")`。如果需要在 Spring Boot 里配置，也使用 `${OPENAI_API_KEY}` 占位。

## 2. 普通 Java：最小依赖

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j</artifactId>
  <version>2.3.0</version>
</dependency>
```

## 3. 普通 Java：第一段可运行代码

```java
import io.github.lnyocly.ai4j.service.ChatClient;

public class Ai4jFirstChat {
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Missing OPENAI_API_KEY");
        }

        String text = ChatClient.openAi(apiKey)
                .chat("gpt-4o-mini", "用一句话介绍 AI4J");
        System.out.println(text);
    }
}
```

`ChatClient` 是首聊门面，不是第二套 SDK。它内部仍然复用这条对象链：

```text
Configuration -> AiService -> IChatService -> ChatCompletion -> ChatCompletionResponse
```

跑通第一条请求后，如果要配置代理、复用自定义 `OkHttpClient`、做流式、多模态、Tool、MCP、RAG，
再展开使用 `Configuration`、`AiService`、`IChatService` 和 `ChatCompletion`。`ChatClient` 也保留
`getConfiguration()`、`getAiService()`、`getChatService()`，方便从短路径升级到完整对象链。

本段普通 Java 首聊链路由仓库内的本地回归保护：

```bash
mvn -pl ai4j -Dtest=ChatClientTest,FirstChatCopyableCodeTest,ConfigurationTest -DskipTests=false test
```

这条命令不读取真实 API Key，也不访问真实 provider；它验证 `ChatClient` 首聊门面、默认 `OkHttpClient`、`AiService` 创建、OpenAI-compatible 请求路径、鉴权头和返回文本读取链路。

## 4. Spring Boot：最小依赖

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-spring-boot-starter</artifactId>
  <version>2.3.0</version>
</dependency>
```

## 5. Spring Boot：最小配置

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
```

如果你的网络环境需要代理，再按 Spring Boot starter 的配置说明补 `ai.okhttp.*`。

## 6. Spring Boot：一个最小接口

Service:

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

    public String chatOnce(String input) throws Exception {
        IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
        ChatCompletion request = ChatCompletion.builder()
                .model("gpt-4o-mini")
                .message(ChatMessage.withUser(input))
                .build();
        ChatCompletionResponse response = chatService.chatCompletion(request);
        return response.getChoices().get(0).getMessage().getContent().getText();
    }
}
```

Controller:

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

验证：

```bash
curl "http://localhost:8080/ai/chat?q=%E7%94%A8%E4%B8%80%E5%8F%A5%E8%AF%9D%E4%BB%8B%E7%BB%8D%20AI4J"
```

如果返回非空文本，说明 starter、配置绑定、Bean 注入和第一条模型请求都已打通。

本段 Spring Boot 首聊链路由仓库内的本地回归保护：

```bash
mvn -pl ai4j-spring-boot-starter -Dtest=AiServiceFirstChatAutoConfigurationTest -DskipTests=false test
```

这条命令验证 `ai.openai.*` 配置能进入 `AiService`，并能创建 `PlatformType.OPENAI` 对应的 `IChatService`。

## 7. 让 agent 帮你接入

如果你使用支持 Skills 的 agent 工具，可以安装本仓库的用户侧 Skill：

```bash
npx skills add LnYo-Cly/ai4j --skill ai4j-app-builder
```

安装后，把你的目标说清楚即可：

```text
Use $ai4j-app-builder to add AI4J first chat to my Java 8 Maven project.
Use env vars for secrets, choose the smallest dependency, create a runnable first-chat slice, and give me the verification command.
```

Spring Boot 项目可以这样说：

```text
Use $ai4j-app-builder to add AI4J to my Spring Boot app, create a /ai/chat endpoint, and verify it without hardcoding secrets.
```

这个 Skill 的作用不是替代 AI4J 文档，而是让 agent 按你的项目结构完成依赖选择、配置、最小代码和验证命令。

## 8. 成功标准

| 检查点 | 成功时应该看到什么 |
| --- | --- |
| Maven 依赖 | 项目能解析 `ai4j` 或 `ai4j-spring-boot-starter` |
| 密钥读取 | 代码只从环境变量读取 API Key |
| 服务创建 | `ChatClient` 能创建底层 `AiService` 和 `IChatService` |
| 请求发送 | provider 返回 `ChatCompletionResponse` |
| 文本读取 | `ChatClient#chat(...)` 返回非空文本；进阶时也能从 `ChatCompletionResponse` 读取文本 |

## 9. 常见问题

| 现象 | 先检查什么 |
| --- | --- |
| `Missing OPENAI_API_KEY` | 当前终端是否设置了环境变量，IDE 运行配置是否继承了环境变量 |
| `401` 或鉴权失败 | API Key 是否有效，provider 是否要求不同的 host 或模型名 |
| 请求超时 | 网络、代理、provider 可访问性；需要时再配置自定义 `OkHttpClient` 或 starter 的 `ai.okhttp.*` |
| 返回结构为空 | 模型名、provider 响应格式、是否走了兼容 endpoint |
| Spring Boot 注入失败 | starter 依赖是否在当前应用模块里，配置前缀是否是 `ai.openai.*` |

## 10. 下一步

- 想继续理解普通 Java 主线：看 [Quickstart for Java](/docs/start-here/quickstart-java)
- 想继续理解 Spring Boot 接入：看 [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
- 想了解 `Chat` 和 `Responses` 怎么选：看 [First Chat](/docs/start-here/first-chat)
- 想让模型调用本地函数：看 [First Tool Call](/docs/start-here/first-tool-call)
- 想判断当前能力边界：看 [Feature Map](/docs/start-here/feature-map)
