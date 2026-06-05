# AI4J App Builder Recipes

Use these patterns as compact starting points. Adjust package names, class names, dependency versions, and provider/model for the target project.

## Plain Java First Chat

Object chain:

```text
Configuration -> AiService -> IChatService -> ChatCompletion -> ChatCompletionResponse
```

Skeleton:

```java
Configuration configuration = new Configuration();
OpenAiConfig openAiConfig = new OpenAiConfig();
openAiConfig.setApiKey(System.getenv("OPENAI_API_KEY"));
configuration.setOpenAiConfig(openAiConfig);

AiService aiService = new AiService(configuration);
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("用一句话介绍 AI4J"))
        .build();

ChatCompletionResponse response = chatService.chatCompletion(request);
String text = response.getChoices().get(0).getMessage().getContent().getText();
```

Implementation notes:

- Import `io.github.lnyocly.ai4j.service.factory.AiService`.
- Import `io.github.lnyocly.ai4j.service.IChatService`.
- Import `io.github.lnyocly.ai4j.service.PlatformType`.
- Import `io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion`, `ChatCompletionResponse`, and `ChatMessage`.
- Use the provider config class that matches the provider.

## Spring Boot Chat Endpoint

Configuration:

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
```

Service pattern:

```java
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

Controller pattern:

```java
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

## Streaming Chat

Use streaming when the UI or caller needs incremental output. Keep the first version simple and log chunks before wiring WebSocket/SSE to the frontend.

```java
SseListener listener = new SseListener() {
    @Override
    protected void send() {
        System.out.print(getCurrStr());
    }
};

chatService.chatCompletionStream(request, listener);
String finalText = listener.getOutput();
```

## Tool / Function Call

Use local function call before MCP when the tool is inside the same app process.

```java
@FunctionCall(name = "queryWeather", description = "查询目标地点的天气预报")
public class QueryWeatherFunction implements Function<QueryWeatherFunction.Request, String> {
    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "城市名")
        private String location;
    }

    @Override
    public String apply(Request request) {
        return request.location + " 天气晴";
    }
}
```

Request pattern:

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("今天北京天气怎么样"))
        .functions("queryWeather")
        .build();
```

## Chat Memory

Use memory when the app needs short multi-turn context but not a full agent runtime.

```java
ChatMemory memory = new InMemoryChatMemory(new MessageWindowChatMemoryPolicy(12));
memory.addSystem("你是一个简洁的 Java 助手");
memory.addUser("请用三点介绍 AI4J");

ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .messages(memory.toChatMessages())
        .build();
```

## RAG Baseline

Use this sequence before optimizing rerank or answer style:

1. ingest one tiny document
2. upsert to one `VectorStore`
3. query with `RagService`
4. assert at least one citation/context result maps back to the source

Prefer clear dataset and metadata fields from the first version. Do not hide ingestion and retrieval in one large controller.

## MCP Integration

Use MCP when the app needs protocolized external tools or a dynamic tool gateway. For first integration:

1. configure one local MCP server/client
2. list tools/resources
3. call one harmless tool
4. only then connect MCP tools to Chat or Agent behavior

If the tool is just local Java code, start with AI4J function call instead of MCP.

## Agent Runtime

Use `ai4j-agent` only when the app needs decisions across steps, tool-loop behavior, memory, trace, subagents, or teams. For the first slice, create one agent with one safe tool and one traceable task. Avoid combining RAG, MCP, and subagents in the first iteration.

## FlowGram

Use FlowGram starter for workflow backend integration, task APIs, and trace bridge. Keep production logic in application services; FlowGram nodes should orchestrate already-testable capabilities rather than becoming the only implementation of business behavior.
