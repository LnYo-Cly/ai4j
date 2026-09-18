<p align="center"><img src="https://capsule-render.vercel.app/api?type=waving&color=0:6A5ACD,100:2E86C1&height=180&section=header&text=ai4j&fontSize=46&fontColor=ffffff&animation=fadeIn&desc=Java%20AI%20Agentic%20SDK%20for%20JDK%208%2B&descAlignY=68" alt="ai4j banner" /></p>
<p align="center"><a href="https://search.maven.org/artifact/io.github.lnyo-cly/ai4j"><img src="https://img.shields.io/maven-central/v/io.github.lnyo-cly/ai4j?color=2E86C1&label=Maven%20Central" alt="Maven Central" /></a> <a href="https://lnyo-cly.github.io/ai4j/"><img src="https://img.shields.io/badge/Docs-GitHub%20Pages-0A7EA4" alt="Docs" /></a> <a href="https://www.apache.org/licenses/LICENSE-2.0.txt"><img src="https://img.shields.io/badge/License-Apache%202.0-1F6FEB" alt="License" /></a> <img src="https://img.shields.io/badge/JDK-8%2B-2EA043" alt="JDK 8+" /> <img src="https://img.shields.io/badge/Agentic-Enabled-6F42C1" alt="Agentic Enabled" /> <img src="https://img.shields.io/badge/MCP-Supported-0F766E" alt="MCP Supported" /> <img src="https://img.shields.io/badge/A2A-Supported-DC2626" alt="A2A Supported" /> <img src="https://img.shields.io/badge/RAG-Built--in-B45309" alt="RAG Built-in" /> <img src="https://img.shields.io/badge/CLI%20%2F%20TUI%20%2F%20ACP-Built--in-475569" alt="CLI TUI ACP Built-in" /></p>

# ai4j

一款面向 **JDK 8+** 的 Java AI Agentic 开发套件：统一的大模型接入、Tool Calling、MCP、A2A、RAG、Agent Runtime，以及内置的 Coding Agent CLI / TUI / ACP。

[English README](README-EN.md)

## 安装

- Gradle：`implementation 'io.github.lnyo-cly:ai4j:2.4.2'`
- Maven：`<dependency><groupId>io.github.lnyo-cly</groupId><artifactId>ai4j</artifactId><version>2.4.2</version></dependency>`

## 30 秒跑通

设置 `OPENAI_API_KEY` 后，下面代码即可发出第一条请求：

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

## 模块组成

| 模块 | 说明 |
|---|---|
| `ai4j` | 核心 SDK：provider 接入、Chat/Responses、RAG、MCP、向量、图像、音频、realtime |
| `ai4j-agent` | Agent 运行时、workflow、trace、memory、subagent/team 编排 |
| `ai4j-coding` | Coding Agent 运行时、workspace 工具、outer loop、compaction |
| `ai4j-cli` | CLI / TUI / ACP 宿主与会话运行时 |
| `ai4j-extension-api` | 插件扩展契约：manifest、ServiceLoader 发现、enable/expose 门禁 |
| `ai4j-plugin-ask-user` | 官方样例插件（host-mediated 用户提问） |
| `ai4j-harness` | 持久化、受治理的长时运行 Agent Harness runtime |
| `ai4j-spring-boot-starter` | Spring Boot 自动装配 |
| `ai4j-flowgram-spring-boot-starter` | FlowGram 集成、task API、trace bridge |
| `ai4j-flowgram-demo` | FlowGram starter 集成演示后端 |
| `ai4j-document-tika` | 可选 Apache Tika 文档加载（PDF/Word/Excel/PPT） |
| `ai4j-bom` | 版本对齐 BOM |

另有 `docs-site/`（Docusaurus 文档站）与 `ai4j-flowgram-webapp-demo/`（Web 演示前端）。

## Spring Boot 接入

```xml
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-spring-boot-starter</artifactId>
    <version>2.4.2</version>
</dependency>
```

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
```

```java
@Autowired
private AiService aiService;
```

详见 [Spring Boot 快速开始](docs-site/docs/integrations/spring-boot/quickstart.md)。

## Coding Agent CLI / TUI / ACP

`ai4j-cli` 是内置的 Coding Agent 宿主，提供 CLI、TUI 与 ACP 三种入口，内置 workspace 工具、审批与插件扩展。使用方式见 [Coding Agent CLI 文档](docs/readme/zh/coding-agent-cli.md)。

## 插件生态

AI4J 插件是普通 Maven jar：`ServiceLoader` 发现 + `ExtensionRegistry` 三段门禁（discover → enable → exposeTool）。引入依赖不等于启用。

| 插件 | 归属 | 说明 |
|---|---|---|
| `ai4j-plugin-ask-user` | 本仓 reactor | 官方样例插件 |
| [`ai4j-plugin-dynamic-workflow`](https://github.com/LnYo-Cly/ai4j-plugin-dynamic-workflow) | 独立仓库 | 旗舰参考插件：动态工作流 |
| 社区插件（如 `you-search`） | [LnYo-Cly/ai4j-plugins](https://github.com/LnYo-Cly/ai4j-plugins) | 社区维护、统一 Central 发布 |

- 使用插件：[插件包与门禁](docs-site/docs/extending/plugins/plugin-packages.md)
- 编写插件：[插件作者实战指南](docs-site/docs/extending/plugins/plugin-author-cookbook.md)
- 向社区仓提交插件：见 [ai4j-plugins README](https://github.com/LnYo-Cly/ai4j-plugins#readme)

## 链接

- 文档站：https://lnyo-cly.github.io/ai4j/
- [5 分钟跑通](docs-site/docs/start-here/five-minute-first-chat.md) / [能力地图](docs-site/docs/start-here/feature-map.md)
- [Coding Agent CLI / TUI / ACP](docs/readme/zh/coding-agent-cli.md) / [A2A Protocol](docs-site/docs/agent/a2a.md)
- [CHANGELOG](CHANGELOG.md) / [CONTRIBUTING](CONTRIBUTING.md)

## 支持的平台

OpenAI / OpenAI-compatible, Anthropic, DashScope（通义/百炼）, Doubao（火山方舟/豆包）, DeepSeek, Moonshot, Zhipu（智谱）, Hunyuan（腾讯混元）, Lingyi（零一万物）, Ollama, MiniMax, Baichuan, Suno；Rerank（Jina / Ollama / Doubao）；AgentFlow（Dify / Coze / n8n）；VectorStore（Pinecone / Qdrant / pgvector / Milvus / Redis）。完整能力列表见 [feature-map](docs-site/docs/start-here/feature-map.md)。

## License

[Apache License 2.0](LICENSE)
