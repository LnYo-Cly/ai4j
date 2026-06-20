# Rerank

`rerank` 在 AI4J 里的位置很容易被写浅。  
它既不是“第二个 retriever”，也不是“最后给模型的 prompt 优化器”，而是夹在 **retrieval** 和 **context assembly** 之间的一层排序修正。

从源码看，这一层的职责非常具体：

- 输入一组已经召回的 `RagHit`
- 根据 query 再排一次序
- 可选保留原始命中尾部
- 把 rerank 分数写回结果

## 1. 源码入口在哪里

这一页最关键的类有：

- `rag/DefaultRagService.java`
- `rag/Reranker.java`
- `rag/NoopReranker.java`
- `rag/ModelReranker.java`
- `rag/RagHitSupport.java`
- `service/IRerankService.java`

provider 侧还有几个实现：

- `platform/standard/rerank/StandardRerankService.java`
- `platform/doubao/rerank/DoubaoRerankService.java`
- `platform/jina/rerank/JinaRerankService.java`
- `platform/ollama/rerank/OllamaRerankService.java`

但真正把 rerank 接进 RAG 主链的，是 `DefaultRagService.search(...)`。

## 2. 在默认 RAG 链里，rerank 精确发生在哪一步

`DefaultRagService.search(query)` 的主线是：

1. `retriever.retrieve(query)`
2. `RagHitSupport.prepareRetrievedHits(...)`
3. 拷贝一份 hits 作为 `rerankInput`
4. `reranker.rerank(query.getQuery(), rerankInput)`
5. `RagHitSupport.prepareRerankedHits(...)`
6. `trim(..., query.finalTopK)`
7. `contextAssembler.assemble(query, finalHits)`

这条顺序很重要，因为它说明：

- rerank 是在 retrieval 之后
- rerank 是在 context 拼装之前
- `finalTopK` 是在 rerank 之后才裁剪

所以如果你只把 `topK` 调大，但不做 rerank，最后给模型的上下文不一定更准；  
反过来，如果你 rerank 了，但 `finalTopK` 太小，也可能把有价值的尾部命中再次截断。

## 3. 为什么默认给你的是 `NoopReranker`

`DefaultRagService` 的默认构造器是：

```java
this(retriever, new NoopReranker(), new DefaultRagContextAssembler());
```

`NoopReranker` 的实现也很直白：

- hits 为空就返回空
- 否则原样拷贝一份返回

这说明框架作者在这一层的默认态度是：

**Rerank 是可选增强，不是 RAG 链路成立的前置条件。**

这么做有两个工程好处：

- 没有额外服务时，链路照样可用
- 你不会被强制绑到某个 rerank provider

但代价也很明显：

- 纯 dense / hybrid 的前几名，不一定是最终最适合回答的问题上下文
- query 与 chunk 的细粒度相关性修正，默认并没有发生

## 4. `ModelReranker` 到底怎么工作

`ModelReranker` 当前实现是最值得看的主干。

它做的事情分 6 步：

1. 校验 `rerankService` 和 `model` 必填
2. 如果 hits 为空，直接返回空
3. 如果 query 为空，直接返回原 hits 拷贝
4. 把 `RagHit` 转成 `RerankDocument`
5. 调 `IRerankService.rerank(...)`
6. 根据 provider 返回的 `index` 把结果映射回原始命中

这里有几个关键实现细节。

### 4.1 文档主键用的是 `stableKey`

`ModelReranker` 给 `RerankDocument.id` 填的是：

```java
RagHitSupport.stableKey(hit)
```

这和 hybrid 去重使用的稳定 key 逻辑是一脉相承的。  
也就是说，命中标识是否稳定，会同时影响：

- hybrid 合并
- rerank 文档标识
- trace 排障

### 4.2 结果映射靠的是 provider 返回的 `index`

`ModelReranker` 真正回写命中时，不是按文档 id 查，而是按返回结果的 `index` 去原始 hits 里取。

这意味着 provider 结果必须和送入 `documents` 的顺序保持一致语义。  
如果某个 provider：

- 返回了空 index
- index 越界
- 顺序语义不一致

这些结果都会被忽略。

### 4.3 `appendRemainingHits` 默认是 `true`

默认构造器里：

```java
this(rerankService, model, null, null, false, true);
```

也就是说，即便 rerank provider 只返回了前 `topN` 个结果，剩余未被 rerank 命中的文档，默认也会按原顺序追加回尾部。

这个默认值有利于“尽量不丢召回”，但也意味着：

- rerank 并不是强裁剪
- 你后面最好配合 `finalTopK`
- 否则最终结果里仍然可能混入未真正重排过的尾部命中

