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

- **独创 `ai4j-harness` 长时运行 Harness**：让 Agent 从"一次性调用"变成"可运维的长期任务"（[详见下方专节](#ai4j-harness把-agent-变成可运维的长期任务)）。
- **各种 Agent 设计都支持**：ReAct / CodeAct / Deep Research 三种执行模式，StateGraph 图编排支持条件分支与循环，还有 subagent 委派和 agent team 多智能体协作。
- **一套代码接 12+ 平台**：OpenAI、Anthropic、DeepSeek、智谱、豆包等统一接入，Chat / Responses / Messages 三套协议各自完整支持，切换平台只需换一个枚举值；多组 API Key 可以并存，按名字路由。
- **内置完整 RAG**：文档加载、切块、向量入库、混合检索、重排、引用标注，整条链路都在 SDK 内实现，不需要外挂检索框架。
- **生产可用的治理能力**：沙箱执行、权限审批、Hook、Skill、插件化扩展、记忆压缩策略、checkpoint 断点续跑、全链路调用追踪——长任务的可控与可观测都是内置的。
- **插件化扩展**：插件用于给 Agent 增加新能力——自定义工具、斜杠命令、Skill、Prompt 四类扩展点。比如写一个插件让 Agent 能查你们内部的工单系统、加一个 `/review` 命令、或注入一套领域 Skill。插件就是普通 Maven jar，引入依赖不会自动启用，需要显式开启——三方扩展安全可控。

## ai4j-harness：把 Agent 变成可运维的长期任务

普通的 Agent 调用是"跑一次就结束"；`ai4j-harness` 让它变成一个**持久化、受治理、可恢复**的长期任务：

- **能中断也能续跑**：任务状态持久化到 File/JDBC 存储，进程重启、机器换台都能从断点继续
- **多实例安全并行**：多个 worker 通过租约认领任务，不会重复执行；worker 挂了任务自动回到可认领状态
- **"做完了"不等于"验收过了"**：Agent 提交结果后要过评审和验收门禁才算完成，不通过可以打回重做
- **全程可审计**：任务依赖、事实、决策、证据都有台账记录，出了什么问题可以回放追溯

适合审批流、长链路业务编排、需要人工介入确认的自动化等场景。详见 [Harness 运行时](docs-site/docs/agent/harness-runtime.md)。

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

OpenAI / OpenAI-compatible, Anthropic, DashScope（通义/百炼）, Doubao（火山方舟/豆包）, DeepSeek, Moonshot, Zhipu（智谱）, Hunyuan（腾讯混元）, Lingyi（零一万物）, Ollama, MiniMax, Baichuan, Suno；Rerank（Jina / Ollama / Doubao）；AgentFlow（Dify / Coze / n8n）；VectorStore（Pinecone / Qdrant / pgvector / Milvus / Redis）。完整能力列表见 [feature-map](docs-site/docs/start-here/feature-map.md)。

## 文档与链接

ai4j 提供两个文档入口，按需取用：

- **官方文档站**：https://lnyo-cly.github.io/ai4j/ —— 中英双语，从 5 分钟跑通到各能力详解，配有大量与源码对齐的架构/时序图，适合系统学习和查 API 用法
- **[DeepWiki](https://deepwiki.com/LnYo-Cly/ai4j)** —— AI 问答式仓库导览，适合直接用自然语言问"某个功能是怎么实现的"，每周自动跟随仓库刷新

快速入口：[5 分钟跑通](docs-site/docs/start-here/five-minute-first-chat.md) / [能力地图](docs-site/docs/start-here/feature-map.md) / [Coding Agent CLI / TUI / ACP](docs/readme/zh/coding-agent-cli.md) / [A2A Protocol](docs-site/docs/agent/a2a.md) / [CHANGELOG](CHANGELOG.md) / [CONTRIBUTING](CONTRIBUTING.md)

## License

[Apache License 2.0](LICENSE)
