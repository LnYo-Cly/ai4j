---
title: 编程式集成
description: 如何把 ai4j 嵌进你的应用：把 AiService 编程入口、ACP 宿主协议、trace/replay 可观测、CLI/TUI 嵌入与 Spring Boot 自动装配五条散落的集成主线收进一个落地页，每条给一句定位和深链。
tags: [concept]
---

# 编程式集成

把 ai4j 当作库嵌进你的应用，而不是只跑它的 CLI。这页是聚合落地页：把散落在各子系统里的五条集成主线收拢到一处，每条只给一句定位和一个深链，让你先选对入口，再去读对应专题页。

## 这页解决什么

ai4j 的集成入口按能力分散在不同子系统里：模型 / 服务访问在 Core SDK，宿主协议在 Coding Agent 的 ACP，可观测事件流在 Agent 的 trace / replay，终端嵌入在 CLI / TUI，配置化接入在 Spring Boot。如果你按"我要把它嵌进应用"这个意图去找，很可能在五个目录间来回跳。

这页不重复各专题页的内容，只负责让你在五条主线里选对起点。

## 从 Pi 的集成面到 ai4j 的对应物

ai4j 是 Java，集成面的划分和 Pi（Node / TypeScript）不同，但能一一对应：

| Pi 的集成面 | ai4j 的对应物 | 入口 |
| --- | --- | --- |
| SDK（`createAgentSession` 编程 API） | `AiService` / `AiServiceRegistry` 编程工厂 | [服务入口与注册表](/docs/capabilities/service-entry) |
| RPC（stdin / stdout JSON-RPC 子进程协议） | ACP headless host（换行分隔 JSON-RPC） | [ACP 集成](/docs/products/coding-agent/acp-integration) |
| JSON event stream（`--mode json` 一次性事件流→stdout） | **无 turnkey 等价**（构建块见下文①） | — |
| TUI（交互终端） | `ai4j-cli` 的 `code` / `tui` 宿主 | [CLI / TUI 使用指南](/docs/products/coding-agent/cli-and-tui) |
| —（Pi 无对应，ai4j 独有） | Spring Boot 自动装配 | [Spring Boot 自动配置](/docs/integrations/spring-boot/auto-configuration) |

最关键的差别：ai4j 没有"一个 SDK 对象包办一切"的单一 session 工厂。能力工厂（`AiService`）和会话协议（ACP）是分开的两层——前者是程序内调用，后者是跨进程协议。

> ① Pi 的 `--mode json` 是"跑一个 prompt、把整个 agent 事件流以 JSONL 打到 stdout 供管道 / `jq` 消费"的**一次性 headless 模式**。ai4j **没有**这种 turnkey 模式：runtime 事件流（`MODEL_REQUEST` / `TOOL_CALL` 等）的**事件类型**与 Pi 同源，但在 ai4j 里它是给 **trace / replay** 做**可观测性与恢复**用的进程内机制（主线 4），不是"事件流→stdout"的集成模式；ACP（主线 3）也会往外发 agent 事件，但它是**双向控制协议**（≈ Pi RPC），不是一次性管道。若需要 Pi `--mode json` 那种用法，得用 runtime 事件流 + 自己加一个 stdout 发射器。

## 五条集成主线

### 1. AiService —— 编程式服务工厂

`AiService` 是把模型访问、检索增强和组合能力统一收束在一个入口对象下的显式工厂（`getChatService` / `getResponsesService` / `getEmbeddingService` / `getRagService` 等），内部用一组 `switch(platform)` 决定创建哪个实现类。这是普通 Java 应用嵌 ai4j 的第一条链。

→ [服务入口与注册表](/docs/capabilities/service-entry) · 首聊代码见 [Java 快速开始](/docs/getting-started/quickstart-java)

### 2. AiServiceRegistry —— 多实例 / 多 profile

多账号、多租户、多 provider profile 共存时，用 `AiServiceRegistry` 按 `id` 管理多套注册项，每项绑定 `PlatformType`，对外直接暴露按 `id` 取 Chat / Embedding / RAG 的便利方法；未知 `id` 会直接抛错，适合做正式多实例入口而非松散查找。

→ [服务入口与注册表](/docs/capabilities/service-entry) · OpenAI-compatible profile 见 [OpenAI-compatible 与 TroveBox](/docs/capabilities/models/openai-compatible-and-trovebox)

### 3. ACP —— 把 coding session 协议化暴露给宿主

ACP 是 ai4j 面向 IDE / 桌面壳的标准接入面：换行分隔 JSON-RPC（不是 LSP 的 `Content-Length` framing），暴露 session 创建 / 加载、prompt 执行、权限确认和结构化事件；权限确认是服务端反向发起的 `session/request_permission` RPC。它是 Pi RPC 模式在 ai4j 里的等价物，但底层和 `code` 命令共用同一套 coding runtime。

→ [ACP 集成](/docs/products/coding-agent/acp-integration) · 与 MCP 的边界见 [MCP 与 ACP](/docs/products/coding-agent/mcp-and-acp)

