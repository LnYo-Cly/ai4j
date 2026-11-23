---
title: Gemini 原生协议、Preset 与开放 Platform
description: 说明 Protocol.GEMINI、GeminiCodec、thoughtSignature 跨协议剥离、GeminiNativeClient、ProviderPresets 与开放式 Platform 扩展模型。
tags: [concept, model, gemini]
---

# Gemini 原生协议、Preset 与开放 Platform

从 `2.x` 起，AI4J 将 Google Gemini 原生 `generateContent` / `streamGenerateContent` 协议、厂商预设（`ProviderPresets`）与开放式平台抽象（`Platform`）提升为一等协议能力，不再要求将 Gemini 强行映射为 OpenAI Chat 兼容格式。

---

## 1. 为什么需要原生 `Protocol.GEMINI`

通过第三方兼容网关调用 Gemini 时，往往会丢失以下原生能力：

1. **思维签名往返（`thoughtSignature`）**：Gemini 2.5 / 3.x 在开启思考模式与工具调用时要求在后续多轮消息中精确回传 `thought: true` 与 `thoughtSignature`，兼容层一旦丢弃会触发请求校验失败或推理链断裂。
2. **原生部件结构（`parts` / `systemInstruction`）**：包括 `inlineData`、`fileData`、`functionCall`、`functionResponse`、`thinkingConfig`、`cachedContent`、`safetySettings` 与 `labels`。
3. **细粒度用量拆解（`usageMetadata`）**：包含 `promptTokenCount`、`candidatesTokenCount`、`thoughtsTokenCount`、`cachedContentTokenCount` 与 `totalTokenCount`。

AI4J 通过 [`Protocol.GEMINI`](/docs/capabilities/models/overview) 与 `GeminiCodec` 直接完成统一 `ModelRequest` / `ModelResponse` / `ModelEvent` 模型到 Gemini 原生载荷的双向编解码。

---

## 2. `Protocol.GEMINI` 与能力矩阵

在 `io.github.lnyocly.ai4j.protocol` 中，`ProtocolDescriptors.of(Protocol.GEMINI)` 与 `CapabilityMatrix.forGemini()` 定义了 Gemini 协议的标准路由与能力边界：

| 维度 | 说明 |
| --- | --- |
| 同步端点 | `/v1beta/models/{model}:generateContent` |
| 流式端点 | 经 `GeminiCodec.resolvePath(...)` 路由到 `/v1beta/models/{model}:streamGenerateContent?alt=sse` |
| 支持能力 | `FUNCTION_TOOLS`、`HOSTED_TOOLS`、`TOOL_CHOICE_FORCED`、`SYSTEM_INSTRUCTIONS`、`IMAGE_INPUT`、`DOCUMENT_INPUT`、`REASONING_SIGNATURE`、`SSE_RAW_EVENT` |
| 当前显式标注不支持 | `TOOL_SEARCH_DEFERRED`、`PARALLEL_TOOL_CALLS`、`STRICT_SCHEMA`、`RESPONSE_CHAINING` |

---

## 3. `GeminiCodec` 编解码与字段映射

`io.github.lnyocly.ai4j.protocol.gemini.GeminiCodec` 负责将统一模型请求与响应事件映射为 Gemini 原生 JSON 结构：

### 3.1 请求编码

- **系统指令**：提取系统消息写入顶层 `systemInstruction.parts`。
- **对话内容（`contents`）**：将消息角色映射为 `user` 或 `model`，并将内容块编码为 `parts[]`：
  - 文本块 -> `text`
  - 内联二进制 / 文件引用 -> `inlineData { mimeType, data }` / `fileData { mimeType, fileUri }`
  - 工具调用 -> `functionCall { name, args, id }`
  - 工具结果 -> `functionResponse { name, id, response }`
  - 推理内容 -> 携带 `thought: true` 与 `thoughtSignature`
- **工具与生成配置**：
  - `tools[].functionDeclarations` 与 `toolConfig.functionCallingConfig`
  - `generationConfig`：`temperature`、`topP`、`topK`、`maxOutputTokens`、`stopSequences`、`responseMimeType`、`responseSchema`、`thinkingConfig`、`candidateCount`、`presencePenalty`、`frequencyPenalty`、`seed`、`responseLogprobs`、`logprobs`、`responseModalities`
  - 顶层扩展字段：`cachedContent`、`safetySettings`、`labels`

### 3.2 响应与流式解码

`GeminiCodec` 解析标准 `usageMetadata` 并投影为统一 `ModelUsage`：

