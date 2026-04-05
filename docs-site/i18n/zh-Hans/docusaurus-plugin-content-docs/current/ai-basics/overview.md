---
sidebar_position: 1
---

# AI基础能力接入总览

本章是 ai4j 的“基础接入层”，与 `MCP`、`Agent` 同级。

目标很明确：

- 先把模型能力稳定接入（平台、协议、同步/流式）；
- 再逐步增加联网、检索、网络栈调优这些增强能力；
- 最后再上 MCP 和 Agent 编排。

## 1. 为什么单独成章

和 `MCP`、`Agent` 一样，AI 基础能力本身就是独立系统层：

- 上接业务 API
- 下接模型平台
- 中间负责协议统一、参数兼容、错误与流式处理

如果这层不清晰，后续 MCP/Agent 也很难稳定。

## 2. 本章结构

### 2.1 平台与协议层

- 平台适配与统一接口
- Chat vs Responses 选型

### 2.2 Chat 接入

- 非流式
- 流式
- Function Call
- 多模态

### 2.3 Responses 接入

- 非流式
- 流式事件模型

### 2.4 其它基础服务

- Embedding
- Audio
- Image
- Realtime

### 2.5 增强能力（仍属基础接入层）

- 联网增强（SearXNG）
- 向量检索工作流（Pinecone）
- 网络扩展（SPI: Dispatcher / ConnectionPool）

## 3. “联网增强/向量检索/网络扩展”放这里合适吗？

合适，原因如下：

1. **联网增强**：本质是对 Chat 请求的输入增强，不属于 MCP 或 Agent 专属。
2. **向量检索**：本质是 Embedding + 向量库接入，属于模型输入构造层。
3. **网络扩展**：本质是 SDK 网络栈能力（OkHttp 并发/连接池），属于基础设施层。

它们确实是“基础接入增强能力”，不是“高级编排能力”。

## 4. 推荐阅读顺序

1. 平台适配与统一接口
2. Chat（非流式 -> 流式 -> Tool/多模态）
3. Responses（非流式 -> 事件流）
4. Embedding/Audio/Image/Realtime
5. 增强能力（SearXNG、Pinecone、SPI）

## 5. 对应核心代码

- 服务工厂：`ai4j/src/main/java/io/github/lnyocly/ai4j/service/factor/AiService.java`
- 平台枚举：`ai4j/src/main/java/io/github/lnyocly/ai4j/service/PlatformType.java`
- 统一配置：`ai4j/src/main/java/io/github/lnyocly/ai4j/service/Configuration.java`

后续每页都围绕这些入口展开，保持“能直接落地”的粒度。
