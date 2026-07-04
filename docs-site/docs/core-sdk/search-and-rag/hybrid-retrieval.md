# Hybrid Retrieval

在 AI4J 里，`hybrid retrieval` 不是一个“神秘黑盒检索器”，而是一个非常具体的组合器：

- 先让多个 `Retriever` 各自出结果
- 再把这些结果做去重、融合、重排
- 最后产出一份统一的 `List<RagHit>`

这页要讲清的重点是：**AI4J 当前的 hybrid 本质上是检索结果融合，不是多阶段 agent。**

## 1. 源码入口在哪里

先看 6 个核心类：

- `rag/HybridRetriever.java`
- `rag/Retriever.java`
- `rag/DenseRetriever.java`
- `rag/Bm25Retriever.java`
- `rag/RrfFusionStrategy.java`
- `rag/RagHitSupport.java`

如果只看文档名字，你很容易把 hybrid 理解成“Dense + BM25 的固定产品能力”。但从实现看，它其实只是：

```java
public class HybridRetriever implements Retriever
```

也就是说，它仍然服从普通 `Retriever` 契约。上层 `DefaultRagService` 并不关心它里面有几个子检索器，只把它当成一个检索实现来调用。

## 2. 一次 hybrid 检索真实会经过什么链路

`HybridRetriever.retrieve(query)` 当前执行顺序很清晰：

1. 遍历构造时传入的 `retrievers`
2. 每个子 `retriever` 各自执行 `retrieve(query)`
3. 用 `RagHitSupport.prepareRetrievedHits(...)` 规范化命中结果
4. 让 `FusionStrategy` 为每个命中位置计算融合贡献值
5. 按稳定 key 合并同一命中
6. 把融合分数写回 `RagHit`
7. 按最终分数倒序排序
8. 按 `query.topK` 裁剪结果

如果你想抓主线，最该记住的是这一句：

**HybridRetriever 不负责“找知识”，它负责“合并多个找知识的结果”。**

## 3. 默认并不是“Dense + BM25 固定套餐”

构造器是：

```java
new HybridRetriever(List<Retriever> retrievers)
```

默认只给你两件事：

- 把一组 `Retriever` 组合起来
- 如果你不指定融合策略，就用 `RrfFusionStrategy`

它并不会强制要求：

- 必须有 dense
- 必须有 bm25
- 必须一稠密一稀疏

所以严格说，AI4J 的 hybrid 更准确的说法是：

**多检索器结果融合器。**

如果你只传了一个 `Retriever`，代码也能跑，只是这时“hybrid”在工程上就没有什么意义了。

## 4. 默认融合算法到底是什么

默认构造会走：

```java
new RrfFusionStrategy()
```

而 `RrfFusionStrategy` 默认 `rankConstant = 60`，贡献公式实际上是：

```java
1.0 / (rankConstant + rank)
```

也就是典型的 Reciprocal Rank Fusion 思路。

这个默认值有一个很重要的后果：

**默认 hybrid 更看重“某命中在各检索器中的名次”，而不是原始分数绝对值。**

这意味着：

- 一个 dense 分数很高但排在第 8 的命中，不一定赢得过一个 bm25 第 1 名
- 不同检索器之间不需要先把分数归一到同一量纲
- 融合更稳，但会牺牲一部分“原始相似度幅度信息”

如果你希望保留更多原始 score 语义，就要自己换 `FusionStrategy`，而不是指望默认 RRF 替你做这件事。

## 5. “同一个命中” 是怎么判定的

这是 hybrid 实现里最容易被忽略、但最影响结果质量的地方。

`HybridRetriever.keyOf(hit, fallbackIndex)` 的 key 优先级大致是：

1. `hit.id`
2. `documentId + chunkIndex`
3. `sourcePath + chunkIndex`
4. `sourceUri + chunkIndex`
5. `sourceName + sectionTitle + chunkIndex`
6. `content`
7. fallback index

这意味着 hybrid 的去重质量，非常依赖你前面 ingest 和检索阶段是否给了稳定标识。

如果你的 `RagHit`：

- 没有 `id`
- `documentId` 不稳定
- `chunkIndex` 每次切块都变
- 或者不同来源只是内容碰巧相同

那么融合结果就可能出现两种问题：

- 本该合并的命中没有合并
- 本不该合并的命中被误合并

所以 hybrid 的质量，不只是算法问题，还是 **标识设计问题**。

## 6. 融合后 `RagHit` 上哪些字段会变

融合完成后，AI4J 会写回几组关键信息：

