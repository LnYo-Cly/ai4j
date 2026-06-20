# core sdk invocation contract audit - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### F-001 Core SDK 已有清晰对象链

- 背景：用户指出 lightweight `ChatClient` 不是原有实现，要求重新对比当前真实调用方式。
- 发现：`Configuration` 聚合 provider/HTTP/vector/MCP 配置，`AiService` 基于该配置显式创建 Chat/Responses/Embedding/RAG 等服务，`IChatService` 接收 `ChatCompletion` 并返回 `ChatCompletionResponse`。
- 影响：后续升级应强化对象链可读性，不应再用隐藏 facade 代替真实合同。
- 后续：docs-site 可以单独做对象链表达优化。

### F-002 多实例能力已有正式入口

- 背景：需要判断 Provider Profile 是否应另起体系。
- 发现：`AiServiceRegistry` / `DefaultAiServiceRegistry` 已通过 `AiConfig.platforms`、`AiPlatform.id`、`PlatformType` 和 scoped `AiService` 管理多实例。
- 影响：provider/profile 升级应优先增强 registry 和 Spring binding，而不是新增平行 `Ai4j.profile(...)` 入口。
- 后续：如要支持 TroveBox/OpenAI-compatible 中转平台，先审计 `AiPlatform` 字段是否覆盖 base URL、模型、能力矩阵。

### F-003 Tool/MCP 是请求级白名单，不是 client 级对象挂载

- 背景：此前提出 `.tools(new WeatherTools())`，需要验证是否贴合现有实现。
- 发现：当前 `ToolUtil` 基于注解扫描和静态缓存管理工具，`ChatCompletion.functions(...)` 与 `mcpServices(...)` 决定本次请求暴露面，provider service 调用 `ToolUtil.getAllTools(...)` 和 `ToolUtil.invoke(...)`。
- 影响：把工具对象直接挂到 Chat facade 会偏离现有白名单模型。
- 后续：如升级 Tool，应设计显式注册/白名单体验。

### F-004 RAG 是独立服务合同

- 背景：此前提出 `.rag(rag)`，需要验证是否贴合现有实现。
- 发现：`DefaultRagService` 组合 `Retriever`、`Reranker`、`RagContextAssembler`，输出 `RagResult`、citations、trace；它不是 Chat 请求的内联开关。
- 影响：RAG 优先升级 recipe 和 context assembly，而不是 Chat facade。
- 后续：可创建 RAG recipe 任务。

### F-005 Memory 是 Chat/Responses 共享事实层

- 背景：需要判断 memory 是否适合绑定到 Chat facade。
- 发现：`ChatMemory` 提供 `toChatMessages()` 和 `toResponsesInput()`，可同时服务 Chat 与 Responses。
- 影响：Memory 应保持独立事实层，不要被单一 Chat client 吞掉。
- 后续：docs-site 可强化 Memory 投影说明。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| D-001 | 保留 `Configuration -> AiService -> IChatService -> ChatCompletion -> ChatCompletionResponse` 为主线 | 这是源码和文档里真实存在、覆盖能力最全的合同 | 新增 `ChatClient` / `Ai4j.chat()` 主入口 | accepted |
| D-002 | 不新增隐藏式 Chat facade | 之前 lightweight `ChatClient` 已证明会抢占主入口并误导 API 判断 | 改名保留 facade | accepted |
| D-003 | Provider/profile 升级优先走 `AiServiceRegistry` | registry 已是正式多实例抽象 | 另起 profile facade | accepted |
| D-004 | RAG/Tool/Memory 保持独立能力边界 | 三者已有明确事实层和执行层 | 塞进 `.rag().tools().memory()` 链式调用 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否要做任何新公开 API | 本任务不做；需要单独设计并人工确认 | user / coordinator | 后续 API 设计任务 |
