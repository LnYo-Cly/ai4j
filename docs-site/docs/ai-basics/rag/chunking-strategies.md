---
sidebar_position: 3
---

# Chunking 策略详解

RAG 的召回质量，很多时候不是输在模型，而是输在切块。

同一份文档，如果 chunk 切得不合理，后面即使你加了：

- `DenseRetriever`
- `Bm25Retriever`
- `HybridRetriever`
- `ModelReranker`

也只能在一堆不理想的候选里做排序优化，效果上限会很低。

这页专门回答三个问题：

1. 当前 AI4J 内置了什么 chunking 能力
2. 不同文档类型应该怎么切
3. 什么场景下不该再继续用“纯字符分块”

## 1. 当前 AI4J 内置能力

当前代码里现成可用的 chunking 入口是：

- `RecursiveCharacterTextSplitter`

它本质上是：

- 按分隔符优先级递归切分
- 常见默认分隔符是 `\n\n -> \n -> 空格 -> 字符`
- 通过 `chunkSize` 和 `chunkOverlap` 控制块大小与重叠

最小示例：

```java
RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(1000, 200);
List<String> chunks = splitter.splitText(content);
```

这意味着当前 AI4J 的内置主线是：

- 先把文档解析成文本
- 再做字符级递归分块

要注意：

- 当前并没有内置“标题感知分块”
- 当前并没有内置“语义分块”
- 当前并没有内置“表格专用分块”
- 当前已经有最小统一抽象：`DocumentLoader / Chunker / MetadataEnricher / IngestionPipeline`

所以这页既讲“当前已有 API”，也讲“更进一步的推荐策略”。

## 2. 先理解一个基本原则

chunk 的目标不是“每块都一样大”，而是：

- 每块尽量语义完整
- 每块不要大到混入太多无关信息
- 每块要保留足够来源信息，方便后续引用和过滤

更实际地说，一个好 chunk 应该同时满足：

- 可以单独被检索命中
- 被命中后本身就足够解释问题
- 被塞进上下文后不会引入太多噪声

## 3. 什么时候字符分块是够用的

字符分块是可以继续作为默认起点的，尤其适合：

- 普通文本手册
- FAQ 文本
- 规章制度
- 产品说明文档
- 已经过清洗的 Markdown / TXT

原因很简单：

- 实现简单
- 成本低
- 对大多数基础 RAG 足够可用

如果你还在第一阶段，优先把：

- `chunkSize`
- `chunkOverlap`
- metadata
- `topK`
- 检索策略

调顺，比一上来做复杂 chunking 更重要。

## 4. 不同文档类型怎么切

### 4.1 技术文档 / 产品手册

推荐起点：

- `chunkSize = 800~1200`
- `chunkOverlap = 10%~20%`

适合原因：

- 一段说明通常需要保留前后句
- 过小会把定义、约束、示例拆散

重点注意：

- 尽量不要跨多个二级标题拼到同一块
- 如果原文标题层级清晰，建议预处理时先按标题分段，再交给 `RecursiveCharacterTextSplitter`

### 4.2 法律 / 制度 / 合同

推荐起点：

- `chunkSize = 600~1000`
- `chunkOverlap = 15%~25%`

适合原因：

- 法规条款往往篇幅不长，但上下条之间有关联
- 需要尽量保留编号、条目名、上下句逻辑

重点注意：

- 条、款、项编号不要被切断
- 标题、章节号、版本号要进入 metadata
- 合同正文和附件尽量分开建 dataset 或至少分 source

### 4.3 FAQ / 问答库

推荐起点：

- 一个问答对就是一个 chunk
- 必要时再按答案长度拆成 2~3 段

适合原因：

- FAQ 的最小检索单位本来就是问答对
- 再按纯字符切，往往会把问题和答案拆开

重点注意：

- 问题文本最好也进入 `content`
- 或者至少放进 metadata，便于 BM25 命中

### 4.4 Markdown / 知识库页面

推荐策略：

- 先按一级/二级标题预切
- 再对过长段落做字符分块

原因：

- Markdown 的结构本来就有天然层级
- 直接全量字符切分，会把标题和正文关联冲淡

重点注意：

- 标题名最好写入 `sectionTitle`
- 代码块、表格块尽量不要和普通说明段落混切

### 4.5 代码 / API 文档

推荐起点：

- 代码注释型文档：`500~900`
- API 文档：按“一个接口说明”为一个 chunk

原因：

- 代码、参数表、返回值示例天然是一组
- 纯字符切很容易把方法签名和说明拆开

重点注意：

- 函数名、类名、接口路径应该单独保留到 metadata
- 代码块适合偏小 chunk，不要把多个接口拼进一块
- 这类内容通常要配合 `Bm25Retriever`

### 4.6 PDF / OCR 文档

推荐策略：

- 先做清洗，再分块
- 优先保留页码和章节信息

原因：

- PDF 抽出来的文本常有断行、页眉页脚、表格噪声
- 如果清洗前就分块，会把噪声一起固化进索引

重点注意：

- 去掉重复页眉页脚
- 修正常见 OCR 断词
- 页码写进 `pageNumber`
- 文件名、路径、章节标题写进 metadata

### 4.7 表格 / 列表 / 配置清单

推荐策略：

