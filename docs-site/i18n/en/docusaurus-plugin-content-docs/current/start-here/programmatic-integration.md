---
title: "Programmatic Integration"
description: "How to embed ai4j into your application: gathers five scattered integration threads — the AiService programmatic entry point, the ACP host protocol, trace/replay observability, CLI/TUI embedding, and Spring Boot auto-configuration — into a single landing page, with a one-sentence positioning and a deep link for each."
tags: [concept]
---

# Programmatic Integration

Embed ai4j into your application as a library, rather than just running its CLI. This page is an aggregation landing page: it pulls the five integration threads scattered across subsystems into one place, giving each only a one-sentence positioning and a deep link, so you can pick the right entry point first and then read the corresponding topic page.

## What this page solves

ai4j's integration entry points are spread across different subsystems by capability: model / service access lives in the Core SDK, the host protocol in Coding Agent's ACP, the observability event stream in the Agent's trace / replay, terminal embedding in the CLI / TUI, and configuration-driven wiring in Spring Boot. If you go looking with the intent of "I want to embed it into my app," you will likely bounce between five directories.

This page does not duplicate the content of each topic page; it only helps you pick the right starting point among the five threads.

## From Pi's integration surfaces to ai4j's counterparts

ai4j is Java, and the division of integration surfaces differs from Pi (Node / TypeScript), but maps one-to-one:

| Pi integration surface | ai4j counterpart | Entry point |
| --- | --- | --- |
| SDK (`createAgentSession` programmatic API) | `AiService` / `AiServiceRegistry` programmatic factory | [Service Entry and Registry](/docs/core-sdk/service-entry-and-registry) |
| RPC (stdin / stdout JSON-RPC subprocess protocol) | ACP headless host (newline-delimited JSON-RPC) | [ACP Integration](/docs/coding-agent/acp-integration) |
| JSON event stream (`--mode json` one-shot event stream → stdout) | **No turnkey equivalent** (building blocks see ① below) | — |
| TUI (interactive terminal) | `ai4j-cli`'s `code` / `tui` host | [CLI / TUI Usage Guide](/docs/coding-agent/cli-and-tui) |
| — (no Pi counterpart; ai4j exclusive) | Spring Boot auto-configuration | [Spring Boot Auto Configuration](/docs/spring-boot/auto-configuration) |

The key difference: ai4j has no single session factory where "one SDK object handles everything." The capability factory (`AiService`) and the session protocol (ACP) are two separate layers — the former is an in-process call, the latter is a cross-process protocol.

> ① Pi's `--mode json` is a **one-shot headless mode** that "runs a prompt and dumps the entire agent event stream as JSONL to stdout for pipelines / `jq` to consume." ai4j **does not** have this kind of turnkey mode: the **event types** of the runtime event stream (`MODEL_REQUEST` / `TOOL_CALL`, etc.) share the same lineage as Pi, but in ai4j it is an in-process mechanism used for **trace / replay** observability and recovery (thread 4), not an "event stream → stdout" integration mode; ACP (thread 3) also emits agent events outward, but it is a **bidirectional control protocol** (≈ Pi RPC), not a one-shot pipeline. If you need the Pi `--mode json` style usage, you have to use the runtime event stream and add your own stdout emitter.

## Five integration threads

### 1. AiService — programmatic service factory

`AiService` is an explicit factory that unifies model access, retrieval augmentation, and composition capabilities under a single entry-point object (`getChatService` / `getResponsesService` / `getEmbeddingService` / `getRagService`, etc.), using a set of `switch(platform)` internally to decide which implementation class to create. This is the first link for an ordinary Java application embedding ai4j.

→ [Service Entry and Registry](/docs/core-sdk/service-entry-and-registry) · first-chat code see [Quickstart for Java](/docs/start-here/quickstart-java)

### 2. AiServiceRegistry — multiple instances / multiple profiles

When multiple accounts, multiple tenants, or multiple provider profiles coexist, use `AiServiceRegistry` to manage multiple registration entries by `id`, each bound to a `PlatformType`, directly exposing convenience methods to fetch Chat / Embedding / RAG by `id`; an unknown `id` throws immediately, making it suitable as a formal multi-instance entry point rather than a loose lookup.

→ [Service Entry and Registry](/docs/core-sdk/service-entry-and-registry) · OpenAI-compatible profile see [OpenAI-compatible and TroveBox](/docs/start-here/openai-compatible-and-trovebox)

### 3. ACP — expose the coding session protocol to a host

ACP is ai4j's standard integration surface for IDE / desktop shells: newline-delimited JSON-RPC (not LSP's `Content-Length` framing), exposing session creation / loading, prompt execution, permission confirmation, and structured events; permission confirmation is a server-initiated reverse `session/request_permission` RPC. It is the ai4j counterpart of the Pi RPC pattern, but under the hood shares the same coding runtime as the `code` command.

