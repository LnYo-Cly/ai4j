---
sidebar_position: 1
---

# Migration Guide

AI4J 文档站正在从旧的 `getting-started/`、`ai-basics/`、`guides/` 结构收敛到按模块和接入路径组织的 canonical 结构。本页说明迁移规则，避免用户把历史页当成新的 source of truth。

## 迁移原则

1. 不直接删除强内容。
2. 先把旧页中的稳定结论迁到 canonical 页面。
3. 再给旧页加 legacy notice 或跳转说明。
4. 新文档只写入 canonical 主线。
5. 迁移后以 sidebar 中的页面作为正式阅读路径。

## 旧路径到新路径

| 旧路径 | 新路径 | 状态 |
| --- | --- | --- |
| `getting-started/installation` | [Quickstart for Java](/docs/start-here/quickstart-java) | 迁移中 |
| `getting-started/quickstart-openai-jdk8` | [Quickstart for Java](/docs/start-here/quickstart-java) | 迁移中 |
| `getting-started/quickstart-springboot` | [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot) | 迁移中 |
| `getting-started/version-compatibility` | [Version Compatibility](/docs/reference/version-compatibility) | 已建立新入口 |
| `getting-started/modules-and-maven-central` | [Release and Artifacts](/docs/reference/release-and-artifacts) | 已建立新入口 |
| `ai-basics/chat/*` | [Model Access](/docs/core-sdk/model-access/overview) | 迁移中 |
| `ai-basics/responses/*` | [Model Access](/docs/core-sdk/model-access/overview) | 迁移中 |
| `ai-basics/rag/*` | [Search & RAG](/docs/core-sdk/search-and-rag/overview) | 迁移中 |
| `ai-basics/services/*` | [Platform and Service Matrix](/docs/core-sdk/platform-service-matrix) 和相关能力页 | 迁移中 |
| `ai-basics/provider-and-model-extension` | [Extension](/docs/core-sdk/extension/overview) | 迁移中 |
| `core-sdk/mcp/*` | [MCP Overview](/docs/mcp/overview) | 顶层 MCP 已为正式主线 |
| `guides/*` | [Solutions](/docs/solutions/overview) | 迁移中 |
| `agent/coding-agent-*` | [Coding Agent](/docs/coding-agent/overview) | 已拆分新主线 |
| `flowgram/builtin-nodes` | [Built-in Nodes](/docs/flowgram/built-in-nodes) | 命名收口中 |

## API 和使用方式迁移

### 从旧 FreeAiService 心智迁移

旧文档中有些示例会把 `FreeAiService` 当成最直接入口。新文档应优先讲清：

- Core SDK 的正式统一入口是 `AiService`。
- 多实例或多 provider 场景应理解 `AiServiceRegistry`。
- `FreeAiService` 更适合作为兼容壳或旧示例迁移线索，不应成为新用户第一主线。

正式入口：

- [Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
- [Spring Boot Overview](/docs/spring-boot/overview)

### 从旧 Chat / Responses 分散页迁移

旧页按接口形态拆得比较细，适合查实现细节。新用户应先从：

- [Model Access Overview](/docs/core-sdk/model-access/overview)
- [Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)

再进入具体 chat、responses、streaming、多模态页面。

### 从旧 MCP 路径迁移

顶层 `docs/mcp/` 是当前正式 MCP 主线。`core-sdk/mcp/*` 中的独有细节会逐步迁移到：

- [Client Integration](/docs/mcp/client-integration)
- [Gateway Management](/docs/mcp/gateway-management)
- [Build Your MCP Server](/docs/mcp/build-your-mcp-server)
- [Tool Exposure Semantics](/docs/mcp/tool-exposure-semantics)

### 从旧 guides 迁移

`guides/` 更像历史博客和教程沉淀。可复制方案应进入：

- [Solutions Overview](/docs/solutions/overview)

生产检查、排障、安全、版本和发布说明应进入：

- [Production Checklist](/docs/operations/production-checklist)
- [Troubleshooting](/docs/troubleshooting/overview)
- [Security Overview](/docs/security/overview)
- [Version Compatibility](/docs/reference/version-compatibility)
- [Release and Artifacts](/docs/reference/release-and-artifacts)

## 新文档写入规则

| 新内容类型 | 写入位置 |
| --- | --- |
| 第一次接入、路径选择 | `start-here/` |
| Core SDK 能力 | `core-sdk/` |
| MCP | `mcp/` |
| Spring Boot | `spring-boot/` |
| Agent runtime | `agent/` |
| Coding Agent | `coding-agent/` |
| FlowGram | `flowgram/` |
| 场景 cookbook | `solutions/` |
| 版本、发布、兼容性 | `reference/` |
| 安全和上线 | `security/`、`operations/` |
| 迁移和排障 | `migration/`、`troubleshooting/` |

不要继续向 `getting-started/`、`ai-basics/` 或 `guides/` 添加新的主线页面。
