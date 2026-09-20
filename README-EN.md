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

- **One codebase, 12+ providers**: unified access to OpenAI, Anthropic, DeepSeek, Zhipu, Doubao, Ollama and more, with full Chat / Responses / Messages protocol support — function calling and SSE streaming included; ten service interfaces (Embedding, Rerank, image/audio/video/music generation, realtime, and more) are served from one factory; switching providers is a one-enum change, and multiple API keys can coexist routed by name.
- **Every agent design covered**: ReAct, CodeAct, and Deep Research execution modes, StateGraph orchestration with conditional branches and loops, plus subagent delegation and multi-agent teams — ready-made skeletons from simple Q&A to multi-step research agents.
- **`ai4j-harness`, a unique long-running agent harness**: turns a one-shot agent call into a pausable, resumable, acceptance-gated long-lived job ([see the dedicated section](#ai4j-harness-turn-agents-into-operable-long-lived-jobs)).
- **Complete RAG built in**: document loading (optional Tika for PDF/Word/Excel), chunking, five vector-store adapters (Pinecone / Qdrant / pgvector / Milvus / Redis), hybrid retrieval, reranking, and citations — the whole pipeline lives inside the SDK, no external retrieval stack needed.
- **Open interop**: MCP client *and* server over Stdio / SSE / Streamable HTTP — call others' tools or expose your own; the A2A protocol lets agents collaborate; you can also call into existing Dify / Coze / n8n AgentFlow orchestrations, with web-search enhancement on top.
- **Governed and observable**: sandboxed execution, permission approvals, hooks, skills, memory compaction, checkpoint resume, end-to-end tracing, and session replay — control and observability for long tasks come built in.
- **Ready out of the box**: the Spring Boot starter injects `AiService` with one config key; a Coding Agent with CLI / TUI / ACP entries ships in the box; plugins cover Tool / Command / Skill / Prompt extension points — adding a dependency never enables it.

Plus Skills resource packs, session replay, FlowGram visual-workflow integration, and more — see the [feature map](docs-site/docs/getting-started/feature-map.md) for the full list.

## ai4j-harness: turn agents into operable long-lived jobs

A normal agent call ends when it returns; `ai4j-harness` makes it a **durable, governed, resumable** long-running task:

- **Interruptible and resumable**: task state persists to File/JDBC stores — restart the process or move to another machine and continue from the checkpoint
- **Safe multi-worker parallelism**: workers claim tasks through leases with no double-execution; a dead worker's task goes back to the claimable pool
- **"Done" is not "accepted"**: an agent's submission must pass review and acceptance gates before it counts — rejected work goes back for another round
- **Fully auditable**: task dependencies, facts, decisions, and evidence are all recorded in a ledger you can replay and trace

A good fit for approval workflows, long business-process orchestration, and automation that needs human checkpoints. See [Harness runtime](docs-site/docs/agent/harness-runtime.md).

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

OpenAI / OpenAI-compatible, Anthropic, DashScope (Tongyi/Bailian), Doubao (Volcengine Ark/Doubao), DeepSeek, Moonshot, Zhipu, Tencent Hunyuan, Lingyi, Ollama, MiniMax, Baichuan, Suno; Rerank (Jina / Ollama / Doubao); AgentFlow (Dify / Coze / n8n); VectorStore (Pinecone / Qdrant / pgvector / Milvus / Redis). See the [feature-map](docs-site/docs/getting-started/feature-map.md) for the full capability list.

## Documentation & Links

ai4j offers two documentation entry points — pick what fits:

- **[Official docs site](https://lnyo-cly.github.io/ai4j/)**: bilingual (Chinese & English), from a five-minute first request to deep capability guides, with extensive source-aligned architecture/sequence diagrams; best for systematic learning and API reference
- **[DeepWiki](https://deepwiki.com/LnYo-Cly/ai4j)**: an AI-powered Q&A tour of this repository — ask in natural language, e.g. "how is feature X implemented" or "how do I build a dedicated agent for my scenario with ai4j-harness"; auto-refreshes weekly with the repo

Quick links:

- **Getting started**: [Java quickstart](docs-site/docs/getting-started/quickstart-java.md) · [Choose your path](docs-site/docs/getting-started/choose-your-path.md) · [Feature map](docs-site/docs/getting-started/feature-map.md) · [FAQ](docs-site/docs/reference/faq.md)
- **Model access**: [Chat](docs-site/docs/capabilities/models/chat.md) · [Responses](docs-site/docs/capabilities/models/responses.md) · [Messages](docs-site/docs/capabilities/models/messages.md) · [Streaming](docs-site/docs/capabilities/models/streaming.md) · [Multimodal](docs-site/docs/capabilities/models/multimodal.md) · [Function Calling](docs-site/docs/capabilities/tools/function-calling.md)
- **Retrieval & interop**: [RAG](docs-site/docs/capabilities/rag/overview.md) · [MCP](docs-site/docs/capabilities/mcp/overview.md) · [Skills](docs-site/docs/capabilities/skills/overview.md) · [A2A](docs-site/docs/agent/observability/a2a.md)
- **Agent**: [Agent overview](docs-site/docs/agent/overview.md) · [Agent quickstart](docs-site/docs/agent/quickstart.md) · [Harness runtime](docs-site/docs/agent/harness-runtime.md) · [Agent teams](docs-site/docs/agent/orchestration/agent-teams.md)
- **Products**: [Coding Agent CLI / TUI / ACP](docs/readme/en/coding-agent-cli.md) · [FlowGram](docs-site/docs/products/flowgram/overview.md) · [Plugin author guide](docs-site/docs/extending/plugins/plugin-author-cookbook.md)
- **Reference**: [Troubleshooting](docs-site/docs/production/troubleshooting.md) · [Comparison](docs-site/docs/reference/about/comparison.md) · [Releases](https://github.com/LnYo-Cly/ai4j/releases) · [CONTRIBUTING](CONTRIBUTING.md)

## License

[Apache License 2.0](LICENSE)
