---
sidebar_position: 2
---

# 平台适配与统一接口

这一页对应 ai4j 的核心价值：**跨平台协议消歧**。

## 1. 统一入口

你只需要面对统一服务接口，不直接依赖某家平台 SDK。

```java
AiService aiService = new AiService(configuration);

IChatService chat = aiService.getChatService(PlatformType.OPENAI);
IResponsesService responses = aiService.getResponsesService(PlatformType.DOUBAO);
IEmbeddingService embedding = aiService.getEmbeddingService(PlatformType.OLLAMA);
IImageService image = aiService.getImageService(PlatformType.DOUBAO);
```

如果某平台不支持该服务，`AiService` 会抛出 `IllegalArgumentException`。

## 2. 平台枚举（PlatformType）

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

## 3. 服务能力矩阵（当前实现）

| 平台 | Chat | Responses | Embedding | Audio | Realtime | Image |
| --- | --- | --- | --- | --- | --- | --- |
| OPENAI | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| DOUBAO | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ |
| DASHSCOPE | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| OLLAMA | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ |
| ZHIPU | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| DEEPSEEK | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| MOONSHOT | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| HUNYUAN | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| LINGYI | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| MINIMAX | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| BAICHUAN | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |

## 4. Spring Boot 配置前缀

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
- 通用网络配置：`ai.okhttp.*`

## 5. 多实例路由（AiServiceRegistry）

如果你希望按租户/业务 id 动态路由模型实例，优先使用 `AiServiceRegistry`。`FreeAiService` 仍保留兼容静态方法。

```java
IChatService tenantA = aiServiceRegistry.getChatService("tenant-a-openai");
IChatService tenantB = aiServiceRegistry.getChatService("tenant-b-doubao");

// 兼容旧用法
IChatService legacy = FreeAiService.getChatService("tenant-a-openai");
```

适合：多租户、灰度、A/B 测试。

## 6. 工程建议

- 业务层只依赖接口（`IChatService` 等）
- 平台选择逻辑放在配置或工厂层
- 统一日志字段：`platform/service/model/traceId`

这样后续切模型或切平台时，业务改动最小。
