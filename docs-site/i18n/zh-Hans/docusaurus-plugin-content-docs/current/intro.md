---
sidebar_position: 1
---

# AI4J 文档中心

欢迎来到 **AI4J** 官方文档站。

这套文档是面向工程落地的文档库：

- 聚焦 Java/JDK8 生产接入；
- 覆盖模型能力、MCP、Agent 到部署；
- 示例以“可直接落地”为目标。

## 1. AI4J 定位

AI4J 是一个工程化 Java 大模型 SDK，核心价值：

1. **平台协议消歧**：统一封装多家模型平台协议。
2. **基础能力完整**：Chat、Responses、Embedding、Audio、Image、Realtime。
3. **工具生态完善**：Function Tool、MCP Client/Server/Gateway。
4. **智能体能力可扩展**：ReAct、CodeAct、SubAgent、Agent Teams、StateGraph、Trace。

## 2. 文档导航

- **快速开始**：安装、最小示例、Spring Boot、Ollama。
- **AI基础能力接入**：平台适配、Chat、Responses、Audio 等基础能力与增强能力。
- **MCP**：协议、客户端接入、第三方 MCP、网关、服务暴露。
- **Agent 智能体**：运行时、记忆、编排、SubAgent、Agent Teams、追踪。
- **场景实践**：RAG、联网检索等端到端案例。
- **部署**：Cloudflare Pages 等部署与维护。

如果你是为了直接使用本地 coding agent，建议优先看：

- `快速开始 / Coding Agent CLI 快速开始`
- `Agent / Coding Agent CLI 与 TUI`
- `Agent / 多 Provider Profile 实战`

## 3. 推荐阅读顺序

### 3.1 首次接入

1. `快速开始 / 安装与环境准备`
2. `快速开始 / JDK8 + OpenAI 最小示例`
3. `快速开始 / Coding Agent CLI 快速开始`
4. `AI基础能力接入 / 平台适配与统一接口`
5. `AI基础能力接入 / Chat / 非流式 + 流式`

### 3.2 进阶能力

1. `AI基础能力接入 / Responses`
2. `Agent / Coding Agent CLI 与 TUI`
3. `Agent / 多 Provider Profile 实战`
4. `AI基础能力接入 / 增强能力（联网、向量、SPI）`
5. `MCP / 总览与集成`
6. `Agent / 架构与编排`

## 4. 仓库结构

- `ai4j/`：核心 SDK
- `ai4j-spring-boot-starter/`：Spring Boot 自动装配
- `docs-site/`：文档站源码

## 5. 历史博客迁移

你之前的 CSDN 实践内容已迁移并结构化，见：

- `场景实践 / 历史博客迁移映射`

## 6. 文档约定

- 优先给可运行最小示例
- 参数含义与默认行为写清楚
- 高风险能力明确安全边界
- 对“框架保证 vs 模型依赖”明确标注

如果你希望优先补充某个专题，可以直接提 Issue。