→ [ACP Integration](/docs/coding-agent/acp-integration) · boundary with MCP see [MCP and ACP](/docs/coding-agent/mcp-and-acp)

### 4. trace / replay — observability and recovery

The runtime already emits unified events (`MODEL_REQUEST` / `MODEL_RESPONSE` / `TOOL_CALL` / `TOOL_RESULT`); trace and replay are consumers of these events rather than instrumentation points: `AgentTraceListener` folds events into spans and exports to OTel / Langfuse / JSONL; `IoCaptureAgentListener` + `NodeReplayer` perform node-level replay, `ResumeCache` performs crash resume, and `HashChainedEventLog` provides tamper-evident auditing.

:::note This is not an equivalent of Pi's `--mode json`
trace / replay is ai4j's **observability and reliability** layer (in-process tracing, node replay, crash resume, tamper-evident audit), not an integration mode for "dumping the agent event stream to stdout in one shot." The event types it consumes share the same lineage as Pi's event stream, but the **positioning is different** — see ① in the table above. The "pipeline consumption" usage of Pi's JSON mode requires you to add your own emitter on top of the runtime event stream in ai4j.
:::

→ [Trace and Observability](/docs/agent/trace-observability) · resume / replay / audit see [Replay, Recovery & Audit](/docs/agent/replay-recovery-audit)

### 5. CLI / TUI — embed the terminal host

`code` and `tui` share the same coding runtime, session manager, MCP runtime, and approval semantics, diverging only at the host layer (JLINE / legacy / TUI runtime). Whether a session is persisted is decided by `--no-session` / `--session-dir`, regardless of `code` or `tui`. Start here when you want to embed or replace the terminal host.

→ [CLI / TUI Usage Guide](/docs/coding-agent/cli-and-tui) · themes and four-layer customization see [TUI Customization and Themes](/docs/coding-agent/tui-customization)

### 6. Spring Boot auto-configuration — configuration-driven wiring

`ai4j-spring-boot-starter` uses `AiConfigAutoConfiguration` to bind `ai.*` properties into a unified `Configuration`, assembling OkHttpClient → provider → `AiService` / `AiServiceRegistry` / `FreeAiService` → conditionally creating VectorStore / RAG / Reranker in a fixed order; key Beans are all `@ConditionalOnMissingBean` and can be taken over by business implementations.

→ [Spring Boot Auto Configuration](/docs/spring-boot/auto-configuration) · quick wiring see [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)

## Minimal runnable example: programmatic first chat

This is the minimal closed loop of thread 1 — `Configuration` → `AiService` → `IChatService` → synchronous Chat. Java 8 style, directly copyable.

Dependency (note the real Maven groupId is `io.github.lnyo-cly`):

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j</artifactId>
  <version>2.4.2</version>
</dependency>
```

Code:

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

        // Programmatic entry: AiService is the unified capability factory
        AiService aiService = new AiService(configuration);
        IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

        ChatCompletion request = ChatCompletion.builder()
                .model("gpt-4o-mini")
                .message(ChatMessage.withUser("Introduce AI4J in one sentence"))
                .build();

        ChatCompletionResponse response = chatService.chatCompletion(request);
        String text = response.getChoices().get(0).getMessage().getContent().getText();
        System.out.println(text);
    }
}
```

Expected output (text varies by provider / model):

```text
AI4J is a Java AI SDK that unifies multiple large models and abstracts away provider differences.
```

Once this chain runs, to continue wiring up Tool / MCP / RAG / Agent on top, all of them extend from the same `AiService` factory — no need to switch entry points.

## Which thread to pick

- Only want to invoke model capabilities → thread 1, first chat is enough.
- Multiple providers / multiple accounts in one application → thread 2.
- Driving a persistent coding session inside an IDE / desktop shell → thread 3.
- Need to observe, replay, and resume agents in production → thread 4.
- Building or replacing a terminal product shell → thread 5.
- Already a Spring Boot project, want to wire in via configuration and Beans → thread 6.

## Further reading

- [Quickstart for Java](/docs/start-here/quickstart-java) · [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
- [Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
- [ACP Integration](/docs/coding-agent/acp-integration) · [MCP and ACP](/docs/coding-agent/mcp-and-acp)
- [Trace and Observability](/docs/agent/trace-observability) · [Replay, Recovery & Audit](/docs/agent/replay-recovery-audit)
- [CLI / TUI Usage Guide](/docs/coding-agent/cli-and-tui) · [TUI Customization and Themes](/docs/coding-agent/tui-customization)
- [Spring Boot Auto Configuration](/docs/spring-boot/auto-configuration)
- For the full capability map: [Feature Map](/docs/start-here/feature-map)
