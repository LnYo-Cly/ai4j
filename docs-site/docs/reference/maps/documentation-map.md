---
sidebar_position: 5
title: 文档地图
description: AI4J 文档站的正式阅读地图：八个顶层分区各回答什么问题、每个能力的唯一入口在哪，以及旧链接如何自动跳转。
tags: [reference]
---

# 文档地图
这页定义 AI4J 文档站的正式阅读地图。它的作用不是替代功能页，而是回答两个问题：

- 八个顶层分区各自回答读者的什么问题，我该进哪个。
- 同一个能力应该从哪条唯一路径进入，避免在主题相近的页面之间来回跳。

## 八个顶层分区

文档按**读者意图**组织，不按代码模块组织。顶层顺序即推荐阅读顺序：

| 分区 | 回答什么问题 | 入口 |
| --- | --- | --- |
| 入门 | AI4J 是什么、值不值得用、怎么跑通第一段代码 | [Intro](/docs/intro) |
| 核心能力 | 不引入 Agent 模块也能用的能力：模型接入、媒体生成、工具、技能、会话记忆、RAG、MCP | [核心能力总览](/docs/capabilities/overview) |
| Agent 开发 | 怎么构建自主 Agent：运行时、记忆与压缩、编排、治理、可观测与互操作 | [Agent 总览](/docs/agent/overview) |
| 扩展 | 怎么扩展 AI4J 自身：jar 插件系统与代码内扩展面 | [扩展总览](/docs/extending/overview) |
| 产品 | 建在 SDK 上的产品：Coding Agent（CLI）与 FlowGram | [Coding Agent](/docs/products/coding-agent/overview) · [FlowGram](/docs/products/flowgram/overview) |
| 集成 | 怎么把 AI4J 接进你的栈：Spring Boot 与实战方案 | [Spring Boot](/docs/integrations/spring-boot/overview) · [实战方案](/docs/integrations/solutions/overview) |
| 生产 | 上线前后的检查：安全、检查清单、排障 | [生产检查清单](/docs/production/production-checklist) |
| 参考 | 查规格与背景：API、版本与迁移、架构地图、关于 | [API](/docs/reference/api) |

核心能力与 Agent 开发的分界线是：**这个能力在不引入 `ai4j-agent` 模块时存在吗？** 存在则归核心能力（如会话记忆 chat memory），不存在则归 Agent 开发（如 Agent 记忆与压缩）。

## 能力到页面的唯一入口

| 你要找的能力 | 从这里开始 |
| --- | --- |
| Chat / Responses / Messages / Streaming / Multimodal | [模型接入](/docs/capabilities/models/overview) |
| 图像 / 音频 / 视频 / 音乐 / Realtime | [媒体生成](/docs/capabilities/media/image-generation) |
| Function Tool / 白名单 / 执行模型 | [工具](/docs/capabilities/tools/overview) |
| Skill（SKILL.md、发现、激活） | [技能](/docs/capabilities/skills/overview) |
| MCP（client / transport / gateway / server） | [MCP](/docs/capabilities/mcp/overview) |
| Chat Memory（会话历史） | [会话记忆](/docs/capabilities/chat-memory/overview) |
| RAG / 向量库 / 摄取 / 评测 | [RAG](/docs/capabilities/rag/overview) |
| Agent 运行时 / 子代理 / Teams | [Agent 开发](/docs/agent/overview) |
| Agent 记忆压缩 / 上下文管理 | [记忆与压缩](/docs/agent/memory/memory-and-state) |
| 审批 / 拦截器 / 沙箱 | [治理](/docs/agent/governance/approval-permission-policy) |
| Trace / 重放恢复 / A2A | [可观测与互操作](/docs/agent/observability/trace-observability) |
| 插件包（jar / SPI） | [插件系统](/docs/extending/plugins/plugin-packages) |
| Provider / 服务 / HTTP 栈扩展 | [代码内扩展面](/docs/extending/code-level/provider-extension) |
| Coding Agent CLI / TUI / ACP | [Coding Agent](/docs/products/coding-agent/overview) |
| FlowGram 节点 / 任务 API | [FlowGram](/docs/products/flowgram/overview) |
| Spring Boot 自动配置 | [Spring Boot](/docs/integrations/spring-boot/overview) |

## 关于旧链接

历史上的目录结构（`start-here/`、`core-sdk/`、`mcp/` 顶层等）已并入上述八区。**所有旧 URL 由站点 redirect 自动跳转到新位置**，不需要手动维护映射；搜索引擎收录的旧地址仍然有效。

新增文档必须落在八个分区的既有主题内；不要为单一页面新开顶层目录。

如果你还不知道应该读哪条线，回到 [入门路径选择](/docs/getting-started/choose-your-path)。
