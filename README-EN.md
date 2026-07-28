<p align="center"><img src="https://capsule-render.vercel.app/api?type=waving&color=0:6A5ACD,100:2E86C1&height=180&section=header&text=ai4j&fontSize=46&fontColor=ffffff&animation=fadeIn&desc=Java%20AI%20Agentic%20SDK%20for%20JDK%208%2B&descAlignY=68" alt="ai4j banner" /></p>
<p align="center"><a href="https://search.maven.org/artifact/io.github.lnyo-cly/ai4j"><img src="https://img.shields.io/maven-central/v/io.github.lnyo-cly/ai4j?color=2E86C1&label=Maven%20Central" alt="Maven Central" /></a> <a href="https://lnyo-cly.github.io/ai4j/"><img src="https://img.shields.io/badge/Docs-GitHub%20Pages-0A7EA4" alt="Docs" /></a> <a href="https://www.apache.org/licenses/LICENSE-2.0.txt"><img src="https://img.shields.io/badge/License-Apache%202.0-1F6FEB" alt="License" /></a> <img src="https://img.shields.io/badge/JDK-8%2B-2EA043" alt="JDK 8+" /> <img src="https://img.shields.io/badge/Agentic-Enabled-6F42C1" alt="Agentic Enabled" /> <img src="https://img.shields.io/badge/MCP-Supported-0F766E" alt="MCP Supported" /> <img src="https://img.shields.io/badge/RAG-Built--in-B45309" alt="RAG Built-in" /> <img src="https://img.shields.io/badge/CLI%20%2F%20TUI%20%2F%20ACP-Built--in-475569" alt="CLI TUI ACP Built-in" /></p>

# ai4j

A **JDK 8+** Java AI Agentic SDK: unified LLM access, Tool Calling, MCP, RAG, an Agent Runtime, and a built-in Coding Agent CLI / TUI / ACP.

[中文 README](README.md)

## Install

- Gradle: `implementation 'io.github.lnyo-cly:ai4j:2.4.2'`
- Maven: `<dependency><groupId>io.github.lnyo-cly</groupId><artifactId>ai4j</artifactId><version>2.4.2</version></dependency>`

## Run your first request in 30 seconds

Set `OPENAI_API_KEY`, then run this code:

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
    public static void main(String[] args) {
        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiKey(System.getenv("OPENAI_API_KEY"));
        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(openAiConfig);
        AiService aiService = new AiService(configuration);
        IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
        ChatCompletion request = ChatCompletion.builder()
                .model("gpt-4o-mini")
                .message(ChatMessage.withUser("Describe ai4j in one sentence"))
                .build();
        ChatCompletionResponse response = chatService.chatCompletion(request);
        System.out.println(response.getChoices().get(0).getMessage().getContent().getText());
    }
}
```

Sample output:
```
ai4j is a JDK 8+ Java AI Agentic SDK covering unified model access, Tool Calling, MCP, and RAG.
```

> Want DashScope / DeepSeek / Ollama instead? Just swap the `PlatformType` and its Config; the rest of the code stays the same.

## Links

- Docs: https://lnyo-cly.github.io/ai4j/
- [First request in five minutes](docs-site/docs/start-here/five-minute-first-chat.md) / [feature map](docs-site/docs/start-here/feature-map.md)
- [Coding Agent CLI / TUI / ACP](docs/readme/en/coding-agent-cli.md)
- [CHANGELOG](CHANGELOG.md) / [CONTRIBUTING](CONTRIBUTING.md)

## Supported platforms

OpenAI / OpenAI-compatible, Anthropic, DashScope (Tongyi/Bailian), Doubao (Volcengine Ark/Doubao), DeepSeek, Moonshot, Zhipu, Tencent Hunyuan, Lingyi, Ollama, MiniMax, Baichuan, Suno; Rerank (Jina / Ollama / Doubao); AgentFlow (Dify / Coze / n8n); VectorStore (Pinecone / Qdrant / pgvector / Milvus / Redis). See the [feature-map](docs-site/docs/start-here/feature-map.md) for the full capability list.

## License

[Apache License 2.0](LICENSE)