### 4. trace / replay —— 可观测性与恢复

runtime 已经发布统一事件（`MODEL_REQUEST` / `MODEL_RESPONSE` / `TOOL_CALL` / `TOOL_RESULT`），trace 和 replay 都是这些事件的消费者而非埋点：`AgentTraceListener` 把事件折叠成 span 并导出到 OTel / Langfuse / JSONL；`IoCaptureAgentListener` + `NodeReplayer` 做节点级重放，`ResumeCache` 做崩溃续跑，`HashChainedEventLog` 做防篡改审计。

:::note 这不是 Pi `--mode json` 的等价物
trace / replay 是 ai4j 的**可观测性与可靠性**层（进程内 tracing、节点重放、崩溃续跑、防篡改审计），不是"把 agent 事件流一次性打到 stdout"的集成模式。它消费的事件类型与 Pi 的事件流同源，但**定位不同**——见上表①。Pi JSON 模式的"管道消费"用法在 ai4j 里需要自己用 runtime 事件流加一个发射器。
:::

→ [Trace 与可观测性](/docs/agent/observability/trace-observability) · 续跑 / 重放 / 审计见 [重放、恢复与审计](/docs/agent/observability/replay-recovery-audit)

### 5. CLI / TUI —— 嵌入终端宿主

`code` 和 `tui` 共用同一套 coding runtime、session manager、MCP runtime 和审批语义，只在宿主层（JLINE / legacy / TUI runtime）分叉。session 是否持久化由 `--no-session` / `--session-dir` 决定，与 `code` 还是 `tui` 无关。要嵌入或替换终端宿主时从这里进入。

→ [CLI / TUI 使用指南](/docs/products/coding-agent/cli-and-tui) · 主题与四层定制见 [TUI 定制与主题](/docs/products/coding-agent/tui-customization)

### 6. Spring Boot 自动装配 —— 配置化接入

`ai4j-spring-boot-starter` 用 `AiConfigAutoConfiguration` 把 `ai.*` 属性绑成统一 `Configuration`，按固定顺序组装 OkHttpClient → provider → `AiService` / `AiServiceRegistry` / `FreeAiService` → 条件性创建 VectorStore / RAG / Reranker，关键 Bean 都是 `@ConditionalOnMissingBean`，可被业务实现接管。

→ [Spring Boot 自动配置](/docs/integrations/spring-boot/auto-configuration) · 快速接入见 [Spring Boot 快速开始](/docs/getting-started/quickstart-spring-boot)

## 最小可运行示例：编程式首聊

这是主线 1 的最小闭环——`Configuration` → `AiService` → `IChatService` → 同步 Chat。Java 8 风格，可直接复制。

依赖（注意真实的 Maven groupId 是 `io.github.lnyo-cly`）：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j</artifactId>
  <version>2.4.2</version>
</dependency>
```

代码：

```java
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;

public class EmbedAi4j {
    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Missing OPENAI_API_KEY");
        }

        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiKey(apiKey);

        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(openAiConfig);

        // 编程入口：AiService 是统一能力工厂
        AiService aiService = new AiService(configuration);
        IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

        ChatCompletion request = ChatCompletion.builder()
                .model("gpt-4o-mini")
                .message(ChatMessage.withUser("用一句话介绍 AI4J"))
                .build();

        ChatCompletionResponse response = chatService.chatCompletion(request);
        String text = response.getChoices().get(0).getMessage().getContent().getText();
        System.out.println(text);
    }
}
```

预期输出（文本随 provider / 模型变化）：

```text
AI4J 是一个统一多家大模型、屏蔽 provider 差异的 Java AI SDK。
```

跑通这条链之后，要继续往上接 Tool / MCP / RAG / Agent，都从同一个 `AiService` 工厂扩展，不必换入口。

## 选哪条主线

- 只想把模型能力调起来 → 主线 1，首聊即可。
- 一个应用里多 provider / 多账号 → 主线 2。
- 在 IDE / 桌面壳里驱动一个持久 coding session → 主线 3。
- 要在线上观测、重放、续跑 agent → 主线 4。
- 要做或替换终端产品壳 → 主线 5。
- 已是 Spring Boot 项目，想用配置和 Bean 接入 → 主线 6。

## 继续阅读

- [Java 快速开始](/docs/getting-started/quickstart-java) · [Spring Boot 快速开始](/docs/getting-started/quickstart-spring-boot)
- [服务入口与注册表](/docs/capabilities/service-entry)
- [ACP 集成](/docs/products/coding-agent/acp-integration) · [MCP 与 ACP](/docs/products/coding-agent/mcp-and-acp)
- [Trace 与可观测性](/docs/agent/observability/trace-observability) · [重放、恢复与审计](/docs/agent/observability/replay-recovery-audit)
- [CLI / TUI 使用指南](/docs/products/coding-agent/cli-and-tui) · [TUI 定制与主题](/docs/products/coding-agent/tui-customization)
- [Spring Boot 自动配置](/docs/integrations/spring-boot/auto-configuration)
- 想看完整能力地图：[功能地图](/docs/getting-started/feature-map)
