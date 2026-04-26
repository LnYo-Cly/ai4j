---
sidebar_position: 2
---

# 平台适配与统一接口

这一页聚焦一件事：当你已经知道如何通过 `AiService` 取服务后，不同平台之间到底被 AI4J 统一了什么，又保留了哪些差异。

如果你还没看统一入口，先看：

- [统一服务入口与调用方式](/docs/ai-basics/unified-service-entry)
- [服务工厂与多实例注册表](/docs/ai-basics/service-factory-and-registry)

---

## 1. 先看“适配层”到底在哪些包里

AI4J 的平台适配不是只靠一个 `switch` 完成，而是分散在几层源码里：

| 层 | 主要包 | 作用 |
| --- | --- | --- |
| 配置层 | `config` | 保存各 provider 的 `apiHost / apiKey / endpoint` 等配置对象 |
| 统一接口层 | `service` | 定义 `IChatService / IResponsesService / IEmbeddingService / IRerankService ...` |
| 工厂分发层 | `service.factory` | 通过 `AiService` / `AiServiceRegistry` 把平台配置映射成统一服务入口 |
| provider 适配层 | `platform.<provider>.<capability>` | 真正把统一请求转成上游 HTTP / JSON 协议 |
| 公共转换层 | `convert`、`listener`、`exception.chain` | 做请求转换、流式解析、错误收敛 |

这意味着：

- “平台适配”不是业务层概念，而是 SDK 内部的一整层
- 业务层真正感知到的只应该是统一接口和能力矩阵

---

## 2. 统一平台入口

你只需要面对统一服务接口，不直接依赖某家平台 SDK。

```java
AiService aiService = new AiService(configuration);

IChatService chat = aiService.getChatService(PlatformType.OPENAI);
IResponsesService responses = aiService.getResponsesService(PlatformType.DOUBAO);
IEmbeddingService embedding = aiService.getEmbeddingService(PlatformType.OLLAMA);
IImageService image = aiService.getImageService(PlatformType.DOUBAO);
```

如果某平台不支持该服务，`AiService` 会抛出 `IllegalArgumentException`。

这意味着：

- 业务层永远先面对统一接口
- 平台能力边界由 `AiService` 明确暴露
- 不会 silently fallback 成另一条协议

而且这条边界不只对单实例 `AiService` 成立，对多实例 `AiServiceRegistry` 也一样成立，因为注册表最后还是回落到 `AiService` 的服务分发。

---

## 3. 平台枚举（PlatformType）

- `OPENAI`
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

---

## 4. 服务能力矩阵（当前实现）

| 平台 | Chat | Responses | Embedding | Rerank | Audio | Realtime | Image |
| --- | --- | --- | --- | --- | --- | --- | --- |
| OPENAI | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ |
| DOUBAO | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | ✅ |
| DASHSCOPE | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| OLLAMA | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ |
| JINA | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| ZHIPU | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| DEEPSEEK | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| MOONSHOT | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| HUNYUAN | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| LINGYI | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| MINIMAX | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| BAICHUAN | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

这个矩阵的来源不是单独维护的一份文档表，而是：

- `AiService#getChatService(...)`
- `AiService#getResponsesService(...)`
- `AiService#getEmbeddingService(...)`
- `AiService#getRerankService(...)`
- `AiService#getAudioService(...)`
- `AiService#getRealtimeService(...)`
- `AiService#getImageService(...)`

也就是说，能力矩阵以工厂分发代码为准。

---

## 5. 当前适配实现分成哪几类

### 5.1 OpenAI 全能力主线

源码入口集中在：

- `platform.openai.chat`
- `platform.openai.response`
- `platform.openai.embedding`
- `platform.openai.audio`
- `platform.openai.image`
- `platform.openai.realtime`

OpenAI 这条线是当前能力最完整的一条，也是很多统一实体设计的参照主线。

### 5.2 兼容 Chat 协议的单能力 provider

例如：

- `platform.zhipu.chat.ZhipuChatService`
- `platform.deepseek.chat.DeepSeekChatService`
- `platform.moonshot.chat.MoonshotChatService`
- `platform.hunyuan.chat.HunyuanChatService`
- `platform.lingyi.chat.LingyiChatService`
- `platform.minimax.chat.MinimaxChatService`
- `platform.baichuan.chat.BaichuanChatService`

这类 provider 的共同点是：

- 当前主要暴露 Chat 入口
- 统一接口已对齐到 `IChatService`
- 具体 URL、鉴权和 JSON 细节都被包在 provider 实现里

