---
sidebar_position: 2
---

# 平台与服务矩阵

这页回答两个问题：

1. 当前代码到底支持哪些平台
2. 每个平台支持到哪类服务

> 说明：本页以 `AiService` 当前实现为准。

## 1. 平台枚举

平台枚举定义在 `PlatformType`：

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

## 2. 服务能力矩阵

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

## 3. 统一调用入口

```java
AiService aiService = new AiService(configuration);

IChatService chat = aiService.getChatService(PlatformType.OPENAI);
IResponsesService responses = aiService.getResponsesService(PlatformType.DOUBAO);
IEmbeddingService embedding = aiService.getEmbeddingService(PlatformType.OLLAMA);
IRerankService rerank = aiService.getRerankService(PlatformType.JINA);
IImageService image = aiService.getImageService(PlatformType.DOUBAO);
```

如果平台不支持该服务，会抛出 `IllegalArgumentException`。

## 4. Spring Boot 配置前缀

`ai4j-spring-boot-starter` 对应的常用配置前缀：

- `ai.openai.*`
- `ai.doubao.*`
- `ai.jina.*`
- `ai.dashscope.*`
- `ai.ollama.*`
- `ai.zhipu.*`
- `ai.deepseek.*`
- `ai.moonshot.*`
- `ai.hunyuan.*`
- `ai.lingyi.*`
- `ai.minimax.*`
- `ai.baichuan.*`
- 通用网络：`ai.okhttp.*`

## 5. 多平台实例（AiServiceRegistry）

如果你需要“一个应用内管理多套平台实例（按 id 路由）”，优先使用 `AiServiceRegistry`。`FreeAiService` 仍保留兼容静态方法。

```java
IChatService tenantA = aiServiceRegistry.getChatService("tenant-a-openai");
IChatService tenantB = aiServiceRegistry.getChatService("tenant-b-doubao");

// 兼容旧用法
IChatService legacy = FreeAiService.getChatService("tenant-a-openai");
```

适合：

- 多租户隔离
- 灰度切换模型
- A/B 平台对比
- 统一路由 rerank provider

## 6. 工程建议

- 业务层只依赖接口（`IChatService` 等），不要直连平台实现类。
- 平台选择下沉到配置/工厂层。
- 日志至少记录：`platform + service + model + traceId`。

这样后续换平台时，业务代码改动最小。