- `promptTokenCount` -> `inputTokens`
- `candidatesTokenCount` -> `outputTokens`
- `thoughtsTokenCount` -> `reasoningTokens`
- `cachedContentTokenCount` -> `cachedInputTokens`
- `totalTokenCount` -> 汇总总 Token

---

## 4. `thoughtSignature` 保留与跨协议安全剥离

多协议 Agent 运行时或故障转移（Failover）场景中，同一段对话历史可能在 `GEMINI`、`CHAT_COMPLETIONS`、`RESPONSES` 与 `MESSAGES` 之间切换。直接把 A 协议的加密推理签名发给 B 协议，通常会导致目标网关报 `400 Bad Request`。

AI4J 在统一块模型中记录协议来源与原始元数据：

- `ReasoningBlock` / `ReasoningItem` 与 `ToolCallValue`（`ToolCallBlock` / `ToolCallItem`）保存 `thoughtSignature`（或 `signature`）、`sourceProtocol` 与 `protocolData`（如 `_protocol = "GEMINI"`）。
- **同协议回传**：当消息由 `Protocol.GEMINI` 编码回 Gemini 时，`GeminiCodec` 完整保留 `thought: true`、`thoughtSignature` 以及 `extractCompatibleThoughtSignature(...)` 提取的签名，确保多轮 Tool Calling 与思考链稳定衔接。
- **跨协议剥离与告警**：
  - 当 `sourceProtocol != null && sourceProtocol != Protocol.GEMINI` 时，`GeminiCodec` 自动剥离外部协议的推理签名并生成 `DroppedField` 告警，而不是透传不兼容载荷；
  - 当 Gemini 产生的历史回放给 `ChatCompletionsCodec`、`MessagesCodec` 或 `ResponsesCodec` 时，这些编解码器同样识别外源 `sourceProtocol` 与 `thoughtSignature`，自动剥离并记录 `DroppedField`（例如 `"foreign thoughtSignature stripped on CHAT_COMPLETIONS handoff"`）。

:::tip 协议切换无需手写消息清洗代码
当配合 [`KeyPoolFailoverMiddleware`](/docs/capabilities/models/ai-stream-and-middleware) 或跨厂商路由将主请求从 Gemini 切换到 OpenAI / Anthropic 时，AI4J 协议层会自动完成签名隔离并保留可读文本与工具参数语义。
:::

---

## 5. `GeminiNativeClient` 原生客户端

如果你希望直接操作 Gemini 原生 REST/SSE 负载（例如使用原生安全策略、缓存句柄或原生事件流），可以使用 `io.github.lnyocly.ai4j.service.nativeapi.GeminiNativeClient`：

| 特性 | 说明 |
| --- | --- |
| 默认基址 | `GeminiNativeClient.DEFAULT_BASE_URL`（`https://generativelanguage.googleapis.com`） |
| 鉴权构造 | `GeminiNativeClient.of(apiKey)` 与 `of(baseUrl, apiKey, configuration)` 使用 `Credentials.header("x-goog-api-key", apiKey)`；`ofBearer(...)` 使用 `Credentials.bearer(...)`（适配 Vertex AI / OAuth2 Token） |
| 核心调用 | `generateContent(model, request)` 与 `streamGenerateContent(model, request, NativeSseListener[, CallExecutionOptions])` |
| 原生事件 | `NativeGeminiStreamEvent`（`TYPE_CHUNK = "gemini.chunk"`、`TYPE_DONE = "done"`、`TYPE_ERROR = "error"`）与 `NativeGeminiStreamListener`（`onGeminiEvent(...)`） |
| 错误与链路追踪 | 自动解析 Google 数组/对象错误响应体，提取 `x-goog-request-id`、`x-request-id`、`request-id` |

```java
GeminiNativeClient nativeClient = GeminiNativeClient.of(System.getenv("GEMINI_API_KEY"));

nativeClient.streamGenerateContent(
        "gemini-2.5-flash",
        nativeRequestMap,
        (NativeGeminiStreamListener) event -> {
            if (NativeGeminiStreamEvent.TYPE_CHUNK.equals(event.getType())) {
                System.out.println(event.getData());
            }
        }
);
```

---

## 6. `ProviderPresets` 与 `ProtocolQuirks`

为减少各模型厂商 URL、默认协议、环境变量与兼容性微差（Quirks）的样板配置，`io.github.lnyocly.ai4j.preset` 提供了开箱即用的预设目录：

### 6.1 9 个内置 `ProviderPresets`

