---
sidebar_position: 4
---

# 历史博客迁移映射

本页用于把历史 CSDN 文章映射到结构化文档，方便持续维护与版本同步。

## 1. 映射总表

1. **Spring Boot + OpenAI + JDK8 快速接入**
   - 原文：[142177544](https://blog.csdn.net/qq_35650513/article/details/142177544)
   - 新文档：`快速开始 / JDK8 + OpenAI 最小示例`

2. **DeepSeek / Qwen / Llama 本地模型接入**
   - 原文：[142408092](https://blog.csdn.net/qq_35650513/article/details/142408092)
   - 新文档：`快速开始 / Ollama 本地模型接入`

3. **DeepSeek 流式 + 联网 + RAG + 多轮**
   - 原文：[146084038](https://blog.csdn.net/qq_35650513/article/details/146084038)
   - 新文档：`场景指南 / DeepSeek：流式 + 联网搜索 + RAG + 多轮会话`

4. **SearXNG 联网增强**
   - 原文：[144572824](https://blog.csdn.net/qq_35650513/article/details/144572824)
   - 新文档：`场景指南 / SearXNG 联网搜索增强`

5. **法律助手 RAG（Pinecone）**
   - 原文：[142568177](https://blog.csdn.net/qq_35650513/article/details/142568177)
   - 新文档：`场景指南 / 基于 Pinecone 的法律助手 RAG`

6. **MCP + MySQL 动态管理**
   - 原文：[150532784](https://blog.csdn.net/qq_35650513/article/details/150532784)
   - 新文档：`MCP / MySQL 动态 MCP 服务管理`

## 2. 为什么需要迁移为文档库

- 文章天然是时间线结构，不适合长期按主题检索。
- 文档库可以按模块维护，和代码变更同步。
- 社区贡献者可直接通过 PR 补充和修订。

## 3. 迁移策略建议

1. 先保留原文链接，确保历史可追溯。
2. 将“概念”与“落地代码”拆分成独立章节。
3. 每次版本升级时同步更新对应文档页。
4. 对高频问题沉淀到 `快速开始 / 常见问题与排障手册`。

## 4. 后续计划（建议）

- 增加“版本差异说明”（如 1.3 -> 1.4）
- 增加“案例仓库索引”
- 增加“常见错误日志 -> 排障步骤”的快速检索页
