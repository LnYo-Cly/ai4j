# Chunking Strategies

很多 RAG 文档会把 `chunking` 写成“把长文切成几段”。  
在 AI4J 里，这样写太轻了。因为从源码看，chunking 决定的不只是文本长度，还决定：

- 命中的边界是否稳定
- `documentId + chunkIndex` 是否可复用
- 引用能不能回到正确段落
- metadata 能不能一路传到检索结果

所以这页真正要讲的是：**AI4J 当前默认 chunker 具体做了什么，以及它没有替你做什么。**

## 1. 源码入口在哪里

当前最重要的入口有 4 个：

- `rag/ingestion/Chunker.java`
- `rag/ingestion/RecursiveTextChunker.java`
- `rag/RagChunk.java`
- `document/RecursiveCharacterTextSplitter`

默认实现是：

```java
public class RecursiveTextChunker implements Chunker
```

它背后不是语义切分器，而是一个基于字符递归切分器的包装层。

## 2. `Chunker` 契约其实很薄

接口签名是：

```java
List<RagChunk> chunk(RagDocument document, String content)
```

这说明 AI4J 在 chunking 层的设计取向很明确：

- 给你一个文档对象
- 给你一段原始文本
- 你返回一组 `RagChunk`

它不强制：

- PDF 解析策略
- Markdown 结构保留策略
- 标题识别策略
- 表格专用切分策略

也就是说，当前 chunking 层是 **开放契约**，不是“内置足够多文档智能”的产品层。

## 3. 默认 `RecursiveTextChunker` 真实做了什么

默认实现非常直接：

1. 如果 `content` 为空，返回空列表
2. 调 `splitter.splitText(content)` 拿到若干文本片段
3. 逐段生成 `RagChunk`
4. 只填充：
   - `documentId`
   - `content`
   - `chunkIndex`

这里最关键的事实是：

**默认 chunker 并不会自动填充 `chunkId`、`pageNumber`、`sectionTitle`、`metadata`。**

而 `RagChunk` 明明是支持这些字段的：

- `chunkId`
- `documentId`
- `content`
- `chunkIndex`
- `pageNumber`
- `sectionTitle`
- `metadata`

这说明默认实现的定位是“给你一个能跑通的文本切分基础件”，不是“完整语义结构切分器”。

## 4. 默认策略为什么够用，但远远不够完整

默认 `RecursiveTextChunker` 的优点是：

- 简单
- 稳
- 没有额外模型依赖
- 跟现有 `RecursiveCharacterTextSplitter` 复用程度高

但它的边界也非常清楚：

- 它按文本片段切，不按业务结构切
- 它不知道标题层级
- 它不知道段落是否属于同一章节
- 它不知道表格、代码块、FAQ、配置段落该怎样保边界
- 它不会自动生产可复用的 chunk 主键

所以如果你的语料是：

- API 文档
- 法规
- 合同
- 论文
- 多页 PDF

默认切分通常只适合作为起点，不适合作为最终方案。

## 5. 为什么 `chunkIndex` 比很多人想的更重要

在 AI4J 当前实现里，`chunkIndex` 不只是一个展示字段。

后面的几个关键环节都可能用到它：

- `HybridRetriever` 去重 key 可能回退到 `documentId + chunkIndex`
- `RagHitSupport.stableKey(...)` 也会用 `chunkIndex`
- citation / trace 排障时，经常靠它定位“是文档的第几块”

这意味着：

**一旦 chunk 顺序不稳定，后续检索去重、引用定位、评估对比都会变脆。**

所以自定义 chunker 时，一个很稳的原则是：

- 文档内容不变时，尽量保证 `chunkIndex` 稳定
- 文档重跑 ingest 时，尽量不要让 chunk 边界频繁漂移

## 6. 默认实现为什么不生成 `chunkId`

从当前源码看，默认 chunker 没有生成 `chunkId`。这其实是在把一个重要决策留给业务：

