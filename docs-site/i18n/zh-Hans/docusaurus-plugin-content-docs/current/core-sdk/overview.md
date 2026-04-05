---
sidebar_position: 1
---

# 核心 SDK 总览

这一章专门解决你提到的问题：文档不能散，必须细分且可落地。

本章把 ai4j 的基础能力拆成可独立阅读的子模块，每个模块都包含：

- 能力边界（能做什么/不能做什么）
- 核心类与关键参数
- 同步与流式调用差异
- 工程化建议与常见坑

## 1. 设计目标

ai4j 的核心目标是“**跨平台协议消歧**”：

- 业务代码只依赖统一接口
- 平台差异收敛在服务实现层
- 模型切换成本最小化

统一入口由 `AiService` 提供，统一接口包括：

- `IChatService`
- `IResponsesService`
- `IEmbeddingService`
- `IAudioService`
- `IImageService`
- `IRealtimeService`

## 2. 阅读顺序（推荐）

第一次接入建议按下面顺序阅读：

1. `平台与服务矩阵`
2. `Chat / 非流式`
3. `Chat / 流式`
4. `Responses / 流式事件模型`
5. `Function Call 与工具注册`
6. `多模态`
7. `SPI、SearXNG、Pinecone`

## 3. 章节目录

### 3.1 平台与协议层

- `平台与服务矩阵`
- `Chat vs Responses 选型`

### 3.2 Chat Completions

- `非流式调用`
- `流式调用`
- `Function Call 与 Tool 注册`
- `多模态（Vision）`

### 3.3 Responses API

- `非流式调用`
- `流式事件模型`

### 3.4 其他服务

- `Embedding`
- `Audio`
- `Image`
- `Realtime`

### 3.5 工程增强能力

- `SearXNG 联网搜索增强`
- `Pinecone 向量检索工作流`
- `SPI：Dispatcher 与 ConnectionPool`

## 4. 你可以得到什么

读完这一章后，你应该可以：

- 用同一套实体在 OpenAI / 豆包 / DashScope / Ollama 间切换
- 解释 Chat 与 Responses 的事件模型差异
- 正确使用 tool/function/mcp 的暴露语义
- 在项目里按“最小侵入”接入联网检索与向量检索
- 按并发模型自定义 OkHttp Dispatcher 与连接池

## 5. 对应代码入口

- 服务工厂：`ai4j/src/main/java/io/github/lnyocly/ai4j/service/factor/AiService.java`
- 平台枚举：`ai4j/src/main/java/io/github/lnyocly/ai4j/service/PlatformType.java`
- 统一配置：`ai4j/src/main/java/io/github/lnyocly/ai4j/service/Configuration.java`

本章后续页面会围绕这些入口逐层展开。