## 5. `topN` 和 `finalTopK` 不是一回事

这是最常见的概念混淆。

`ModelReranker.topN` 表示：

- 发给 rerank provider 时，希望它重点返回多少条结果

`RagQuery.finalTopK` 表示：

- `DefaultRagService` 在 rerank 之后，最终保留多少条 hits

两者不相等时，表现会很不一样：

- `topN < finalTopK`：尾部可能来自 `appendRemainingHits`
- `topN > finalTopK`：rerank 做了更多工作，但最终被再裁掉
- `topN = null`：默认按全部 hits 做 rerank

所以生产里通常不要只调一个数字，不看另一个。

## 6. rerank 后哪些字段会变化

`RagHitSupport.prepareRerankedHits(...)` 会把两份数据合并：

- 原始 retrieved hits
- reranker 返回的 hits

然后做几件事：

- 合并 metadata 与来源字段
- 若 rerank 生效，则写 `rerankScore`
- 重新分配 `rank`
- 用 `normalizeEffectiveScore(...)` 把 `score` 归一成当前阶段有效分

当前有效分优先级是：

1. `rerankScore`
2. `fusionScore`
3. `retrievalScore`
4. `score`

这意味着 rerank 一旦生效，`score` 的含义就变了。  
所以排障时你不能只说“这个命中 score 高”，还要问：

- 这是 retrieval score？
- fusion score？
- 还是 rerank score？

## 7. `returnDocuments` 会带来什么副作用

`ModelReranker` 支持：

- `returnDocuments`

如果打开它，并且 provider 返回了 `document.content`，代码会：

```java
copy.setContent(result.getDocument().getContent());
```

这意味着 rerank 阶段不仅可能改顺序，还可能改内容。

这个能力很强，但也要谨慎：

- 如果 provider 返回的是清洗后内容，citation snippet 可能变
- 如果 provider 对原文做了截断或标准化，trace 对比会更难解释

所以默认值是 `false`，是合理的保守策略。

## 8. Trace 到底能看到什么

当 `query.includeTrace = true` 时，`DefaultRagService` 会把：

- `retrievedHits`
- `rerankedHits`

塞进 `RagTrace`。

这不是“模型最终回答 trace”，而是 **RAG 排序链 trace**。

它能帮助你回答的问题是：

- 哪些命中被召回了
- rerank 后顺序怎么变了
- 某条命中的分数语义怎么变化了

它不能直接告诉你：

- 模型为什么最终用了某条上下文
- provider 内部是怎么算 rerank 的
- prompt 阶段发生了什么

## 9. 当前这一层没有替你做什么

AI4J 的 rerank 层当前没有直接替你做：

- query rewrite
- 业务规则过滤
- 按 source 白名单/黑名单打分
- 失败自动降级重试
- 并行比较多个 rerank provider

它提供的是一个很清楚的框架位点，让你把“二次相关性排序”插进 RAG 主链。

## 10. 最容易踩坑的 5 个点

### 10.1 把 rerank 当成 retriever

Rerank 不负责召回新文档，它只重新排列已经召回出来的 hits。

### 10.2 忘了 `embedding` / `hybrid` 质量仍然是上游前提

如果召回集合本身就错了，rerank 也救不回来。

### 10.3 只开 rerank，不设 `finalTopK`

这样尾部未重排命中可能仍会一路进入 context。

### 10.4 误读 `score`

Rerank 生效后，`score` 通常已经不是原始检索分。

### 10.5 以为默认链路已经“自动接了大模型重排”

默认其实是 `NoopReranker`，没有任何远端重排服务。

## 11. 什么时候值得引入 rerank

下面这些场景，通常值得上 rerank：

- dense/hybrid 能召回，但前几条排序经常不稳
- 文档块比较大，需要更强 query-aware 排序
- 你想把召回集先放宽，再用更贵的模型精排
- 你已经有 trace / 评估体系，能量化排序收益

反过来，如果你的语料很小、query 很简单、召回本身已经稳定，默认 `NoopReranker` 也是完全合理的起点。

## 12. 这页最该记住的结论

AI4J 当前的 rerank，不是另一套检索框架，而是 `DefaultRagService` 中 retrieval 与 context assembly 之间的一层排序修正：

- 默认不开启远程重排，走 `NoopReranker`
- 真正的模型重排由 `ModelReranker + IRerankService` 完成
- `score` 会在这一层被改写成 rerank 有效分
- `finalTopK` 是在 rerank 之后才生效

把这一层看清楚之后，你再调 dense、hybrid、citation 才不会混层。

## 13. 继续阅读

- [Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)
- [Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)
- [Embedding](/docs/core-sdk/search-and-rag/embedding)
