<p align="center"><img src="https://capsule-render.vercel.app/api?type=waving&color=0:6A5ACD,100:2E86C1&height=180&section=header&text=ai4j&fontSize=46&fontColor=ffffff&animation=fadeIn&desc=Java%20AI%20Agentic%20SDK%20for%20JDK%208%2B&descAlignY=68" alt="ai4j banner" /></p>
<p align="center"><a href="https://search.maven.org/artifact/io.github.lnyo-cly/ai4j"><img src="https://img.shields.io/maven-central/v/io.github.lnyo-cly/ai4j?color=2E86C1&label=Maven%20Central" alt="Maven Central" /></a> <a href="https://lnyo-cly.github.io/ai4j/"><img src="https://img.shields.io/badge/Docs-GitHub%20Pages-0A7EA4" alt="Docs" /></a> <a href="https://deepwiki.com/LnYo-Cly/ai4j"><img src="https://deepwiki.com/badge.svg" alt="Ask DeepWiki" /></a> <a href="https://www.apache.org/licenses/LICENSE-2.0.txt"><img src="https://img.shields.io/badge/License-Apache%202.0-1F6FEB" alt="License" /></a> <img src="https://img.shields.io/badge/JDK-8%2B-2EA043" alt="JDK 8+" /> <img src="https://img.shields.io/badge/Agentic-Enabled-6F42C1" alt="Agentic Enabled" /> <img src="https://img.shields.io/badge/MCP-Supported-0F766E" alt="MCP Supported" /> <img src="https://img.shields.io/badge/A2A-Supported-DC2626" alt="A2A Supported" /> <img src="https://img.shields.io/badge/RAG-Built--in-B45309" alt="RAG Built-in" /> <img src="https://img.shields.io/badge/CLI%20%2F%20TUI%20%2F%20ACP-Built--in-475569" alt="CLI TUI ACP Built-in" /></p>

# ai4j

一套面向 **JDK 8+** 的 Java AI Agentic 开发套件：从一次模型调用到可治理的长时运行 Agent，整条链路都在源码内实现，不靠外部框架拼装。

[English README](README-EN.md)

## 为什么是 ai4j

Java 生态里能调大模型的库不少，但**从协议到治理一整条 Agentic 链路都在一个仓里、且兼容 JDK 8** 的不多：

- **三协议原生进出，不做最低公分母**：`Chat` / `Responses` / `Messages` 三套协议各自原生收发——Anthropic 方言零转换零字段丢失，而不是把所有 provider 削成 OpenAI 形状。
- **一个工厂接 12+ 平台**：`Configuration` + `PlatformType` 进 `AiService`，Chat/Responses/Messages/Embedding/媒体/Rerank 十个服务接口按需取；多实例注册表（`AiServiceRegistry`）让同进程持有多个独立 api-key 的 `openai-main`、`doubao-backup` 并存，按 id 路由。
- **能力全是内置实现，不是转调**：RAG（文档加载→切块→混合检索→融合→重排→引用）、MCP（客户端+服务端，Stdio/SSE/Streamable HTTP 三传输）、A2A、向量库适配、Web 搜索增强——都在 `ai4j` 模块内。
- **Agent 运行时到治理一条链**：ReAct/CodeAct/DeepResearch 执行策略、StateGraph 图编排（条件边+回边循环）、memory 分层压缩、checkpoint/replay、span 树 trace、subagent/team、权限/沙箱/Hook/插件门禁——再到 `ai4j-harness` 的持久化受治理执行。
- **到手即用**：Spring Boot starter 一行配置注入 `AiService`；`ai4j-cli` 提供开箱即用的 Coding Agent CLI / TUI / ACP 三入口；插件是普通 Maven jar，ServiceLoader 发现 + 三段门禁。

## 模块组成