- 不要直接把整张大表当一个 chunk
- 按行组、主题段、表头 + 若干行一起切

原因：

- 大表整块入库通常语义混乱
- 模型真正回答时，往往只需要其中一部分记录

重点注意：

- 表头要和数据一起保留
- 如果是配置清单，字段名必须保留
- 纯字符分块在这里通常是最不稳的

## 5. overlap 应该怎么选

`chunkOverlap` 的作用不是“多复制一点更保险”，而是避免关键句正好被切断。

推荐起点：

- 普通文档：`10%~20%`
- 制度/合同：`15%~25%`
- FAQ：通常可以很低，甚至为 `0`
- 代码/API：按场景试，一般不宜过大

overlap 太小的问题：

- 关键定义或条件被截断
- 相邻块之间失去上下文连续性

overlap 太大的问题：

- 索引冗余明显增加
- 检索结果容易重复
- rerank 前候选质量下降

## 6. 什么时候不要再用纯字符分块

下面这些情况，纯字符分块通常已经不是最优解：

- 标题层级非常清晰的 Markdown / Wiki
- 强结构化 FAQ
- 以表格为主的知识库
- API 文档、SDK 文档、代码说明
- OCR 噪声很重的 PDF

这时候更合理的方式通常是：

1. 先做结构预切
2. 再对过长部分做字符分块

也就是：

```text
结构感知切分
  -> 递归字符切分
```

而不是直接从整篇文本暴力切。

## 7. metadata 要和 chunk 一起设计

chunking 不是只决定 `content`，还会直接影响这些字段：

- `documentId`
- `sourceName`
- `sourcePath`
- `sourceUri`
- `pageNumber`
- `sectionTitle`
- `chunkIndex`
- `tenant`
- `biz`
- `version`

如果你切块时不保留这些信息，后面即使能检索到，也会出现：

- 无法展示来源
- 无法做租户过滤
- 无法按页码跳转
- 无法稳定去重

所以正确顺序应该是：

```text
文档解析
  -> 结构信息提取
  -> chunk
  -> metadata 绑定
  -> embedding
  -> upsert
```

## 8. 怎么判断 chunking 是否有问题

如果你的系统出现下面现象，优先怀疑 chunking：

- 检索结果“沾边但不回答问题”
- 回答里总夹杂相邻章节的噪声
- 同一答案需要的信息总是落在两个 chunk 中间
- BM25 能命中关键词，但上下文一看就是残缺的
- rerank 加上后也只是“在错误候选里选最不差的”

简单排查方法：

1. 随机抽问题
2. 看 top5 命中的原始 chunk
3. 判断 chunk 本身是否语义完整

如果 chunk 本身就不完整，后面调检索器和 rerank 收益会很有限。

## 9. 一套实用的默认起点

如果你现在还没有专门做结构化 ingestion，建议先用这套默认值起步：

### 通用默认值

- splitter：`RecursiveCharacterTextSplitter`
- `chunkSize = 900~1100`
- `chunkOverlap = 120~200`

### 制度 / 合同

- `chunkSize = 700~900`
- `chunkOverlap = 120~180`

### Markdown / 手册

- 先按标题分段
- 段内超过 `1200` 再递归切

### FAQ

- 一个问答对一个 chunk

然后再配合：

- `DenseRetriever`
- `Bm25Retriever`
- `HybridRetriever`
- 可选 `ModelReranker`

逐层调优。

## 10. AI4J 当前推荐的现实做法

基于当前仓库已有能力，最现实的主线是：

1. 用 `IngestionPipeline` 统一走入库链路
2. 默认入口可直接复用 `TikaDocumentLoader`
3. 默认分块可直接复用 `RecursiveTextChunker`
4. 默认 metadata 可直接复用 `DefaultMetadataEnricher`
5. 写入目标仍然面向 `VectorStore`
6. 如有需要，再在检索阶段用 `Bm25Retriever + HybridRetriever + ModelReranker`

最小代码入口可以直接看：

- [Ingestion Pipeline 文档入库流水线](/docs/ai-basics/rag/ingestion-pipeline)

也就是说：

- 文档层先把切块做稳
- 检索层再去做融合和精排

不要把本该在 chunking 层解决的问题，全压给 rerank。

## 11. 后面还会缺什么

现在最小统一抽象已经有了：

- `Chunker`
- `DocumentLoader`
- `MetadataEnricher`
- `IngestionPipeline`

后面继续往前走，最值得补的是这些更强的能力：

- 标题感知分块
- 语义分块
- 表格分块
- 父子块
- 多格式 ingestion

也就是说，当前阶段已经把“基础编排骨架”补齐了，下一阶段再去补更强的结构化切分与清洗策略。

## 12. 继续阅读

1. [RAG 架构、分块与索引设计](/docs/ai-basics/rag/architecture-and-indexing)
2. [Ingestion Pipeline 文档入库流水线](/docs/ai-basics/rag/ingestion-pipeline)
3. [混合检索与 Rerank 实战工作流](/docs/ai-basics/rag/hybrid-retrieval-and-rerank-workflow)
4. [引用、Trace 与前端展示](/docs/ai-basics/rag/citations-trace-and-ui-integration)
5. [Pinecone RAG 工作流](/docs/ai-basics/rag/pinecone-workflow)
