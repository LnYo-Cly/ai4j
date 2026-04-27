---
sidebar_position: 998
---

# FAQ

本页收敛 AI4J 文档站里最常见的“我到底该看哪一页”和“这几个概念到底有什么区别”。

如果你遇到的是某个接口调用失败、参数异常或具体报错，优先看对应专题页里的排障部分；本页更偏“路径判断”和“概念澄清”。

---

## 1. 我第一次接 AI4J，应该先看哪里

优先顺序：

1. [Why AI4J](/docs/start-here/why-ai4j)
2. [Architecture at a Glance](/docs/start-here/architecture-at-a-glance)
3. [Quickstart for Java](/docs/start-here/quickstart-java)
4. [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)

---

## 2. AI4J 到底是 SDK，还是 AI 基座

更准确的说法是：

- 它当然包含 SDK
- 但项目定位不只是“一个模型接口封装包”
- 它更像一套面向 Java 生态的 AI 基座

原因是它把这些能力放进了一条连续主线：

- 模型调用
- `Tool / Function Call`
- `Skill`
- `MCP`
- Spring Boot
- Agent
- Coding Agent
- Flowgram

如果你要先理解这套基座是怎么分层的，优先看：

- [Why AI4J](/docs/start-here/why-ai4j)
- [Architecture at a Glance](/docs/start-here/architecture-at-a-glance)

---

## 3. 我是想直接用本地 coding agent，不想先读 SDK

直接看：

1. [Coding Agent 总览](/docs/coding-agent/overview)
2. [Coding Agent 快速开始](/docs/coding-agent/quickstart)
3. [发布、安装与 GitHub Release](/docs/coding-agent/install-and-release)
4. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)

---

## 4. `Chat` 和 `Responses` 该怎么选

优先 `Chat`：

- 想先打通最成熟的普通对话链路
- 更习惯消息式接口
- 重点是 Tool / Function 调用

优先 `Responses`：

- 需要更细的事件流
- 要消费 reasoning / output item / function args
- 在构建新一代 Agent runtime

对应文档：

- [Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)

---

## 5. `Function Call` 和 `Tool` 是什么关系

在 AI4J 文档语境里，最常见的第一条 Tool 主线就是本地 `Function Call`。

可以简单理解为：

- `Function Call` 是本地 Tool 的一种典型暴露方式
- `Tool` 是更宽一点的可调用能力概念

如果你现在只是想先理解第一条工具链路，优先看：

- [First Tool Call](/docs/start-here/first-tool-call)
- [Core SDK / Tools](/docs/core-sdk/tools/overview)

---

## 6. `MCP` 和 `Agent` 是什么关系

简化理解：

- `MCP` 解决“外部能力怎么接”
- `Agent` 解决“推理循环和编排怎么做”

MCP 可以成为 Agent 的工具来源，但 MCP 不等于 Agent。

---

## 7. `Agent` 和 `Coding Agent` 是什么关系

`Coding Agent` 不是通用 Agent 框架的别名。

可以理解为：

- `Agent` 是框架层
- `Coding Agent` 是面向本地代码仓交互的产品层

如果你要在业务系统里实现自己的智能体，先看 `Agent`。
如果你要直接拿来当本地编码助手用，先看 `Coding Agent`。

---

## 8. `Skill` 和 `Tool` 有什么区别

`Skill`：

- 一般是 `SKILL.md`
- 是任务说明、模板、工作流指引
- 需要模型先读取再使用

`Tool`：

- 是结构化可调用能力
- 有 schema、有执行器
- 能被模型直接调用

---

## 9. `ACP` 和 `MCP` 有什么区别

`ACP`：

- `Coding Agent` 的宿主集成协议
- 用于 IDE / 桌面应用 / 前端接 `ai4j-cli acp`

`MCP`：

- 模型接外部能力的协议层
- 用于 Tool / Resource / Prompt 的对接与发布

两者不是一类协议。

---

## 10. 我想新增一个模型平台，应该看哪里

先看：

1. [Provider 扩展](/docs/core-sdk/extension/provider-extension)
2. [Model 扩展](/docs/core-sdk/extension/model-extension)

如果只是切换已有 provider 下的新模型名，通常不需要改 SDK 源码。

---

## 11. 我想把第三方 MCP 接进来，应该从哪里开始

如果是 1~2 个服务，先看：

- [MCP Client Integration](/docs/core-sdk/mcp/client-integration)

如果是多服务统一管理，先看：

- [Gateway and Multi-service](/docs/core-sdk/mcp/gateway-and-multi-service)
- [Tool Exposure Semantics](/docs/core-sdk/mcp/tool-exposure-semantics)

---

## 12. `Flowgram` 和 `Agent` 怎么选

优先 `Flowgram`：

- 任务天然是节点图
- 前端会画流程
- 输入输出 schema 要稳定

优先 `Agent`：

- 需要多轮推理
- 需要模型自己决定工具调用路径
- 需要 SubAgent / Teams / Trace 治理

---

## 13. 我想先跑 Flowgram demo

直接看：

1. [Flowgram 总览](/docs/flowgram/overview)
2. [Flowgram 快速开始](/docs/flowgram/quickstart)

---

## 14. 文档里还有旧路径或迁移页怎么办

当前站点还在持续从旧结构收敛到新的专题结构。

如果看到迁移页，优先跟着页面给出的正式入口继续阅读，不要同时在旧页和新页之间来回跳。

---

## 15. 我应该优先看 FAQ 还是术语表

如果你卡在“我该看哪条线”，先看 FAQ。

如果你卡在“这几个名词到底什么意思”，看：

- [术语表](/docs/glossary)
