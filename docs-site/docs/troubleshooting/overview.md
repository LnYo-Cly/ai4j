---
sidebar_position: 1
---

# Troubleshooting

本页是生产排障入口。它不列出所有异常栈，而是按 AI4J 的能力层把问题定位到正确页面和检查项。

## 先定位是哪一层

| 现象 | 优先检查 |
| --- | --- |
| 模型请求失败、401、404、模型不存在 | provider key、baseUrl、model、PlatformType |
| Chat 能用，Responses 不能用 | provider 是否支持 Responses，模型是否匹配 |
| Streaming 没有增量输出 | provider streaming 支持、HTTP client、前端消费方式 |
| Tool 没被模型调用 | tool schema、system prompt、工具白名单、模型工具调用能力 |
| Tool 调了但执行失败 | ToolExecutor、入参校验、业务异常和超时 |
| MCP 连不上 | transport、server 命令、URL、token、handshake、timeout |
| RAG 召回为空 | 文档是否入库、embedding 是否成功、vector store filter |
| RAG 答案质量差 | chunk、召回数量、rerank、引用和 prompt |
| Agent 循环停不下来 | maxSteps、stop condition、tool result、memory |
| Coding Agent 不能写文件或跑命令 | workspace 边界、approval、tool policy |
| FlowGram task 没结果 | validate、task store、runtime report、节点 executor |

## Provider 和模型调用

检查顺序：

1. key 是否存在且来自正确环境变量。
2. baseUrl 是否包含正确协议、域名和路径。
3. model 名称是否是当前 provider 可用模型。
4. 当前 service 面是否被 provider 支持。
5. 非流式先跑通，再排 streaming。

相关页面：

- [Model Access Overview](/docs/core-sdk/model-access/overview)
- [Platform and Service Matrix](/docs/core-sdk/platform-service-matrix)
- [Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)

## Spring Boot 配置

如果普通 Java 能跑，Spring Boot 不能跑，优先看：

- 配置项是否落在正确的 `ai.*` 命名空间。
- 单实例配置和 `ai.platforms[]` 是否混用导致路由不符合预期。
- `AiConfigAutoConfiguration` 是否被 Spring 扫描到。
- 业务自定义 Bean 是否覆盖了默认 Bean。

相关页面：

- [Auto Configuration](/docs/spring-boot/auto-configuration)
- [Configuration Reference](/docs/spring-boot/configuration-reference)
- [Bean Extension](/docs/spring-boot/bean-extension)

## Tool 和 MCP

Tool 问题要分清“模型是否看见工具”和“系统是否执行成功”。

- 模型没看见：检查工具列表、schema、tool whitelist、prompt。
- 模型看见但没选：检查模型能力、用户指令、工具描述是否清楚。
- 模型选了但失败：检查入参、执行器、外部 API、异常处理。
- MCP 工具不可见：检查 MCP client 是否 connected，gateway 是否注册工具。

相关页面：

- [Tools Overview](/docs/core-sdk/tools/overview)
- [Tool Execution Model](/docs/core-sdk/tools/tool-execution-model)
- [MCP Overview](/docs/mcp/overview)
- [Gateway Management](/docs/mcp/gateway-management)

## RAG

RAG 排障建议先分成 ingestion、retrieval、generation 三段。

| 段 | 检查 |
| --- | --- |
| Ingestion | 文件是否读取成功、chunk 是否生成、embedding 是否写入向量库 |
| Retrieval | query embedding 是否成功、filter 是否过严、topK 是否过小 |
| Generation | prompt 是否带入 context、引用是否保留、模型是否忽略资料 |

相关页面：

- [Search & RAG Overview](/docs/core-sdk/search-and-rag/overview)
- [Ingestion Pipeline](/docs/core-sdk/search-and-rag/ingestion-pipeline)
- [Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)

## Agent / Coding Agent

通用 Agent 优先看：

- runtime 是 ReAct、CodeAct 还是 DeepResearch。
- maxSteps 是否足够或过大。
- memory 是否把旧工具结果反复带入。
- trace 是否能定位停在哪一步。

Coding Agent 额外看：

- workspace 根目录是否正确。
- 文件和 shell tool 是否需要 approval。
- session 是否从旧状态恢复。
- compact / checkpoint 是否影响当前上下文。

相关页面：

- [Agent Trace](/docs/agent/trace-observability)
- [Coding Agent Session Runtime](/docs/coding-agent/session-runtime)
- [Coding Agent Tools and Approvals](/docs/coding-agent/tools-and-approvals)

## FlowGram

FlowGram 排障按 task lifecycle 看：

1. `/flowgram/tasks/validate` 是否通过。
2. `/flowgram/tasks/run` 是否返回 taskId。
3. `/flowgram/tasks/{taskId}/report` 是否能看到节点状态。
4. 失败节点的 executor 类型和错误是否清楚。
5. `/flowgram/tasks/{taskId}/result` 是否在结束后可读。

相关页面：

- [FlowGram Overview](/docs/flowgram/overview)
- [API and Runtime](/docs/flowgram/api-and-runtime)
- [Runtime](/docs/flowgram/runtime)

## 提交 Issue 前建议附带的信息

- AI4J 版本和使用模块。
- Java / Maven / Spring Boot 版本。
- provider、baseUrl 类型和模型名，但不要贴真实 key。
- 最小复现代码或配置片段。
- 是否使用 Tool、MCP、RAG、Agent、Coding Agent 或 FlowGram。
- 错误栈、trace id、关键日志片段。
- 期望行为和实际行为。
