---
sidebar_position: 5
---

# Documentation Map

这页定义 AI4J 文档站的正式阅读路径。它的作用不是替代功能页，而是告诉读者：

- 哪些目录是当前 canonical 主线。
- 哪些目录属于历史沉淀或迁移来源。
- 同一个能力应该从哪条路径进入，避免在旧页和新页之间来回跳。

## Canonical 主线

| 主题 | 正式入口 | 适合谁 |
| --- | --- | --- |
| 项目定位、模块选择、第一步运行 | [Start Here](/docs/intro) | 第一次接触 AI4J 的用户 |
| Core SDK、模型、Tool、Skill、RAG、基础能力 | [Core SDK](/docs/core-sdk/overview) | 普通 Java 或 SDK 接入者 |
| MCP client、gateway、server、tool 暴露 | [MCP](/docs/mcp/overview) | 要接外部工具或发布 Java 能力的团队 |
| Spring Boot 自动配置和 Bean 扩展 | [Spring Boot](/docs/spring-boot/overview) | Spring 应用开发者 |
| 通用 Agent runtime、workflow、trace、team | [Agent](/docs/agent/overview) | 要在业务系统中嵌入 Agent 的团队 |
| 本地代码仓任务、CLI、TUI、ACP | [Coding Agent](/docs/coding-agent/overview) | 要做 coding agent 或本地开发宿主的团队 |
| FlowGram.ai 画布后端执行层 | [FlowGram](/docs/flowgram/overview) | 要做可视化工作流平台的团队 |
| 场景 cookbook | [Solutions](/docs/solutions/overview) | 已经有明确业务场景的读者 |
| 版本、发布、安全、迁移、生产检查 | [Operations](/docs/operations/production-checklist) | 选型、上线和维护人员 |

## Legacy 来源目录

这些目录暂时保留，因为里面有不少长文和迁移内容仍然有价值。它们不再作为新用户的第一入口。

| 目录 | 当前定位 | 处理原则 |
| --- | --- | --- |
| `getting-started/` | 旧入门页、历史 quickstart 和版本页 | 精华迁移到 `start-here/`、`reference/`、`spring-boot/` 后保留跳转 |
| `ai-basics/` | Core SDK 低层细节和旧能力树 | 迁移强内容到 `core-sdk/`、`mcp/`、`solutions/` 后降级为 legacy reference |
| `guides/` | 历史博客、方案和迁移指南 | 迁移到 `solutions/` 或 `migration/`，保留博客迁移索引 |
| `core-sdk/chat/`、`core-sdk/responses/` | 旧模型访问拆分页 | 内容逐步并入 `core-sdk/model-access/` |
| `core-sdk/mcp/` | Core SDK 视角下的 MCP 深层参考 | 顶层 `mcp/` 是正式主线；独有技术细节再迁入或作为 advanced reference |
| `agent/orchestration/`、`agent/runtimes/`、`agent/observability/` | 迁移期别名目录 | 优先阅读 `agent/` 下的平铺 canonical 页面 |

## 能力到页面的唯一入口

| 你要找的能力 | 从这里开始 | 不建议从这里开始 |
| --- | --- | --- |
| Chat / Responses / Streaming / Multimodal | [Model Access](/docs/core-sdk/model-access/overview) | `ai-basics/chat/*`、`core-sdk/chat/*` |
| Function Tool / Tool whitelist | [Tools](/docs/core-sdk/tools/overview) | `ai-basics/chat/tool-calling` |
| Skill | [Skills](/docs/core-sdk/skills/overview) | `ai-basics/skills` |
| MCP | [MCP Overview](/docs/mcp/overview) | `core-sdk/mcp/overview` |
| Memory | [Memory](/docs/core-sdk/memory/overview) | `ai-basics/chat/chat-memory*` |
| RAG / Vector / Ingestion | [Search & RAG](/docs/core-sdk/search-and-rag/overview) | `ai-basics/rag/*` |
| Provider 扩展 | [Extension](/docs/core-sdk/extension/overview) | `ai-basics/provider-and-model-extension` |
| Spring Boot | [Spring Boot Overview](/docs/spring-boot/overview) | `getting-started/quickstart-springboot` |
| Coding Agent MCP / ACP | [MCP and ACP](/docs/coding-agent/mcp-and-acp) | `agent/coding-agent-*` |
| FlowGram 节点和任务 API | [FlowGram Overview](/docs/flowgram/overview) | `flowgram/builtin-nodes` |

## 迁移期间的阅读规则

1. 新用户只从 sidebar 里的 canonical 主线进入。
2. 搜索到旧页时，先看页面顶部是否给出正式入口。
3. 旧页里的长代码和实现细节可以参考，但不要把旧页当成模块边界的来源。
4. 新增文档必须落在 canonical 主线；不能继续向 `getting-started/`、`ai-basics/` 或 `guides/` 增加新主线内容。

如果你还不知道应该读哪条线，回到 [Choose Your Path](/docs/start-here/choose-your-path)。
