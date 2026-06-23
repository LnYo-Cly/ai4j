---
sidebar_position: 2
---

# 平台与服务矩阵

这页只回答两个问题：

1. 当前代码到底支持哪些平台
2. 每个平台支持到哪类 service

> 说明：本页以 `AiService` 当前实现为准，不按 README、示例或未来规划估算。

## 1. 平台枚举

当前平台枚举定义在 `PlatformType`：

- `OPENAI`
- `ANTHROPIC`
- `ZHIPU`
- `DEEPSEEK`
- `MOONSHOT`
- `HUNYUAN`
- `LINGYI`
- `OLLAMA`
- `MINIMAX`
- `BAICHUAN`
- `DASHSCOPE`
- `DOUBAO`
- `JINA`

要注意一点：平台枚举存在，并不等于它自动支持所有 service 面。  
真正的支持关系仍然取决于 `AiService.create*Service(...)` 里有没有对应分支。

## 2. 当前 service 支持矩阵

| 平台 | Chat | Responses | Messages | Embedding | Rerank | Audio | Realtime | Image |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| OPENAI | ✅ | ✅ | ❌ | ✅ | ❌ | ✅ | ✅ | ✅ |
| ANTHROPIC | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| DOUBAO | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |
| DASHSCOPE | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| OLLAMA | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ |
| JINA | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| ZHIPU | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| DEEPSEEK | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| MOONSHOT | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| HUNYUAN | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| LINGYI | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| MINIMAX | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| BAICHUAN | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

## 3. 这张矩阵真正说明了什么

### `Chat` 是最广覆盖的主线

几乎所有平台能力都先落在 `Chat` 入口，这使它成为当前最通用的模型访问主线。

### `Responses` 是明确存在、但覆盖更窄的第二主线

当前只支持：

- OpenAI
- Doubao
- DashScope

这意味着 `Responses` 在 AI4J 里不是“每个平台都应当自动拥有”的替代接口，而是一条更聚焦的结构化访问主线。

### `Messages` 是 Anthropic 协议第三主线

当前只支持 `ANTHROPIC`（原生 `IMessagesService`）。它让本就说 Anthropic 方言的系统**原生 in / 原生 out** 接入（零 OpenAI 转换），并复用同一条 `IMessagesService` 打到 Claude 及合作厂家的 Anthropic 兼容入口（智谱 / MiniMax coding-plan）。`ANTHROPIC` 平台同时保留 `Chat` 入口（统一 `IChatService` 适配器，翻译 OpenAI 格式）。详见 [Messages（Anthropic 原生）](/docs/core-sdk/model-access/messages)。

### `Embedding` / `Audio` / `Realtime` 更窄

- `Embedding` 只支持 OpenAI / Ollama
- `Audio` 只支持 OpenAI
- `Realtime` 只支持 OpenAI

这说明这些 service 面虽然已经正式进入 SDK，但并没有被伪装成跨平台完全对称能力。

### `Rerank` 是独立矩阵

当前只支持：

- Jina
- Ollama
- Doubao

这说明检索相关能力并不总是跟 `Chat` provider 一起出现，实际工程里经常需要“chat provider”和“rerank provider”分离。

## 4. 统一调用入口仍然一致

虽然矩阵不对称，调用入口仍然统一：

```java
AiService aiService = new AiService(configuration);

IChatService chat = aiService.getChatService(PlatformType.OPENAI);
IResponsesService responses = aiService.getResponsesService(PlatformType.DOUBAO);
IMessagesService messages = aiService.getMessagesService(PlatformType.ANTHROPIC);
IEmbeddingService embedding = aiService.getEmbeddingService(PlatformType.OLLAMA);
IRerankService rerank = aiService.getRerankService(PlatformType.JINA);
IImageService image = aiService.getImageService(PlatformType.DOUBAO);
```

如果平台不支持该 service，当前实现会直接抛：

- `IllegalArgumentException("Unknown platform: ...")`

也就是说，不支持不是“静默降级”，而是显式失败。

## 5. 和多实例注册表怎么结合

如果你在一个应用里需要管理多套 provider 实例，通常应结合：

- `AiServiceRegistry`

例如：

```java
IChatService tenantA = aiServiceRegistry.getChatService("tenant-a-openai");
IChatService tenantB = aiServiceRegistry.getChatService("tenant-b-doubao");
IRerankService rerank = aiServiceRegistry.getRerankService("tenant-rerank");
```

这里的重点不是“能按 id 取对象”，而是你可以把不对称的 provider 能力矩阵显式组织成多实例路由图，而不是把平台选择散落到业务代码里。

## 6. 阅读这张矩阵时不要犯的错

### 不要把 `PlatformType` 当成能力保证

平台枚举只是候选集合，真正支持什么要回到 `AiService` 分发实现。

### 不要假设 `Responses` 是 `Chat` 的完全覆盖替代

当前矩阵已经说明它不是。

### 不要把检索链 provider 和 chat provider 绑死

`Rerank` 的支持矩阵本身就是独立的。

## 7. 这一页的结论

> AI4J 当前的统一，不在于所有平台能力完全对称，而在于它用统一入口显式维护了一张不对称但清晰的 service 矩阵。理解这张矩阵，比记住某个 provider 的示例更重要，因为它直接决定了你能否正确设计多平台、多 service 的工程边界。
