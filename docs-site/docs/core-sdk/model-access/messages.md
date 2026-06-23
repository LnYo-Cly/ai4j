# Messages（Anthropic 原生）

`Messages` 是 AI4J 里与 `Chat`（OpenAI Chat Completions）、`Responses`（OpenAI Responses）并列的第三条模型访问主线，走的是 **Anthropic Messages 协议**（`POST /v1/messages`）。

它存在的核心理由不是"多一个 provider"，而是：

> 让本就说 Anthropic 方言的系统，**原生 in / 原生 out**地接入，零 OpenAI 格式转换、零字段丢失。

## 1. 三条主线，不是新旧关系

| 主线 | 协议 | 接口 | 适合 |
| --- | --- | --- | --- |
| Chat | OpenAI Chat Completions | `IChatService` | OpenAI 兼容链路；绝大多数国产模型 |
| Responses | OpenAI Responses | `IResponsesService` | 结构化事件 / item 流；完整下推 reasoning/store |
| **Messages** | **Anthropic Messages** | **`IMessagesService`** | **Claude；各家 coding-plan 的 Anthropic 兼容入口** |

它们是三条不同协议线，不是高低级关系。选哪条取决于"你的系统/模型说哪种方言"，而不是"谁更先进"。

## 2. 关键源码入口

- `service/IMessagesService.java` —— 协议族接口（与 `IChatService` / `IResponsesService` 并列）
- `platform/anthropic/chat/AnthropicMessagesService.java` —— 原生实现（传输 + SSE 解析）
- `platform/anthropic/chat/entity/AnthropicChatCompletion.java` —— 原生请求
- `platform/anthropic/chat/entity/AnthropicChatCompletionResponse.java` —— 原生响应
- `platform/anthropic/stream/AnthropicStreamHandler.java` —— 原生流式事件回调
- `platform/anthropic/errors/AnthropicApiException.java` —— 类型化异常
- `platform/anthropic/chat/AnthropicChatService.java` —— 统一 `IChatService` 适配器（委托原生 + OpenAI 翻译）
- `service/factory/AiService.java` —— `getMessagesService(PlatformType.ANTHROPIC)`

## 3. 两层设计：原生在下，统一在上

`Messages` 同时暴露两个入口，按你的需要选：

```
统一层   IChatService（OpenAI 格式）            ← 想跨 provider 统一调用 / 已有 OpenAI 链路
            │ 翻译（OpenAI ⇄ Anthropic），有损于通用语边界
原生层 ★ IMessagesService（Anthropic 原生）     ← 系统本就说 Anthropic / 要零转换
            │
         传输层  x-api-key + anthropic-version + SSE
```

- **原生 `IMessagesService`**：`AnthropicChatCompletion` 进、`AnthropicChatCompletionResponse` 出，零 OpenAI 转换。流式用 `AnthropicStreamHandler` 回调原生事件（`text_delta` / `thinking_delta` / `tool_use` / `stop_reason`）。
- **统一 `AnthropicChatService`（`IChatService`）**：把统一的 `ChatCompletion` 翻译成 Anthropic Messages、响应/流式事件翻回 OpenAI 格式。它**委托**原生服务做传输，自己只做翻译。

> 选统一入口 = 你主动接受 OpenAI 通用语边界的损耗（如 content block 被拍平）；选原生入口 = 零损耗。`AnthropicContentBlock` 保留 `thinking` 字段，原生路径不丢思考内容。

## 4. 直接用原生 `IMessagesService`

```java
AnthropicConfig config = new AnthropicConfig();
config.setApiKey(apiKey);
// 默认指向 https://api.anthropic.com/；指向合作厂家 Anthropic 兼容入口只需覆盖 apiHost
config.setApiHost("https://open.bigmodel.cn/api/anthropic/");

Configuration configuration = new Configuration();
configuration.setAnthropicConfig(config);
IMessagesService messages = new AiService(configuration).getMessagesService(PlatformType.ANTHROPIC);

AnthropicChatCompletion request = new AnthropicChatCompletion();
request.setModel("glm-5.1");
request.setSystem("Reply concisely.");
AnthropicMessage user = new AnthropicMessage();
user.setRole("user");
user.setContent("Introduce yourself.");
request.setMessages(Collections.singletonList(user));
request.setMaxTokens(128);

AnthropicChatCompletionResponse response = messages.messages(request);
// 原生 content blocks（text / thinking / tool_use）原样拿到
```

### 流式（原生事件回调）

```java
messages.messagesStream(request, new AnthropicStreamHandler() {
    @Override public void onStart(String messageId, String model) {}
    @Override public void onDeltaText(String text) { System.out.print(text); }
    @Override public void onThinkingDelta(String thinking) { /* 思考增量 */ }
    @Override public void onToolUseComplete(int idx, String id, String name, String inputJson) {}
    @Override public void onStopReason(String stopReason, long in, long out) {}
    @Override public void onComplete() {}
});
```