### 5.3 兼容 Responses 的 provider

当前包括：

- `platform.openai.response.OpenAiResponsesService`
- `platform.doubao.response.DoubaoResponsesService`
- `platform.dashscope.response.DashScopeResponsesService`

这说明 `Responses` 在 AI4J 里是独立服务线，不是“Chat 的一个模式开关”。

### 5.4 复用标准 `/v1/rerank` 语义的 provider

这里是一个很容易被忽略的源码设计点。

公共适配类：

- `platform.standard.rerank.StandardRerankService`

复用它的 provider：

- `platform.jina.rerank.JinaRerankService`
- `platform.ollama.rerank.OllamaRerankService`

这意味着 AI4J 没有为每个 rerank provider 都重复造一套解析逻辑，而是先抽出一个“标准 rerank 协议层”，再在上面绑定 Jina / Ollama 配置。

### 5.5 特殊协议 provider

例如：

- `platform.doubao.rerank.DoubaoRerankService`

豆包 rerank 不是标准 `/v1/rerank` 协议，因此单独实现了请求体、返回体和 host 解析逻辑。

这正是“统一接口 + 保留 provider 差异”的典型体现：

- 对上游协议差异不装作不存在
- 但对业务层继续暴露统一 `IRerankService`

---

## 6. Spring Boot 配置前缀

`ai4j-spring-boot-starter` 常用前缀：

- `ai.openai.*`
- `ai.doubao.*`
- `ai.dashscope.*`
- `ai.ollama.*`
- `ai.zhipu.*`
- `ai.deepseek.*`
- `ai.moonshot.*`
- `ai.hunyuan.*`
- `ai.lingyi.*`
- `ai.minimax.*`
- `ai.baichuan.*`
- `ai.jina.*`
- 通用网络配置：`ai.okhttp.*`

更完整的 starter 说明继续看：

- [Spring Boot / Auto Configuration](/docs/spring-boot/auto-configuration)

---

## 7. 多实例路由（AiServiceRegistry）

如果你希望按租户/业务 id 动态路由模型实例，优先使用 `AiServiceRegistry`。`FreeAiService` 仍保留兼容静态方法。

```java
IChatService tenantA = aiServiceRegistry.getChatService("tenant-a-openai");
IChatService tenantB = aiServiceRegistry.getChatService("tenant-b-doubao");

// 兼容旧用法
IChatService legacy = FreeAiService.getChatService("tenant-a-openai");
```

适合：多租户、灰度、A/B 测试。

但要记住：

- 注册表只负责按 id 取哪套 `AiService`
- 真正支持哪些能力，仍然由那套 `AiService` 的 `PlatformType` 分发决定

---

## 8. 平台适配层真正统一了什么

统一的是：

- 统一服务接口
- 统一调用入口
- 统一基础请求对象与结果对象
- 统一流式监听模式
- 统一工厂与注册表语义

没有强行统一的是：

- provider 自己的能力覆盖范围
- provider 特有的 endpoint 路径
- provider 的鉴权细节
- 特殊协议（例如豆包 rerank）

这也是 AI4J 的适配设计重点：

- 统一“业务接入方式”
- 不伪造“所有 provider 完全等价”

---

## 9. 工程建议

- 业务层只依赖接口（`IChatService` 等）
- 平台选择逻辑放在配置或工厂层
- 统一日志字段：`platform/service/model/traceId`
- 想扩 provider 时，优先判断能否复用已有标准适配层
- 需要按 id 路由时，用 `AiServiceRegistry`，不要自己在业务层维护 provider map

这样后续切模型或切平台时，业务改动最小。

---

## 10. 对应源码入口

- `ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiService.java`
- `ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiServiceRegistry.java`
- `ai4j/src/main/java/io/github/lnyocly/ai4j/platform/standard/rerank/StandardRerankService.java`
- `ai4j/src/main/java/io/github/lnyocly/ai4j/platform/openai`
- `ai4j/src/main/java/io/github/lnyocly/ai4j/platform/doubao`
- `ai4j/src/main/java/io/github/lnyocly/ai4j/platform/ollama`
- `ai4j/src/main/java/io/github/lnyocly/ai4j/platform/jina`

---

## 11. 下一步阅读

1. [Chat（非流式）](/docs/ai-basics/chat/non-stream)
2. [Responses（非流式）](/docs/ai-basics/responses/non-stream)
3. [Embedding 接口](/docs/ai-basics/services/embedding)
4. [新增 Provider 与模型适配](/docs/ai-basics/provider-and-model-extension)
