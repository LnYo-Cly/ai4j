---
sidebar_position: 30
---

# Embedding 接口

Embedding 在 AI4J 里不是孤立能力，它是 `DenseRetriever`、`IngestionPipeline` 和向量存储主线的基础输入层。  
这一页讲的是 **AI4J 当前如何统一 embedding 请求和返回，以及它保留了哪些 provider 差异**。

## 1. 当前支持矩阵

从 `AiService.createEmbeddingService(...)` 的实际分发看，当前 embedding 只支持：

- `OPENAI`
- `OLLAMA`

这点很重要，因为它意味着 embedding 目前不是“所有 chat provider 自动都有”的能力面，而是一条更窄的支持矩阵。

## 2. 统一契约长什么样

统一入口是：

- `IEmbeddingService`

它定义了两种调用方式：

- `embedding(String baseUrl, String apiKey, Embedding embeddingReq)`
- `embedding(Embedding embeddingReq)`

这说明 embedding 层默认有一套配置内回退逻辑，但也允许你对单次调用显式覆盖：

- base URL
- API key

## 3. 请求对象的真实形态

请求对象是：

- `platform/openai/embedding/entity/Embedding.java`

它当前的几个关键字段是：

- `input`
- `model`
- `encodingFormat`
- `dimensions`
- `user`

其中最值得注意的是 `input` 被设计成 `Object`，但 builder 实际只暴露了两条安全路径：

- `input(String)`
- `input(List<String>)`

也就是说，这一层明确支持：

- 单条文本向量化
- 批量文本向量化

## 4. OpenAI 路径的真实行为

`OpenAiEmbeddingService` 是一条比较薄的实现：

- 从 `OpenAiConfig` 读取默认 `apiHost` / `apiKey`
- 空参数时回退到配置值
- 直接把 `Embedding` JSON 序列化后 POST 到 `embeddingUrl`
- 成功时解析成 `EmbeddingResponse`
- 失败时直接返回 `null`

这意味着它做了统一协议封装，但没有在失败时构建复杂异常对象。  
如果你在业务层直接拿返回值用，应该先考虑 `null` 分支，而不是假设失败一定抛异常。

## 5. Ollama 路径的真实行为

`OllamaEmbeddingService` 的价值比表面看起来更大，因为它不只是“改个 URL”，而是做了 **协议转换**。

### 请求转换

它把统一 `Embedding` 请求转换为：

- `OllamaEmbedding`

并且显式兼容：

- 单条字符串输入
- `List<String>` 批量输入

### 返回转换

它把 `OllamaEmbeddingResponse` 转成统一 `EmbeddingResponse`：

- `object = "list"`
- `data[i].embedding`
- `data[i].index`
- `model`
- `usage`

其中 `usage` 来自 `promptEvalCount`，会被映射成：

- `promptTokens`
- `totalTokens`

这说明 Ollama 路径虽然底层协议不同，但在 SDK 外层仍然被收敛成和 OpenAI 类似的 embedding 响应心智。

## 6. 这层和 RAG 主线怎么接

当前仓库里 embedding 的核心下游不是“只给你一个向量”，而是：

- `DenseRetriever`
- `IngestionPipeline`

这两处都直接依赖 `IEmbeddingService`。

### `DenseRetriever`

会：

- 对 query 生成 embedding
- 读取第一条向量
- 交给 `VectorStore` 执行相似检索

### `IngestionPipeline`

会：

- 按批次生成 chunk embeddings
- 校验返回向量数是否与 chunk 数一致
- 再把向量和 metadata 写进向量存储

所以 embedding 页真正该建立的心智是：它是 RAG 链路的统一向量入口，而不是孤立 API。

## 7. 当前实现里要特别注意的行为

### 失败可能返回 `null`

OpenAI 和 Ollama 这两个实现都在 HTTP 非成功或 body 为空时返回 `null`。  
这意味着调用方要自己决定：

- 是否做重试
- 是否做错误转换
- 是否在上层显式校验返回结构

### 批量返回顺序依赖 provider 语义

`EmbeddingResponse` 通过 `EmbeddingObject.index` 表示顺序。  
如果你在业务层做批量入库，不要只按“返回数组刚好顺序正确”来假设，而应使用统一返回对象。

### 维度一致性不是 SDK 自动治理的

`Embedding` 里虽然有 `dimensions` 字段，但向量维度一致性最终仍是你的索引设计责任。  
同一索引混用不同模型或不同维度，后果要由业务层承担。

## 8. 什么时候这一层不够

如果你开始关心：

- chunk 切分策略
- metadata 映射
- 入库批量大小
- 检索排序与 rerank

那说明你已经进入 `search-and-rag` 主线，而不只是 embedding 接口本身。

## 9. 这一页的结论

> AI4J 的 embedding 层当前是一条窄而明确的能力面：OpenAI 和 Ollama 共用统一 `Embedding` / `EmbeddingResponse` 契约，Ollama 通过协议转换被收敛进同一返回心智，而真正的业务价值主要体现在它作为 `DenseRetriever` 和 `IngestionPipeline` 的统一向量入口。
