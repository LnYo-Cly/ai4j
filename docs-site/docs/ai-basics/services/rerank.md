---
sidebar_position: 31
---

# Rerank 接口

`Rerank` 解决的不是“召回更多”，而是“把已经召回的候选重新排得更准”。

在 AI4J 里，这层能力被拆成两层：

- 直接调用模型重排服务：`IRerankService`
- 接到 RAG 流水线里做精排：`Reranker` / `ModelReranker`

这样做的目的很直接：

- 你可以单独把 rerank 当搜索能力来用
- 也可以把它接到 `HybridRetriever` 之后，作为 RAG 的后置精排阶段

## 1. 统一入口

### 1.1 直接调用 rerank 模型

```java
IRerankService rerankService = aiService.getRerankService(PlatformType.JINA);

RerankRequest request = RerankRequest.builder()
        .model("jina-reranker-v2-base-multilingual")
        .query("哪些文档更适合回答 Java 8 为什么还在生产环境中常见？")
        .documents(Arrays.asList(
                RerankDocument.builder().id("doc-1").text("Java 8 在很多传统系统中仍然是基线版本").build(),
                RerankDocument.builder().id("doc-2").text("AI4J 提供统一 Chat、Responses 与 RAG 接口").build(),
                RerankDocument.builder().id("doc-3").text("很多企业受中间件和历史依赖约束，升级 JDK 成本较高").build()
        ))
        .topN(2)
        .build();

RerankResponse response = rerankService.rerank(request);
```

常用结果字段：

- `response.getResults()`：重排后的结果列表
- `result.getIndex()`：命中的候选在原始文档列表中的位置
- `result.getRelevanceScore()`：相关性分数
- `result.getDocument()`：返回文档内容（前提是 provider 支持且你开启了 `returnDocuments`）

### 1.2 作为 RAG 精排器使用

```java
Reranker reranker = aiService.getModelReranker(
        PlatformType.JINA,
        "jina-reranker-v2-base-multilingual",
        5,
        "优先保留制度原文、版本说明和编号明确的片段",
        false,
        true
);
```

这里返回的是 `ModelReranker`，它会把 `IRerankService` 转成 RAG 层使用的 `Reranker` 接口。

也就是说：

- `IRerankService` 属于基础模型服务层
- `ModelReranker` 属于 RAG 编排层桥接器

## 2. 支持平台

当前 `AiService#getRerankService(...)` 支持：

- `JINA`
- `OLLAMA`
- `DOUBAO`

要注意两点：

- `JINA` 这一路不只代表 Jina 官方，也代表所有兼容 `/v1/rerank` 协议的服务
- 自托管的 `BAAI/bge-reranker-*`，如果你通过 vLLM 或 Jina-compatible gateway 暴露成 `/v1/rerank`，也直接走 `JINA` 这条接入线

## 3. 三类 provider 的差异

### 3.1 Jina / Jina-compatible

这条线采用标准 `/v1/rerank` 约定，最适合：

- Jina 官方 reranker
- 自托管 `BAAI/bge-reranker-*`
- 任何兼容 Jina 风格请求体的服务

非 Spring 配置：

```java
JinaConfig jinaConfig = new JinaConfig();
jinaConfig.setApiHost("https://api.jina.ai/");
jinaConfig.setApiKey(System.getenv("JINA_API_KEY"));
jinaConfig.setRerankUrl("v1/rerank");

Configuration configuration = new Configuration();
configuration.setJinaConfig(jinaConfig);
configuration.setOkHttpClient(new OkHttpClient());
```

Spring Boot 配置：

```yaml
ai:
  jina:
    api-host: https://api.jina.ai/
    api-key: ${JINA_API_KEY}
    rerank-url: v1/rerank
```

如果你接的是自建兼容服务，只要把 `api-host` 改成你的网关地址即可。

### 3.2 Ollama

Ollama 的 rerank 能力默认走：

- `apiHost`: `http://localhost:11434/`
- `rerankUrl`: `api/rerank`

Spring Boot 配置：

```yaml
ai:
  ollama:
    api-host: http://localhost:11434/
    rerank-url: api/rerank
```

为什么这里把 `rerankUrl` 暴露成配置项：

- 上游不同版本和代理网关的路径稳定性不如 Chat / Embedding 清晰
- 直接做成可配置，方便你接本地、代理或企业网关

### 3.3 豆包 / 火山方舟知识库重排

豆包这条线不是标准 `/v1/rerank` 协议，而是知识库服务的专用接口，因此额外拆了：

- `rerankApiHost`
- `rerankUrl`

Spring Boot 配置：

```yaml
ai:
  doubao:
    api-key: ${ARK_API_KEY}
    rerank-api-host: https://api-knowledgebase.mlp.cn-beijing.volces.com/
    rerank-url: api/knowledge/service/rerank
```

这意味着：

- `chat/responses` 走 `apiHost`
- `rerank` 可以单独走知识库服务域名

不要把它误认为和 OpenAI/Jina 一样的统一路径。

## 4. 请求对象说明

核心请求对象是 `RerankRequest`。

最常用字段：

- `model`：重排模型名
- `query`：查询文本
- `documents`：候选文档列表
- `topN`：只返回前 N 条
- `instruction`：对重排行为的补充指令
- `returnDocuments`：是否让上游在结果里回传文档内容
- `extraBody`：透传 provider 自定义参数

文档项使用 `RerankDocument`：

- `id`
- `text` / `content`
- `title`
- `metadata`
- `image`

如果你只是做纯文本重排，最常用的字段其实只有：

- `query`
- `documents[].text`
- `topN`

## 5. 与混合检索的关系

推荐把这几个概念分开：

- `DenseRetriever`：语义召回
- `Bm25Retriever`：关键词召回
- `HybridRetriever`：多路召回合并
- `FusionStrategy`：RRF / RSF / DBSF 等融合算法
- `Reranker`：候选集合的后置精排

标准链路通常是：

```text
Dense / BM25 召回
  -> HybridRetriever 融合
  -> 可选 ModelReranker 精排
  -> RagContextAssembler 拼上下文
  -> 模型回答
```

要点是：

- `RRF/RSF/DBSF` 不是 rerank 模型
- `Reranker` 也不是必须使用
- 如果你追求更高的 topK 精度，再接 `ModelReranker`

## 6. 在 RAG 里怎么拿到精排结果

接入 `ModelReranker` 后，运行时可直接看到：

- `RagHit.rerankScore`
- `RagHit.rank`
- `RagHit.scoreDetails`
- `RagResult.trace.rerankedHits`

所以你可以同时做两件事：

- 在线排查“为什么这条排在前面”
- 离线结合 `RagEvaluator` 看 `Precision@K/Recall@K/F1@K/MRR/NDCG`

这也是为什么 AI4J 没把 rerank 写死在某个向量库里，而是单独做成统一服务层。

## 7. 什么时候直接用 `IRerankService`

适合：

- 搜索结果二次排序
- FAQ 候选精排
- 自己已有召回链路，只缺一个统一重排层
- 想把 rerank 独立成服务，不绑定 RAG

不一定适合：

- 候选数很少、延迟很敏感
- 只做最小 RAG demo
- 召回质量本身就很差，先修召回比先加 rerank 更重要

## 8. 推荐阅读

1. [统一服务入口与调用方式](/docs/ai-basics/unified-service-entry)
2. [平台与服务能力矩阵](/docs/getting-started/platforms-and-service-matrix)
3. [RAG 与知识库增强总览](/docs/ai-basics/rag/overview)
4. [RAG 架构、分块与索引设计](/docs/ai-basics/rag/architecture-and-indexing)