| 模块 | 定位 |
|------|------|
| `ai4j` | 核心 SDK：`Configuration`+`AiService` 统一 12+ provider，Chat/Responses/Messages 三协议原生进出；内置 RAG 全链（Loader→Chunker→Embedding→混合检索→RRF/RSF/DBSF 融合→Rerank→引用）、MCP 客户端+服务端三传输、向量库、图像/音视频/实时服务 |
| `ai4j-agent` | Agent 运行时：ReAct/CodeAct/DeepResearch 策略、StateGraph 图编排、memory 压缩投影、checkpoint/replay、RUN>STEP>MODEL/TOOL span 树 trace、subagent/team 编排、权限/沙箱/Hook/Skill/插件 |
| `ai4j-coding` | Coding Agent 运行时：workspace 感知工具、outer loop、会话压缩 |
| `ai4j-cli` | Coding Agent 宿主：交互式 CLI、TUI、ACP（IDE 接入）三入口，provider profile 持久化、session resume/fork/replay |
| `ai4j-extension-api` | 插件扩展契约：manifest、ServiceLoader 发现、`discover→enable→exposeTool` 三段门禁——引入依赖不等于启用 |
| `ai4j-harness` | **持久化、受治理的长时运行 Agent Harness**：借鉴 durable execution 与审批门禁思想，基于 lease + fencing token 驱动多 worker 安全认领，可中断/可恢复/可审计——Task/Execution/Checkpoint/Wait/Wakeup 持久化原语、`提交→评审→验收 Gate` 把"Agent 说做完了"和"系统验收通过了"彻底分开、Task DAG 依赖（拒绝循环）、Fact/Decision/Evidence 台账，适配 File/JDBC 存储，业务上让 Agent 从"一次性调用"变成"可运维的长期任务" |
| `ai4j-plugin-ask-user` | 官方样例插件：host-mediated 用户提问工具 |
| `ai4j-spring-boot-starter` | Spring Boot 自动装配：`ai.openai.api-key` 风格配置直注 `AiService` |
| `ai4j-flowgram-spring-boot-starter` | FlowGram 集成：task API、trace bridge、starter 侧运行时支持 |
| `ai4j-flowgram-demo` | FlowGram starter 集成演示后端 |
| `ai4j-document-tika` | 可选 Apache Tika 文档加载（PDF/Word/Excel/PPT） |
| `ai4j-bom` | 版本对齐 BOM |

另有 `docs-site/`（Docusaurus 双语文档站，含 50+ 张源码对齐的交互式架构图）与 `ai4j-flowgram-webapp-demo/`（Web 演示前端）。

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

`ai4j-cli` 是开箱即用的本地 Coding Agent，不只是 API 封装：交互式 CLI、TUI 界面、ACP（供 IDE 接入）三种入口。

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

AI4J 插件是普通 Maven jar：`ServiceLoader` 发现 + `ExtensionRegistry` 三段门禁（discover → enable → exposeTool）。引入依赖不等于启用。

| 插件 | 归属 | 说明 |
|---|---|---|
| `ai4j-plugin-ask-user` | 本仓 reactor | 官方样例插件 |
| [`ai4j-plugin-dynamic-workflow`](https://github.com/LnYo-Cly/ai4j-plugin-dynamic-workflow) | 独立仓库 | 旗舰参考插件：动态工作流 |
| 社区插件（如 `you-search`） | [LnYo-Cly/ai4j-plugins](https://github.com/LnYo-Cly/ai4j-plugins) | 社区维护、统一 Central 发布 |

- 使用插件：[插件包与门禁](docs-site/docs/extending/plugins/plugin-packages.md)
- 编写插件：[插件作者实战指南](docs-site/docs/extending/plugins/plugin-author-cookbook.md)
- 向社区仓提交插件：见 [ai4j-plugins README](https://github.com/LnYo-Cly/ai4j-plugins#readme)

## 支持的平台

OpenAI / OpenAI-compatible, Anthropic, DashScope（通义/百炼）, Doubao（火山方舟/豆包）, DeepSeek, Moonshot, Zhipu（智谱）, Hunyuan（腾讯混元）, Lingyi（零一万物）, Ollama, MiniMax, Baichuan, Suno；Rerank（Jina / Ollama / Doubao）；AgentFlow（Dify / Coze / n8n）；VectorStore（Pinecone / Qdrant / pgvector / Milvus / Redis）。完整能力列表见 [feature-map](docs-site/docs/start-here/feature-map.md)。

## 链接

- 文档站：https://lnyo-cly.github.io/ai4j/ · [DeepWiki](https://deepwiki.com/LnYo-Cly/ai4j)（AI 问答式仓库导览）
- [5 分钟跑通](docs-site/docs/start-here/five-minute-first-chat.md) / [能力地图](docs-site/docs/start-here/feature-map.md)
- [Coding Agent CLI / TUI / ACP](docs/readme/zh/coding-agent-cli.md) / [A2A Protocol](docs-site/docs/agent/a2a.md)
- [CHANGELOG](CHANGELOG.md) / [CONTRIBUTING](CONTRIBUTING.md)

## License

[Apache License 2.0](LICENSE)