| 预设常量 | 默认协议 | 典型用途 |
| --- | --- | --- |
| `ProviderPresets.OPENAI` | `Protocol.RESPONSES` | OpenAI 官方与 Responses API |
| `ProviderPresets.ANTHROPIC` | `Protocol.MESSAGES` | Claude 原生 Messages API |
| `ProviderPresets.GEMINI` | `Protocol.GEMINI` | Google Gemini 原生协议 |
| `ProviderPresets.DEEPSEEK` | Chat 兼容 | DeepSeek 推理与对话模型 |
| `ProviderPresets.DASHSCOPE` | Chat 兼容 | 通义千问 DashScope 兼容接口 |
| `ProviderPresets.DOUBAO` | Chat / Responses | 火山引擎方舟（豆包） |
| `ProviderPresets.ZHIPU` | Chat 兼容 | 智谱 GLM 系列 |
| `ProviderPresets.MINIMAX` | Chat 兼容 | MiniMax 系列 |
| `ProviderPresets.MOONSHOT` | Chat 兼容 | Moonshot / Kimi 系列 |

常用辅助方法：

- `ProviderPresets.find(id)` / `require(id)`：按厂商 ID 查找预设
- `ProviderPresets.all()` / `availableFromEnv()`：枚举全部预设或当前环境变量已就绪的预设
- `ProviderPresets.parseModelSpec("provider:model")`：按首个 `:` 拆分厂商与模型；若省略前缀，可根据模型名前缀（如 `gemini-`）推断预设
- `preset.toEndpoint(apiKey)` / `preset.toEndpointFromEnv()`：快速构造可绑定到 `AiClient` 或 `ModelClient` 的端点

### 6.2 `ProtocolQuirks` 行为微差描述

每个 `ProviderPreset` 可附带 `ProtocolQuirks`，声明目标厂商与标准协议之间的差异，供高层客户端和中间件统一适配：

- `requiresThoughtSignatureRoundTrip`：是否强制要求思考签名往返
- `supportsDeveloperRole`：是否支持 `developer` 角色还是回退为 `system`
- `supportsStreamIncludeUsage`：流式请求是否支持 `stream_options.include_usage`
- `supportsPromptCaching`：是否支持提示词缓存控制
- `requiresMaxTokens` / `maxTokensParameter`：是否必填最大输出 Token 及对应字段名
- `supportsParallelToolCalls` / `supportsStrictSchema`：并行工具调用与严格 JSON Schema 支持度
- `reasoningFieldName`：推理字段别名差异

---

## 7. 开放式 `Platform` 与 `CliProtocol`

### 7.1 从封闭枚举演进到开放 `Platform`

早期版本依赖封闭枚举 `PlatformType` 区分厂商；在 `2.x` 中，`io.github.lnyocly.ai4j.service.Platform` 被抽象为开放接口（`String id()`）：

- 现有枚举 `PlatformType` 实现 `Platform`（包括 `PlatformType.GEMINI("gemini")`），保持 100% 源码与字节码兼容；
- 调用 `Platform.of("my-custom-gateway")` 时，若匹配已知 `PlatformType` 则返回枚举常量，否则返回 `CustomPlatform`，允许业务侧与第三方扩展无需修改 SDK 源码即可注册自定义平台服务。

```java
Platform gemini = Platform.of("gemini");        // 解析为 PlatformType.GEMINI
Platform privateGateway = Platform.of("corp");  // 解析为 CustomPlatform("corp")
```

### 7.2 CLI 多协议选择（`CliProtocol`）

在 `ai4j-cli` 中，`io.github.lnyocly.ai4j.cli.CliProtocol` 已原生包含 `CHAT`、`RESPONSES`、`MESSAGES` 与 `GEMINI`：

- `CliProtocol.defaultProtocol(...)` 会将 `PlatformType.GEMINI` 默认映射到 `CliProtocol.GEMINI`；
- `CliProtocol.isSupportedBy(...)` 支持在 `PlatformType.GEMINI` 与 `PlatformType.OPENAI`（通过兼容适配）上选择 `GEMINI` 协议。

## 下一步建议

- 需要无 Reactor 阻塞、带虚拟线程自动检测的异步流与重试/熔断能力：阅读 [AiStream 异步流与模型中间件](/docs/capabilities/models/ai-stream-and-middleware)
- 希望用一行 `AiClient.fromEnv()`、`provider:model` 与 `@AiService` 启动业务开发：阅读 [AiClient DX 与声明式服务](/docs/getting-started/ai-client-dx)
- 查看各内置平台能力对比：阅读 [平台与服务支持矩阵](/docs/capabilities/models/platform-service-matrix)