- 你要不要用 `documentId + chunkIndex`
- 你要不要用 hash
- 你要不要把 page、section、dataset 编进去

这不是疏漏，而是边界选择。

因为一旦框架替你强行定义 `chunkId`，后面：

- 向量库主键
- 覆盖写入
- 增量更新
- 去重策略

都会被提前绑死。

所以 AI4J 当前更像是在说：  
**“我给你 `RagChunk` 结构，但 chunk 主键策略由你自己决定。”**

## 7. chunking 为什么直接影响引用质量

`DefaultRagContextAssembler` 生成 citation 时，会把最终命中的 `RagHit.content` 直接当作 snippet。

这意味着 citation 质量首先取决于：

- chunk 是否足够完整
- 是否恰好保留了回答所需上下文
- 是否没有把两个不相干的段落切在一起

如果 chunk 太碎：

- snippet 会丢语义
- 回答可能缺因果前文

如果 chunk 太大：

- snippet 会太长
- 无关上下文会稀释召回质量
- 模型读到的噪音会增加

所以 citation 质量并不是 citation 子系统单独决定的，它前面很大一部分是 chunking 决定的。

## 8. 当前默认 chunking 最容易踩的坑

### 8.1 只切文本，不补 metadata

`DenseRetriever` 后面会尽量从 metadata 里恢复：

- `documentId`
- `sourceName`
- `sourcePath`
- `sourceUri`
- `pageNumber`
- `sectionTitle`
- `chunkIndex`

如果 ingest 时没有把这些信息补进去，后面的检索命中和引用都会明显变“瞎”。

### 8.2 chunk 大小只按 token 成本调

很多人只盯着上下文成本调 `chunkSize`，不看：

- query 命中粒度
- 章节完整性
- 表格/列表保真
- rerank 输入质量

这会让 chunk 看起来省 token，但实际召回质量下降。

### 8.3 每次重建语料都让 chunk 边界飘

边界一漂，历史评估和线上问题对比就难做，尤其当你已经有 trace、引用、离线评测时。

## 9. 什么时候应该自己实现 `Chunker`

下面这些场景，建议直接自定义：

- 你需要按标题层级切
- 你需要保留页码
- 你需要把 section 信息灌进 metadata
- 你需要稳定 `chunkId`
- 你处理的是结构化文档而不是纯文本

一个靠谱的自定义 chunker，至少应该考虑：

- chunk 边界是否稳定
- `documentId` 是否可追踪
- `pageNumber` / `sectionTitle` 是否能回填
- metadata 是否足够支撑后续 citation / rerank / trace

## 10. 从 AI4J 当前实现看，最稳的设计建议

如果你现在要在这个框架上做生产化 RAG，chunking 层最稳的建议是：

1. 先用默认 `RecursiveTextChunker` 跑通链路
2. 明确你的主键和 metadata 规范
3. 再根据文档类型切到自定义 `Chunker`

不要一上来就把所有复杂性都塞进 retriever 或 reranker。  
因为很多“检索不准”的根因，其实早在 chunking 这里就已经定下来了。

## 11. 这页最该记住的结论

AI4J 当前默认 chunking 的本质是：

- 用 `RecursiveCharacterTextSplitter` 做文本递归切分
- 产出最基础的 `RagChunk`
- 只默认填 `documentId`、`content`、`chunkIndex`

它解决的是“把文本切成可入库块”，没有替你解决：

- 结构语义保留
- 稳定主键
- 页码与章节元数据
- 生产级 citation 友好性

所以 chunking 在 AI4J 里不是小细节，而是整个 RAG 质量链的前置开关。

## 12. 继续阅读

- [Ingestion Pipeline](/docs/core-sdk/search-and-rag/ingestion-pipeline)
- [Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)
- [Vector Store and Backends](/docs/core-sdk/search-and-rag/vector-store-and-backends)
