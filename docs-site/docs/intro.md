---
sidebar_position: 1
---

# AI4J 文档中心

欢迎来到 **AI4J** 官方文档站。

这套文档面向工程落地，优先解决“怎么接入、怎么跑起来、怎么扩展”这三类问题：

- 聚焦 Java/JDK8 生产接入；
- 覆盖模型能力、Coding Agent、MCP、Agent 到部署；
- 示例以“可直接落地”为目标。

## 1. AI4J 定位

AI4J 是一个工程化 Java 大模型 SDK，核心价值：

1. **平台协议消歧**：统一封装多家模型平台协议。
2. **基础能力完整**：Chat、Responses、Embedding、Rerank、Audio、Image、Realtime。
3. **工具生态完善**：Function Tool、MCP Client/Server/Gateway。
4. **编码与智能体能力可扩展**：Coding Agent、ReAct、CodeAct、SubAgent、Agent Teams、StateGraph、Trace。

## 2. 文档导航

- **快速开始**：安装、最小示例、Spring Boot、Ollama。
- **Coding Agent**：发布安装、CLI、TUI、ACP、配置、会话、命令、Tools、Skills、MCP、TUI 定制。
- **AI基础能力接入**：统一入口、请求返回约定、Provider 扩展、Chat、Responses、Audio 等基础能力与增强能力。
- **MCP**：路径选择、配置参考、客户端接入、第三方 MCP、网关、服务暴露。
- **Agent 智能体**：路径选择、运行时、记忆、编排、SubAgent、Agent Teams、追踪，偏框架级开发。
- **Agentic 工作流平台**：基于字节开源 Flowgram 框架，AI4J 提供 runtime、节点执行、后端 API 与平台化接入能力。
- **FAQ**：收敛高频问题，快速定位应该看哪条主线。
- **场景实践**：RAG、联网检索等端到端案例。
- **部署**：Cloudflare Pages 等部署与维护。
- **术语表**：统一关键概念，便于跨专题阅读。

如果你是为了直接使用本地 coding agent，建议优先看：

- `Coding Agent / 总览`
- `Coding Agent / 快速开始`
- `Coding Agent / 发布、安装与 GitHub Release`
- `Coding Agent / CLI / TUI 使用指南`
- `Coding Agent / ACP 集成`
- `Coding Agent / 配置体系`

## 3. 推荐阅读顺序

### 3.1 首次接入

1. `快速开始 / 安装与环境准备`
2. `快速开始 / 平台与服务能力矩阵`
3. `快速开始 / JDK8 + OpenAI 最小示例`
4. `快速开始 / Spring Boot 最小示例`
5. `AI基础能力接入 / 统一服务入口与调用方式`
6. `AI基础能力接入 / 统一请求与返回读取约定`
7. `AI基础能力接入 / Chat / 非流式 + 流式`

### 3.2 直接使用 Coding Agent

1. `Coding Agent / 总览`
2. `Coding Agent / 快速开始`
3. `Coding Agent / 发布、安装与 GitHub Release`
4. `Coding Agent / CLI / TUI 使用指南`
5. `Coding Agent / 会话、流式与进程`
6. `Coding Agent / Prompt 组装与上下文来源`
7. `Coding Agent / 配置体系`
8. `Coding Agent / 命令参考`

### 3.3 扩展与集成

1. `Coding Agent / Tools 与审批机制`
2. `Coding Agent / Skills 使用与组织`
3. `Coding Agent / MCP 对接`
4. `Coding Agent / ACP 集成`
5. `MCP / 使用路径与场景选择`
6. `MCP / 配置与网关参考`
7. `Agent / 使用路径与场景选择`
8. `Agent / 架构总览`
9. `Agentic 工作流平台 / 总览`
10. `Agentic 工作流平台 / 快速开始`
11. `FAQ`

## 4. 仓库结构

- `ai4j/`：统一 AI SDK 基座，包含 provider 接入、Chat / Responses / Embedding / Audio / Image、多模态、Tool 与 MCP 基础能力
- `ai4j-agent/`：通用 Agent runtime、workflow、memory、trace、team 等智能体能力
- `ai4j-coding/`：Coding Agent 运行时，包含 workspace、runtime、session、skills、commands 等
- `ai4j-cli/`：终端产品层，包含 CLI、TUI、ACP 与默认交互壳
- `ai4j-spring-boot-starter/`：Spring Boot 自动装配
- `ai4j-flowgram-spring-boot-starter/`：Flowgram Agentic 工作流平台后端 starter
- `ai4j-bom/`：Maven 版本对齐
- `ai4j-flowgram-demo/`：Flowgram 示例工程
- `docs-site/`：文档站源码

## 5. 历史博客迁移

你之前的 CSDN 实践内容已迁移并结构化，见：

- `场景实践 / 历史博客迁移映射`

## 6. 文档约定

- 优先给可运行最小示例
- 参数含义与默认行为写清楚
- 高风险能力明确安全边界
- 对“框架保证 vs 模型依赖”明确标注

如果你想优先走 Coding Agent 路线，建议从 `Coding Agent / 快速开始` 开始。
