<p align="center"><img src="https://capsule-render.vercel.app/api?type=waving&color=0:6A5ACD,100:2E86C1&height=180&section=header&text=ai4j&fontSize=46&fontColor=ffffff&animation=fadeIn&desc=Java%20AI%20Agentic%20SDK%20for%20JDK%208%2B&descAlignY=68" alt="ai4j banner" /></p>
<p align="center">
  <a href="https://search.maven.org/artifact/io.github.lnyo-cly/ai4j"><img src="https://img.shields.io/maven-central/v/io.github.lnyo-cly/ai4j?color=2E86C1&label=Maven%20Central" alt="Maven Central" /></a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0.txt"><img src="https://img.shields.io/badge/License-Apache%202.0-1F6FEB" alt="License" /></a>
  <img src="https://img.shields.io/badge/JDK-8%2B-2EA043" alt="JDK 8+" />
  <a href="https://lnyo-cly.github.io/ai4j/"><img src="https://img.shields.io/badge/Docs-GitHub%20Pages-0A7EA4" alt="Docs" /></a>
  <a href="https://deepwiki.com/LnYo-Cly/ai4j"><img src="https://deepwiki.com/badge.svg" alt="Ask DeepWiki" /></a>
</p>
<p align="center">
  <img src="https://img.shields.io/badge/Agentic-Enabled-6F42C1" alt="Agentic Enabled" />
  <img src="https://img.shields.io/badge/MCP-Supported-0F766E" alt="MCP Supported" />
  <img src="https://img.shields.io/badge/A2A-Supported-DC2626" alt="A2A Supported" />
  <img src="https://img.shields.io/badge/RAG-Built--in-B45309" alt="RAG Built-in" />
  <img src="https://img.shields.io/badge/CLI%20%2F%20TUI%20%2F%20ACP-Built--in-475569" alt="CLI TUI ACP Built-in" />
</p>

# ai4j

A **JDK 8+** Java AI Agentic SDK: unified access to many model providers with complete agent capabilities built in, so you can quickly build your own agent applications.

[中文 README](README.md)

## Highlights

- **`ai4j-harness`, a unique long-running agent harness**: turns an agent from a one-shot call into an operable long-lived job — tasks can pause, resume, and go through review and acceptance; multiple workers claim work safely without double-execution; every step is auditable.
- **Every agent design you need**: ReAct / CodeAct / Deep Research execution modes, StateGraph orchestration with conditional branches and loops, plus subagent delegation and multi-agent teams.
- **One codebase, 12+ providers**: unified access to OpenAI, Anthropic, DeepSeek, Zhipu, Doubao and more, with full Chat / Responses / Messages protocol support — switching providers is a one-enum change, and multiple API keys can coexist routed by name.
- **Complete RAG built in**: document loading, chunking, vector stores, hybrid retrieval, reranking, and citations — the whole pipeline lives inside the SDK, no external retrieval framework needed.
- **Production-grade governance**: sandboxed execution, permission approvals, hooks, skills, plugin extension, memory compaction strategies, checkpoint resume, and end-to-end tracing — control and observability for long-running tasks come built in.
- **Plugin-oriented extension**: plugins add new capabilities to agents — four extension points: custom **tools**, **slash commands**, **skills**, and **prompts**. Write a plugin to let the agent query your internal ticketing system, add a `/review` command, or ship a domain-specific skill pack. A plugin is just a plain Maven jar — adding the dependency never enables it; explicit opt-in keeps third-party extensions safe and controllable.

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

## Spring Boot

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

See the [Spring Boot quickstart](docs-site/docs/integrations/spring-boot/quickstart.md).

## Coding Agent CLI / TUI / ACP

`ai4j-cli` is a ready-to-use local Coding Agent — not just an API wrapper. Three entry points: interactive CLI, TUI, and ACP (for IDE integration). You can also pull `ai4j-cli` in as a dependency and build your own Coding Agent application on top of it — the TUI supports custom configuration.

**Install** (requires Java 8+; the script pulls `ai4j-cli` from Maven Central and creates the `ai4j` command):

