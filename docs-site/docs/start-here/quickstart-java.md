# Quickstart for Java

这页是普通 Java / Maven 项目的最短接入路径。如果你还没跑过任何代码，建议先看一遍 [5 分钟首聊](/docs/start-here/five-minute-first-chat)，再回到本页补普通 Java 细节。

本页对应的主模块是：

- `ai4j/`

目标很明确：不引入 Spring，不引入 Agent，不引入 RAG，先用一个 `main` 或测试方法跑通第一条同步 `Chat` 请求。

## 1. 这条路径会验证什么

跑通后，你至少能确认：

- Maven 依赖声明正确
- API Key 从环境变量读取
- `ChatClient` 可以创建底层 provider 配置和 chat service
- 第一条同步 `Chat` 请求可以直接返回文本
- 需要进阶时，可以展开到底层 `Configuration` / `AiService` / `IChatService`

它暂时不解决：

- Spring Boot 自动配置
- Tool / Function Call
- `Responses`
- MCP
- Agent runtime
- RAG / VectorStore

## 2. 最小依赖

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j</artifactId>
  <version>2.3.0</version>
</dependency>
```

如果项目会同时引入多个 AI4J 模块，再使用 `ai4j-bom` 做版本对齐；第一次首聊不需要先上 BOM。

## 3. 设置环境变量

PowerShell:

```powershell
$env:OPENAI_API_KEY="sk-..."
```

Bash:

```bash
export OPENAI_API_KEY="sk-..."
```

不要把密钥写进 Java 文件、Git 跟踪的配置文件或 README 示例。

## 4. 首聊最短链路

普通 Java 首聊先用这一条链：

```text
ChatClient -> assistant text
```

`ChatClient` 内部仍然走完整对象链：

```text
Configuration
    -> AiService
        -> IChatService
            -> ChatCompletion
                -> ChatCompletionResponse
```

`Configuration` 放 provider 配置；`AiService` 是服务工厂；`IChatService` 负责 `Chat` 请求；`ChatCompletionResponse` 是返回对象。

## 5. 可复制代码

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

## 6. 何时展开完整对象链

如果你只是验证第一条请求，`ChatClient` 足够。下面这些情况建议展开完整对象链：

- 需要自定义 `OkHttpClient`
- 需要流式输出
- 需要多模态、Tool / Function Call、MCP、RAG
- 需要读取 `ChatCompletionResponse` 里的 usage、finish reason 或 tool calls
- 需要在一个应用内管理多个 provider profile

`ChatClient` 不会挡住这些能力。你可以先从短路径拿到底层对象：

```java
ChatClient client = ChatClient.openAi(System.getenv("OPENAI_API_KEY"));
IChatService chatService = client.getChatService();
AiService aiService = client.getAiService();
Configuration configuration = client.getConfiguration();
```

## 7. 关于 `OkHttpClient`

AI4J 的 `Configuration` 默认已经带有 `OkHttpClient`，普通 Java 首聊不需要先手动创建客户端。

只有在下面这些情况，才需要调用 `configuration.setOkHttpClient(...)`：

- 要配置代理
- 要调整超时
- 要加拦截器
- 要复用应用已有的连接池或网络栈

第一次首聊建议先用默认客户端，减少变量。

## 8. 本页示例的回归合同

本页的普通 Java 首聊对象链由本仓库测试保护：

```bash
mvn -pl ai4j -Dtest=ChatClientTest,FirstChatCopyableCodeTest,ConfigurationTest -DskipTests=false test
```

这不是 live provider 测试。它用本地 HTTP double 验证 `ChatClient` 首聊门面、请求路径、鉴权头和 `ChatCompletionResponse` 文本读取，同时用 `ConfigurationTest` 锁住默认 `OkHttpClient` 行为。真实模型质量、额度、网络和 provider 可用性仍然属于你运行首聊时的外部条件。

## 9. 换 provider 时改哪里

| 你要换什么 | 需要改的地方 |
| --- | --- |
| OpenAI-compatible endpoint | `OpenAiConfig#setApiHost(...)`、模型名、API Key |
| DeepSeek | 使用 `DeepSeekConfig`，并改为 `PlatformType.DEEPSEEK` |
| Moonshot | 使用 `MoonshotConfig`，并改为 `PlatformType.MOONSHOT` |
| DashScope | 使用 `DashScopeConfig`，并改为 `PlatformType.DASHSCOPE` |
| Ollama | 使用 `OllamaConfig`，并改为 `PlatformType.OLLAMA` |

原则是：provider config class 和 `PlatformType` 要匹配。当前 `ChatClient.openAi(...)` 面向 OpenAI-compatible 首聊；换到其他 provider 时，使用完整对象链更清楚。

## 10. 成功标准

| 检查点 | 成功说明 |
| --- | --- |
| 项目能编译 | 依赖和 import 正确 |
| 没有 `Missing OPENAI_API_KEY` | 当前运行环境能读到密钥 |
| 没有 provider 鉴权错误 | API Key、host、模型名基本有效 |
| 输出非空文本 | 第一条同步 `Chat` 链路已经成立 |

如果只想先做本地编译检查，不想调用真实 provider，可以先写一个单元测试验证对象构造和依赖解析；真实模型请求仍然需要有效密钥和网络。

## 11. 跑通之后

- 想接入 Spring Boot：看 [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
- 想理解 `Chat` 细节：看 [First Chat](/docs/start-here/first-chat)
- 想让模型调用本地函数：看 [First Tool Call](/docs/start-here/first-tool-call)
- 想继续看完整能力：看 [Feature Map](/docs/start-here/feature-map)
