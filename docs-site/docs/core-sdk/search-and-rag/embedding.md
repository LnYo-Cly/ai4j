# Embedding

在 AI4J 当前实现里，`embedding` 不是一个孤立 API，而是离线 RAG 两端都在依赖的公共底座：

- ingest 时，它把 chunk 变成向量
- query 时，它把问题变成向量

如果这一层理解得不准，你后面看 `DenseRetriever`、`IngestionPipeline`、`VectorStore` 都会失真。

## 1. 抽象入口其实非常薄

统一接口只有一个：

- `service/IEmbeddingService.java`

签名也很克制：

```java
EmbeddingResponse embedding(String baseUrl, String apiKey, Embedding embeddingReq)
EmbeddingResponse embedding(Embedding embeddingReq)
```

这意味着 AI4J 在 embedding 层的设计取向是：

- 统一输入输出结构
- 把 provider 差异压到实现类
- 不在接口层提前引入 RAG 语义

也就是说，embedding 层本身并不知道：

- dataset 是什么
- chunk 是什么
- 检索要怎么做

它只负责“把输入文本转成向量”。

## 2. 当前工厂默认支持哪些 embedding provider

`AiService.createEmbeddingService(platform)` 目前只支持：

- `OPENAI`
- `OLLAMA`

这一点很值得在文档里讲透，因为它和聊天模型支持面并不一致。  
也就是说，AI4J 当前平台支持面里：

- chat provider 比 embedding provider 更多
- rerank provider 又是另一套支持集合

所以“这个平台能聊天”并不等于“这个平台也能直接作为 embedding 提供方”。

## 3. embedding 在 ingest 链里是怎么被调用的

真正把 embedding 接进入库主线的是：

- `rag/ingestion/IngestionPipeline.java`

在 `buildRecords(...)` 前，它会先做：

```java
List<List<Float>> vectors = embed(contents, request.getEmbeddingModel(), request.getBatchSize());
```

这个 `embed(...)` 方法有几个很关键的实现细节：

- 默认批大小 `DEFAULT_BATCH_SIZE = 32`
- 每批把 `List<String>` 作为一次 `Embedding.input` 发送
- 从 `EmbeddingResponse.data` 中按 `EmbeddingObject.index` 重新组装顺序
- 如果返回数量不足，会直接抛异常

这说明 AI4J 当前的 ingest embedding 不是“来一个 chunk 打一次请求”，而是已经做了基础批处理和顺序恢复。

## 4. 为什么 `EmbeddingObject.index` 很关键

`IngestionPipeline.extractEmbeddings(...)` 会把 provider 返回结果按 `index` 放回原顺序。

这个实现细节决定了：

- provider 可以按 batch 返回多个向量
- 但最终必须能恢复到原请求文本顺序

如果返回里：

- `index` 缺失过多
- 向量数量少于 batch 大小
- 某个 index 缺向量

当前实现都会抛 `IllegalStateException`。

这比“静默跳过坏数据”更严格，但对 RAG ingest 是正确取向，因为：

- 一旦向量顺序错位
- 后面的 `VectorRecord` 就会把错误向量写到错误 chunk 上

这种错比直接失败更难查。

## 5. embedding 在 query 链里是怎么被调用的

查询侧真正使用 embedding 的是：

- `rag/DenseRetriever.java`

它会：

1. 校验 `query.query` 非空
2. 强制要求 `query.embeddingModel` 非空
3. 调 `embeddingService.embedding(...)` 生成 query 向量
4. 把向量交给 `VectorStore.search(...)`

这说明 AI4J 的 dense retrieval，不是把 embedding 藏在 vector store 内部，而是显式拆成两步：

- query to vector
- vector to hits

这种设计的好处是层次清楚；代价是 embedding 模型一致性要由你自己治理。

## 6. 为什么“同一模型一致性”在这里不是建议，而是约束

从当前实现看，ingest 和 query 侧都只认你传入的 embedding model 名称。  
框架不会自动检查：

- 你入库时用的是否和查询时一致
- 维度是否匹配
- 索引里是否混入了多套 embedding 体系

这意味着一旦你：

- 用 A 模型入库
- 用 B 模型查询

即便代码能跑通，检索质量也很可能直接失真，甚至后端因为维度不一致而报错。

所以 embedding 在 AI4J 当前语义下，不是“随时可替换的 provider 配置”，而是：

**索引级协议的一部分。**

## 7. embedding 和 `dataset` 为什么不在同一层

这个结构设计也值得说透。

`IEmbeddingService` 不关心 `dataset`，因为：

- dataset 是向量存储和检索边界
- embedding 只是文本到向量的变换

所以在当前实现里：

- `IngestionPipeline` 先做 embedding
- 再把向量和 metadata 组装成 `VectorRecord`
- 最后连同 `dataset` 一起交给 `VectorStore.upsert(...)`

这种分层是合理的。  
因为一旦让 embedding 层感知 dataset，它就开始和存储策略耦合了。

## 8. 当前实现最容易踩的 5 个点

### 8.1 以为聊天模型可用，embedding 也一定可用

当前 `AiService` 对 embedding 的 provider 支持明显更窄。

### 8.2 ingest 和 query 用不同 embedding model

这是最常见、也最隐蔽的 RAG 质量问题来源。

### 8.3 忽略 batch 行为

`IngestionPipeline` 默认按 32 条分批，不同 provider 的吞吐、限流、超时特征都可能在这里暴露。

### 8.4 只看 provider 返回成功，不校验向量数量和顺序

AI4J 当前实现是严格校验的，这一点不要在上层包装时给“优化掉”。

### 8.5 把 embedding 当成 RAG 质量的唯一决定因素

chunking、metadata、dataset、retrieval、rerank 都同样重要。

## 9. 当前这一层没有替你做什么

Embedding 层当前没有直接替你解决：

- 文本清洗
- chunk 边界设计
- 向量缓存
- 模型版本治理
- 维度迁移
- 召回效果评测

它的职责非常克制，就是统一 provider 调用与结果承接。

## 10. 从当前源码看，最稳的使用建议

如果你要在 AI4J 上做稳定 RAG，embedding 层最稳的做法是：

1. 先固定一套 embedding model
2. 用它完整跑通 ingest 与 query
3. 把模型名视为索引协议的一部分
4. 更换模型时按新 dataset / 新索引重建

不要一边沿用旧向量，一边改查询模型。  
在当前架构下，这种混用不会被自动阻止。

## 11. 这页最该记住的结论

AI4J 当前的 embedding，是离线 RAG 的共享底层变换层：

- ingest 侧靠它把 chunk 批量转成向量
- query 侧靠它把问题转成向量后再交给 `VectorStore`

接口本身很薄，真正重要的是它在主链里的位置和一致性约束。  
把它看成“索引协议的一部分”，比把它看成“随时可切换的小配置”更准确。

## 12. 继续阅读

- [Ingestion Pipeline](/docs/core-sdk/search-and-rag/ingestion-pipeline)
- [Vector Store and Backends](/docs/core-sdk/search-and-rag/vector-store-and-backends)
- [Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)
