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

一套面向 **JDK 8+** 的 Java AI Agentic 开发套件：统一接入多种大模型服务，内置完整的 Agent 能力，帮你快速开发自己专属的 Agent 应用。

[English README](README-EN.md)

## 核心优势

- **一套代码接 12+ 平台**：OpenAI、Anthropic、DeepSeek、智谱、豆包、Ollama 等统一接入，Chat / Responses / Messages 三套协议完整支持，Function Calling、SSE 流式开箱就有；Embedding、Rerank、图像/音频/视频/音乐生成、实时对话等十类服务接口一个工厂按需取用；切换平台只改一个枚举值，多组 API Key 还能并存、按名字路由。
- **Agent 玩法全覆盖**：ReAct、CodeAct、Deep Research 三种执行模式，StateGraph 图编排支持条件分支与循环，subagent 委派和多智能体团队协作也有——从简单问答到多步研究型 Agent 都有现成骨架。
- **独创 `ai4j-harness` 长时运行 Harness**：让 Agent 从"一次性调用"变成可暂停、可恢复、可验收的长期任务（[详见下方专节](#ai4j-harness把-agent-变成可运维的长期任务)）。
- **内置完整 RAG**：文档加载（可选 Tika 解析 PDF/Word/Excel）、切块、五大向量库适配（Pinecone / Qdrant / pgvector / Milvus / Redis）、混合检索、重排、引用标注，整条链路都在 SDK 内实现，不需要外挂检索框架。
- **生态互联**：MCP 客户端 + 服务端（Stdio / SSE / Streamable HTTP 三种传输）既能调别人的工具，也能把自己的能力暴露出去；A2A 协议让 Agent 互相协作；还能反向接入 Dify / Coze / n8n 上已有的 AgentFlow 编排，另有联网搜索增强。
- **可控可观测**：沙箱执行、权限审批、Hook、Skill、记忆压缩策略、checkpoint 断点续跑、全链路调用追踪、会话回放——长任务的可控与可观测都是内置的。
- **开箱即用**：Spring Boot starter 一行配置注入 `AiService`；自带 Coding Agent 的 CLI / TUI / ACP 三入口；插件化扩展覆盖 Tool / Command / Skill / Prompt 四类扩展点，引入依赖不等于启用。

此外还有 RAG 在线评估（LLM-as-judge）、提示词缓存、Agent Blueprint 声明式装配、FlowGram 可视化工作流集成等——完整能力清单见[能力地图](docs-site/docs/getting-started/feature-map.md)。

## ai4j-harness：把 Agent 变成可运维的长期任务

普通的 Agent 调用是"跑一次就结束"；`ai4j-harness` 让它变成一个**持久化、受治理、可恢复**的长期任务：

- **能中断也能续跑**：任务状态持久化到 File/JDBC 存储，进程重启、机器换台都能从断点继续
- **多实例安全并行**：多个 worker 通过租约认领任务，不会重复执行；worker 挂了任务自动回到可认领状态
- **"做完了"不等于"验收过了"**：Agent 提交结果后要过评审和验收门禁才算完成，不通过可以打回重做
- **全程可审计**：任务依赖、事实、决策、证据都有台账记录，出了什么问题可以回放追溯

适合审批流、长链路业务编排、需要人工介入确认的自动化等场景。详见 [Harness 运行时](docs-site/docs/agent/harness-runtime.md)。

## 安装

- Gradle：`implementation 'io.github.lnyo-cly:ai4j:2.5.0'`
- Maven：`<dependency><groupId>io.github.lnyo-cly</groupId><artifactId>ai4j</artifactId><version>2.5.0</version></dependency>`

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
ai4j 是一套面向 JDK 8+ 的 Java AI Agentic 开发套件，统一接入多种大模型服务，内置完整的 Agent 能力。
```

> 换成 DashScope / DeepSeek / Ollama 等其他平台？只需替换 `PlatformType` 与对应 Config，其余代码不变。

## Spring Boot 接入

```xml
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-spring-boot-starter</artifactId>
    <version>2.5.0</version>
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

`ai4j-cli` 是开箱即用的本地 Coding Agent，不只是 API 封装：交互式 CLI、TUI 界面、ACP（供 IDE 接入）三种入口。开发者也可以把 `ai4j-cli` 作为依赖引入自己的项目，直接在其上开发自己的 Coding Agent 应用——TUI 界面支持自定义配置。

**安装**（需 Java 8+，脚本从 Maven Central 拉取 `ai4j-cli` 并生成 `ai4j` 命令）：

```bash
curl -fsSL https://lnyo-cly.github.io/ai4j/install.sh | sh    # Linux / macOS / Git Bash
irm https://lnyo-cly.github.io/ai4j/install.ps1 | iex         # Windows PowerShell
```

**三种入口**：

```bash
ai4j code --provider openai --protocol responses --model gpt-5-mini --prompt "总结这个项目的结构"   # one-shot / 交互式 CLI
ai4j tui  --provider zhipu --protocol chat --model glm-4.7 --base-url https://open.bigmodel.cn/api/coding/paas/v4 --workspace .   # TUI
ai4j acp  --provider openai --protocol responses --model gpt-5-mini --workspace .   # ACP，供 IDE 接入
```

能力：one-shot 与持续会话、provider profile 持久化（`~/.ai4j/providers.json`）、workspace model override、subagent / agent teams、session resume / fork / replay、skills 目录、MCP 对接、工具审批、后台 process 管理。

完整说明：[Coding Agent CLI 文档](docs/readme/zh/coding-agent-cli.md) · [快速开始](docs-site/docs/products/coding-agent/quickstart.md) · [总览](docs-site/docs/products/coding-agent/overview.md)

## 插件生态

插件用来给 Agent 扩展能力：可以向 Agent / Coding Agent 注入新的**工具**（Tool）、**斜杠命令**（Command）、**Skill**、**Prompt** 四类内容——比如对接内部系统做成工具、给 CLI/TUI 加自定义命令、封装团队专属的 Skill 包。

插件就是普通 Maven jar：`ServiceLoader` 发现 + `ExtensionRegistry` 三段门禁（discover → enable → exposeTool）。引入依赖不等于启用。

| 插件 | 归属 | 说明 |
|---|---|---|
| `ai4j-plugin-ask-user` | 本仓 reactor | 官方样例插件 |
| [`ai4j-plugin-dynamic-workflow`](https://github.com/LnYo-Cly/ai4j-plugin-dynamic-workflow) | 独立仓库 | 旗舰参考插件：动态工作流 |
| 社区插件（如 `you-search`） | [LnYo-Cly/ai4j-plugins](https://github.com/LnYo-Cly/ai4j-plugins) | 社区维护、统一 Central 发布 |

- 使用插件：[插件包与门禁](docs-site/docs/extending/plugins/plugin-packages.md)
- 编写插件：[插件作者实战指南](docs-site/docs/extending/plugins/plugin-author-cookbook.md)
- 向社区仓提交插件：见 [ai4j-plugins README](https://github.com/LnYo-Cly/ai4j-plugins#readme)

## 支持的平台

OpenAI / OpenAI-compatible, Anthropic, DashScope（通义/百炼）, Doubao（火山方舟/豆包）, DeepSeek, Moonshot, Zhipu（智谱）, Hunyuan（腾讯混元）, Lingyi（零一万物）, Ollama, MiniMax, Baichuan, Suno；Rerank（Jina / Ollama / Doubao）；AgentFlow（Dify / Coze / n8n）；VectorStore（Pinecone / Qdrant / pgvector / Milvus / Redis）。完整能力列表见 [feature-map](docs-site/docs/getting-started/feature-map.md)。

## 文档与链接

ai4j 提供两个文档入口，按需取用：

- **[官方文档站](https://lnyo-cly.github.io/ai4j/)**：中英双语，从 5 分钟跑通到各能力详解，配有大量与源码对齐的架构/时序图，适合系统学习和查 API 用法
- **[DeepWiki](https://deepwiki.com/LnYo-Cly/ai4j)**：AI 问答式仓库导览，适合直接用自然语言提问——比如"某个功能是怎么实现的"、"如何用 ai4j-harness 开发某场景的专属 Agent"，每周自动跟随仓库刷新

快速入口：

- **新手起步**：[Java 快速开始](docs-site/docs/getting-started/quickstart-java.md) · [路径选择](docs-site/docs/getting-started/choose-your-path.md) · [能力地图](docs-site/docs/getting-started/feature-map.md) · [FAQ](docs-site/docs/reference/faq.md)
- **模型调用**：[Chat](docs-site/docs/capabilities/models/chat.md) · [Responses](docs-site/docs/capabilities/models/responses.md) · [Messages](docs-site/docs/capabilities/models/messages.md) · [流式](docs-site/docs/capabilities/models/streaming.md) · [多模态](docs-site/docs/capabilities/models/multimodal.md) · [Function Calling](docs-site/docs/capabilities/tools/function-calling.md)
- **检索与互联**：[RAG](docs-site/docs/capabilities/rag/overview.md) · [MCP](docs-site/docs/capabilities/mcp/overview.md) · [Skills](docs-site/docs/capabilities/skills/overview.md) · [A2A](docs-site/docs/agent/observability/a2a.md)
- **Agent**：[Agent 总览](docs-site/docs/agent/overview.md) · [Agent 快速开始](docs-site/docs/agent/quickstart.md) · [Harness 运行时](docs-site/docs/agent/harness-runtime.md) · [多智能体团队](docs-site/docs/agent/orchestration/agent-teams.md)
- **产品**：[Coding Agent CLI / TUI / ACP](docs/readme/zh/coding-agent-cli.md) · [FlowGram](docs-site/docs/products/flowgram/overview.md) · [插件开发指南](docs-site/docs/extending/plugins/plugin-author-cookbook.md)
- **参考**：[排障指南](docs-site/docs/production/troubleshooting.md) · [生产检查清单](docs-site/docs/production/production-checklist.md) · [选型对比](docs-site/docs/reference/about/comparison.md) · [Releases](https://github.com/LnYo-Cly/ai4j/releases) · [CONTRIBUTING](CONTRIBUTING.md)

## License

[Apache License 2.0](LICENSE)
