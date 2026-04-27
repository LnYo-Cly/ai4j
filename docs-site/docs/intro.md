---
sidebar_position: 1
---

# AI4J 文档中心

AI4J 是一套面向 Java 生态的工程化 AI 基座。

它不只解决“发一个大模型请求”，而是把整条能力链连成体系：

- 统一模型调用：`Chat`、`Responses`、多模态、流式
- 统一能力接入：`Function Call`、`Skill`、`MCP`
- 统一上层扩展：`Spring Boot`、`Agent`、`Coding Agent`、`Flowgram`

如果要用一句话概括 AI4J，最准确的表达是：

> 一个把模型调用、工具接入、协议扩展和上层运行时连成体系的 Java AI SDK。

## 1. 你应该先走哪条线

### 1.1 第一次接入 AI4J

按这个顺序读：

1. [Why AI4J](/docs/start-here/why-ai4j)
2. [Architecture at a Glance](/docs/start-here/architecture-at-a-glance)
3. [Quickstart for Java](/docs/start-here/quickstart-java) 或 [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
4. [First Chat](/docs/start-here/first-chat)
5. [First Tool Call](/docs/start-here/first-tool-call)

### 1.2 你是为了直接使用本地 Coding Agent

直接看：

1. [Coding Agent / 总览](/docs/coding-agent/overview)
2. [Coding Agent / 快速开始](/docs/coding-agent/quickstart)
3. [Coding Agent / Install and Release](/docs/coding-agent/install-and-release)
4. [Coding Agent / CLI / TUI](/docs/coding-agent/cli-and-tui)

### 1.3 你是为了做框架扩展或平台集成

先看：

1. [Core SDK / 总览](/docs/core-sdk/overview)
2. [Core SDK / Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
3. [Core SDK / Tools](/docs/core-sdk/tools/overview)
4. [Core SDK / Skills](/docs/core-sdk/skills/overview)
5. [Core SDK / MCP](/docs/core-sdk/mcp/overview)

### 1.4 你是为了面试复习或架构表达

建议按这个顺序：

1. [Why AI4J](/docs/start-here/why-ai4j)
2. [Architecture at a Glance](/docs/start-here/architecture-at-a-glance)
3. [Choose Your Path](/docs/start-here/choose-your-path)
4. [Core SDK / Overview](/docs/core-sdk/overview)
5. 再按重点阅读 `Spring Boot / Agent / Coding Agent / Flowgram`

## 2. 文档结构

### 2.1 Start Here

只负责三件事：

- 解释 AI4J 是什么
- 帮你选路径
- 带你走通第一条成功路径

### 2.2 Core SDK

这是整站最重要的基座主线，统一解释：

- 模型调用
- 本地工具与函数调用
- Skills
- MCP
- Memory
- Search / RAG
- Provider 与网络栈扩展

### 2.3 上层模块

- `Spring Boot`：容器化接入与自动装配
- `Agent`：通用智能体运行时
- `Coding Agent`：面向本地代码仓的产品化入口
- `Flowgram`：可视化工作流平台后端与节点运行体系

## 3. 仓库模块地图

- `ai4j/`：统一 AI SDK 基座，包含 provider 接入、Chat / Responses / Embedding / Audio / Image、多模态、Tool 与 MCP 基础能力
- `ai4j-agent/`：通用 Agent runtime、workflow、memory、trace、team 等智能体能力
- `ai4j-coding/`：Coding Agent 运行时，包含 workspace、runtime、session、skills、commands 等
- `ai4j-cli/`：终端产品层，包含 CLI、TUI、ACP 与默认交互壳
- `ai4j-spring-boot-starter/`：Spring Boot 自动装配
- `ai4j-flowgram-spring-boot-starter/`：Flowgram Agentic 工作流平台后端 starter
- `ai4j-bom/`：Maven 版本对齐
- `ai4j-flowgram-demo/`：Flowgram 示例工程
- `docs-site/`：文档站源码

## 4. 阅读约定

- 优先给可运行最小示例
- 参数含义与默认行为写清楚
- 高风险能力明确安全边界
- 对“框架保证 vs 模型依赖”明确标注
- canonical page 优先负责入口解释，深页再负责细节展开

如果你只想先建立整体心智模型，下一页建议直接看 [Why AI4J](/docs/start-here/why-ai4j)。
