<p align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=0:6A5ACD,100:2E86C1&height=180&section=header&text=ai4j&fontSize=46&fontColor=ffffff&animation=fadeIn&desc=Java%20AI%20Agentic%20SDK%20for%20JDK%208%2B&descAlignY=68" alt="ai4j banner" />
</p>

<p align="center">
  <a href="https://search.maven.org/artifact/io.github.lnyo-cly/ai4j">
    <img src="https://img.shields.io/maven-central/v/io.github.lnyo-cly/ai4j?color=2E86C1&label=Maven%20Central" alt="Maven Central" />
  </a>
  <a href="https://lnyo-cly.github.io/ai4j/">
    <img src="https://img.shields.io/badge/Docs-GitHub%20Pages-0A7EA4" alt="Docs" />
  </a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0.txt">
    <img src="https://img.shields.io/badge/License-Apache%202.0-1F6FEB" alt="License" />
  </a>
  <img src="https://img.shields.io/badge/JDK-8%2B-2EA043" alt="JDK 8+" />
  <img src="https://img.shields.io/badge/Agentic-Enabled-6F42C1" alt="Agentic Enabled" />
  <img src="https://img.shields.io/badge/MCP-Supported-0F766E" alt="MCP Supported" />
  <img src="https://img.shields.io/badge/RAG-Built--in-B45309" alt="RAG Built-in" />
  <img src="https://img.shields.io/badge/CLI%20%2F%20TUI%20%2F%20ACP-Built--in-475569" alt="CLI TUI ACP Built-in" />
</p>

# ai4j

一款面向 **JDK 8+** 的 Java AI Agentic 开发套件：统一的大模型接入、Tool Calling、MCP、RAG、Agent Runtime，以及内置的 Coding Agent CLI / TUI / ACP。从基础模型调用到完整的 agentic 应用，一套 SDK 覆盖全链路。

[English README](README-EN.md)

## 安装

Gradle：

```gradle
implementation 'io.github.lnyo-cly:ai4j:2.4.2'
```

Maven：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j</artifactId>
  <version>2.4.2</version>
</dependency>
```

## 30 秒跑通

设置环境变量后，下面这段代码即可发出第一条请求：

```bash
export OPENAI_API_KEY=sk-...
```

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
                .message(ChatMessage.withUser("用一句话介绍 ai4j"))
                .build();

        ChatCompletionResponse response = chatService.chatCompletion(request);
        System.out.println(response.getChoices().get(0).getMessage().getContent().getText());
    }
}
```

输出示例：

```
ai4j 是一款面向 JDK 8+ 的 Java AI Agentic 开发套件，覆盖统一模型接入、Tool Calling、MCP 与 RAG。
```

> 换成 DashScope / DeepSeek / Ollama 等其他平台？只需替换 `PlatformType` 与对应 Config，其余代码不变。

## 链接

- 文档站：https://lnyo-cly.github.io/ai4j/
- 5 分钟跑通第一条请求：[five-minute-first-chat](docs-site/docs/start-here/five-minute-first-chat.md)
- 能力地图（28 项特性详解）：[feature-map](docs-site/docs/start-here/feature-map.md)
- Coding Agent CLI / TUI / ACP：[coding-agent-cli](docs/readme/zh/coding-agent-cli.md)
- 更新日志：[CHANGELOG](CHANGELOG.md)
- 贡献指南：[CONTRIBUTING](CONTRIBUTING.md)

## 支持的平台

OpenAI / OpenAI-compatible, Anthropic, DashScope（通义/百炼）, Doubao（火山方舟/豆包）, DeepSeek, Moonshot, Zhipu（智谱）, Hunyuan（腾讯混元）, Lingyi（零一万物）, Ollama, MiniMax, Baichuan, Suno；Rerank（Jina / Ollama / Doubao）；AgentFlow（Dify / Coze / n8n）；VectorStore（Pinecone / Qdrant / pgvector / Milvus / Redis）。完整能力列表见 [feature-map](docs-site/docs/start-here/feature-map.md)。

## License

[Apache License 2.0](LICENSE)
