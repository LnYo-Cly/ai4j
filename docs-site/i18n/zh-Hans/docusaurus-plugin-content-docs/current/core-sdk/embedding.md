---
sidebar_position: 30
---

# Embedding 接口

Embedding 是 RAG 的基础能力，ai4j 统一为 `IEmbeddingService`。

## 1. 支持平台

当前 `AiService#getEmbeddingService(...)` 支持：

- `OPENAI`
- `OLLAMA`

## 2. 最小示例

```java
IEmbeddingService embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);

Embedding request = Embedding.builder()
        .model("text-embedding-3-small")
        .input("Explain JVM class loading")
        .build();

EmbeddingResponse response = embeddingService.embedding(request);
List<Float> vector = response.getData().get(0).getEmbedding();
System.out.println(vector.size());
```

## 3. 批量向量化

`input` 可传 `List<String>`，用于批量生成向量：

```java
Embedding request = Embedding.builder()
        .model("text-embedding-3-small")
        .input(Arrays.asList("文档片段1", "文档片段2", "文档片段3"))
        .build();
```

## 4. Ollama 兼容细节

`OllamaEmbeddingService` 会把 Ollama 返回结果转换成 OpenAI 风格 `EmbeddingResponse`：

- `data[i].embedding`
- `usage`
- `model`

这就是“协议消歧”的核心体现。

## 5. 参数与模型建议

- 固定一个 embedding 模型，不要混用
- 同一索引内向量维度必须一致
- 批量请求优先控制单批大小，避免超时

## 6. 常见问题

### 6.1 向量维度不一致

通常是模型变更导致，需重新建索引或重建数据。

### 6.2 返回为空

- API key 未配置
- 模型名无效
- 上游限流或超时

### 6.3 批量很慢

建议：

- 分批并发（但注意限流）
- 缓存重复文本的向量结果

## 7. 与 Pinecone 的配合

Embedding 产物通常直接写入 Pinecone：

- `List<List<Float>>` -> 向量
- 原文 -> metadata.content

完整流程见：`Pinecone 向量检索工作流`。