```bash
curl -fsSL https://lnyo-cly.github.io/ai4j/install.sh | sh    # Linux / macOS / Git Bash
irm https://lnyo-cly.github.io/ai4j/install.ps1 | iex         # Windows PowerShell
```

**Three entry points**:

```bash
ai4j code --provider openai --protocol responses --model gpt-5-mini --prompt "Summarize this project's structure"   # one-shot / interactive CLI
ai4j tui  --provider zhipu --protocol chat --model glm-4.7 --base-url https://open.bigmodel.cn/api/coding/paas/v4 --workspace .   # TUI
ai4j acp  --provider openai --protocol responses --model gpt-5-mini --workspace .   # ACP, for IDE integration
```

Capabilities: one-shot and continuous sessions, persistent provider profiles (`~/.ai4j/providers.json`), workspace model overrides, subagents / agent teams, session resume / fork / replay, skills directories, MCP integration, tool approvals, background process management.

Full docs: [Coding Agent CLI doc](docs/readme/en/coding-agent-cli.md) · [Quickstart](docs-site/docs/products/coding-agent/quickstart.md) · [Overview](docs-site/docs/products/coding-agent/overview.md)

## Plugin ecosystem

Plugins extend what agents can do: they inject new **tools**, **slash commands**, **skills**, or **prompts** into the Agent / Coding Agent — for example wrapping an internal system as a tool, adding a custom command to the CLI/TUI, or packaging a team-specific skill.

An AI4J plugin is an ordinary Maven jar: `ServiceLoader` discovery plus `ExtensionRegistry` three-stage gates (discover → enable → exposeTool). Adding a dependency never enables it by itself.

| Plugin | Home | Notes |
|---|---|---|
| `ai4j-plugin-ask-user` | This reactor | Official sample plugin |
| [`ai4j-plugin-dynamic-workflow`](https://github.com/LnYo-Cly/ai4j-plugin-dynamic-workflow) | Standalone repo | Flagship reference: dynamic workflows |
| Community plugins (e.g. `you-search`) | [LnYo-Cly/ai4j-plugins](https://github.com/LnYo-Cly/ai4j-plugins) | Community-maintained, unified Central releases |

- Using plugins: [Plugin packages & gates](docs-site/docs/extending/plugins/plugin-packages.md)
- Writing one: [Plugin author cookbook](docs-site/docs/extending/plugins/plugin-author-cookbook.md)
- Submitting to the community repo: [ai4j-plugins README](https://github.com/LnYo-Cly/ai4j-plugins#readme)

## Supported platforms

OpenAI / OpenAI-compatible, Anthropic, DashScope (Tongyi/Bailian), Doubao (Volcengine Ark/Doubao), DeepSeek, Moonshot, Zhipu, Tencent Hunyuan, Lingyi, Ollama, MiniMax, Baichuan, Suno; Rerank (Jina / Ollama / Doubao); AgentFlow (Dify / Coze / n8n); VectorStore (Pinecone / Qdrant / pgvector / Milvus / Redis). See the [feature-map](docs-site/docs/start-here/feature-map.md) for the full capability list.

## Documentation & Links

ai4j offers two documentation entry points — pick what fits:

- **Official docs site**: https://lnyo-cly.github.io/ai4j/ — bilingual (Chinese & English), from a five-minute first request to deep capability guides, with 50+ source-aligned architecture/sequence diagrams; best for systematic learning and API reference
- **[DeepWiki](https://deepwiki.com/LnYo-Cly/ai4j)** — an AI-powered Q&A tour of this repository; ask "how is feature X implemented" in natural language; auto-refreshes weekly with the repo

Quick links: [First request in five minutes](docs-site/docs/start-here/five-minute-first-chat.md) / [Feature map](docs-site/docs/start-here/feature-map.md) / [Coding Agent CLI / TUI / ACP](docs/readme/en/coding-agent-cli.md) / [A2A Protocol](docs-site/docs/agent/a2a.md) / [CHANGELOG](CHANGELOG.md) / [CONTRIBUTING](CONTRIBUTING.md)

## License

[Apache License 2.0](LICENSE)