- `retrieverSource = "hybrid"`
- `retrievalScore = bestRetrievalScore`
- `fusionScore = 累加后的融合分`
- `score = fusionScore`
- `scoreDetails = 每个子检索器的来源、排名、原始检索分、融合贡献`

这里要特别注意 `score` 的语义。

在 dense 检索时，`score` 更像向量检索分；在 rerank 后，`score` 又会被改成 rerank 分；而在 hybrid 结果里，`score` 已经变成融合后的有效分数。  
所以看 `RagHit.score` 时，永远不要脱离阶段去理解。

最稳的做法是同时看：

- `retrievalScore`
- `fusionScore`
- `rerankScore`
- `scoreDetails`

## 7. Dense 和 BM25 在这条链里各自扮演什么角色

`DenseRetriever` 的逻辑是：

1. 用 `IEmbeddingService` 为 query 生成向量
2. 调 `VectorStore.search(...)`
3. 把返回的 `VectorSearchResult` 转成 `RagHit`

它依赖：

- `query.embeddingModel`
- `query.dataset`
- 向量库里的 metadata 质量

`Bm25Retriever` 的逻辑则完全不同：

1. 基于内存 `corpus` 建局部 BM25 索引
2. 对 query 做分词
3. 计算词频、逆文档频率、长度归一化分数
4. 输出按 score 排序的命中

它不依赖 embedding，也不依赖外部向量库。

所以 hybrid 组合的真正价值不是“把两个流行名词拼起来”，而是把：

- dense 的语义召回
- sparse / bm25 的词项匹配能力

放进同一条融合链。

## 8. `topK` 到底在哪几层生效

这个点很容易理解错。

`query.topK` 在当前实现里至少会影响两层：

1. 每个子 `Retriever` 自己返回多少结果
2. `HybridRetriever` 融合后最终保留多少结果

如果后面再交给 `DefaultRagService`，还会有第三层：

3. `query.finalTopK` 在 rerank 之后再次裁剪

所以当你觉得“hybrid 召回太少”时，不要只盯着一层看。可能是：

- dense 子检索器先裁掉了
- bm25 子检索器先裁掉了
- hybrid 融合后又裁掉了
- rerank 后 `finalTopK` 再裁了一次

## 9. 当前实现没有做什么

AI4J 这层 hybrid 很实用，但边界也很明确。它当前没有直接做：

- 并行执行多个子检索器
- 查询改写或查询扩展（这类检索前处理归到 `RagQueryPlanner`）
- 基于业务规则的二次过滤
- rerank
- context 拼装

也就是说，它只解决“多路检索结果如何合并”，不解决“查询如何变聪明”。

## 10. 最容易踩坑的 4 个点

### 10.1 把默认 RRF 当成分数融合

默认实现主要吃 rank，不是吃原始 score 幅度。不要误以为 dense 的 0.91 和 bm25 的 17.4 会被直接做数值比较。

### 10.2 命中没有稳定 id

没有稳定标识，hybrid 的去重质量会明显下降，`scoreDetails` 也会变得难解释。

### 10.3 只看最终 `score`

融合后的 `score` 已经不是底层检索分。排障时一定要把 `scoreDetails` 一起看。

### 10.4 以为 hybrid 之后就不需要 rerank

hybrid 解决的是“多源召回融合”，不是“面向 query 的最终相关性排序”。这两层不是互斥关系。

## 11. 什么时候该扩展它

如果你遇到下面这些情况，就应该考虑扩展而不是硬用默认值：

- 不同检索器分数语义你想保留得更明显
- 你有域内强规则，需要按 source 设权重
- 你的命中 key 需要更稳定的业务主键
- 你想让 hybrid 兼容不止 dense/bm25 两路

扩展点主要有两个：

- 自定义 `Retriever`
- 自定义 `FusionStrategy`

而不是去改 `DefaultRagService`。

## 12. 这页最该记住的结论

AI4J 当前的 hybrid retrieval，本质上是一个 **`Retriever` 级结果融合器**：

- 它接收多个子检索器
- 默认用 RRF 按排名贡献做融合
- 用稳定 key 去重
- 把融合后的分数和细节写回 `RagHit`

理解这一点后，你就不会把 hybrid、rerank、context assembly、online search 误写成同一层能力。

## 13. 继续阅读

- [Query Planning](/docs/core-sdk/search-and-rag/query-planning)
- [Rerank](/docs/core-sdk/search-and-rag/rerank)
- [Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)
- [Vector Store and Backends](/docs/core-sdk/search-and-rag/vector-store-and-backends)