`AnthropicStreamHandler` 的每个回调对应一类原生 SSE 事件，无需自己解析 SSE。

## 5. agent 一行接入（`.anthropicMessages`）

agent 规范模型（`AgentPrompt` / `AgentModelResult`）本就协议中立。`AgentBuilder.anthropicMessages(...)` 把 agent 直接接到原生 Anthropic 线协议：

```java
Agent agent = Agents.react()
        .anthropicMessages(apiKey, "https://open.bigmodel.cn/api/anthropic/")
        .model("glm-5.1")
        .build();

AgentResult result = agent.newSession().run("Introduce yourself in one sentence.");
// result.getOutputText()  —— 正文
// result.getReasoningText() —— thinking（开 thinking 时）
// result.getRawResponse() —— 原生 AnthropicChatCompletionResponse（非 OpenAI 类型）
```

底层是 `MessagesModelClient`（与 `ChatModelClient` / `ResponsesModelClient` 并列的第三个 `AgentModelClient`）。详见 [Model Client Selection](/docs/agent/model-client-selection)。

## 6. coding-plan：智谱 / MiniMax 的 Anthropic 入口

智谱（GLM）和 MiniMax 的 coding-plan key **走 Anthropic 格式**，不是它们各自的 OpenAI 兼容端点。配 `apiHost` 即可复用同一个 `IMessagesService`：

| 厂家 | Anthropic 入口 | 模型 |
| --- | --- | --- |
| Anthropic 官方 | `https://api.anthropic.com/` | claude-* |
| 智谱 Coding Plan | `https://open.bigmodel.cn/api/anthropic/` | glm-5.1 / glm-5.2 |
| MiniMax Coding Plan | `https://api.minimaxi.com/anthropic/` | MiniMax-M3（国际 `api.minimax.io/anthropic`） |

> 注意：同一个 coding-plan key 打到该厂家的 **OpenAI 兼容端点**（如智谱 `api/paas/v4`）会报"余额不足"——这不是 key 坏了，是 coding-plan 配额只挂在 Anthropic 入口上。必须走 `api/anthropic`。

## 7. thinking → reasoningContent

开 thinking（通过 `extraBody` 透传，或 agent 的 `AgentPrompt.reasoning`）时：

- 非流式响应：content 里的 `thinking` block → 统一路径映射为 `ChatMessage.reasoningContent`；agent 路径映射为 `AgentModelResult.reasoningText`。
- 流式：`thinking_delta` → 统一路径发 `reasoning_content` delta；agent 路径回调 `onReasoningDelta(...)`。

## 8. 鉴权与异常

- 鉴权头：`x-api-key: <key>` + `anthropic-version: 2023-06-01`（非 `Authorization: Bearer`）。
- 原生路径错误抛类型化 `AnthropicApiException`（子类 `AnthropicRateLimitException` / `AnthropicOverloadedException` / `AnthropicAuthenticationException` / `AnthropicInvalidRequestException`），可精确 catch；统一 `IChatService` 路径仍映射为 `CommonException`。

## 9. Spring Boot starter 配置

`ai4j-spring-boot-starter` 通过 `@ConfigurationProperties(prefix = "ai.anthropic")` 暴露 Anthropic 配置，auto-config 把 `ai.anthropic.*` 灌进 `AnthropicConfig`：

```yaml
ai:
  anthropic:
    api-key: ${ANTHROPIC_API_KEY}
    api-host: https://open.bigmodel.cn/api/anthropic/   # 默认 api.anthropic.com；coding-plan 改这里
    chat-completion-url: v1/messages                      # 默认
    api-version: "2023-06-01"                             # 默认
```

配置后即可 `aiService.getMessagesService(PlatformType.ANTHROPIC)` 拿到原生服务；agent 侧用 `.anthropicMessages(apiKey, baseUrl)`（见第 5 节）。

## 10. 什么时候选 Messages

适合 `Messages` 原生路径：

- 你的系统本就说 Anthropic 方言（历史代码、Claude Code 兼容生态）
- 你在用 coding-plan（智谱 / MiniMax）跑 glm-5.x / MiniMax-M3
- 你要原生 content block / thinking / cache 字段零损耗

适合统一 `IChatService`（`AnthropicChatService`）路径：

- 你想跨 provider 一套 OpenAI 格式调用，Anthropic 只是其中一家
- 你已有成熟 OpenAI 兼容链路

## 11. 继续阅读

1. [Chat](/docs/core-sdk/model-access/chat)
2. [Responses](/docs/core-sdk/model-access/responses)
3. [Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)
4. [Model Client Selection](/docs/agent/model-client-selection)
5. [平台与服务矩阵](/docs/core-sdk/platform-service-matrix)
