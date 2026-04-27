# Flowgram Architecture

`Flowgram` 的架构重点，不是单讲 `Flowgram.ai` 前端库本身，而是讲“前端画布 + AI4J 后端执行层”如何拼成一套完整系统。

## 1. 先看整体分层

可以先把这条线压成四层：

- `Flowgram.ai` 前端画布 / editor
- `ai4j-flowgram-webapp-demo/` 前端接入示例
- `ai4j-flowgram-spring-boot-starter/` starter、task API、task store、runtime facade
- `ai4j-agent/.../flowgram` 后端核心执行引擎

也就是说，真正的后端执行核心并不在 web demo，而是在 Java runtime 模块里。

## 2. 关键模块路径

### 前端侧

- `ai4j-flowgram-webapp-demo/`

它负责：

- 画布 UI
- schema 组织
- 前后端交互示例

### 后端核心 runtime

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/flowgram`

核心类包括：

- `FlowGramRuntimeService`
- `FlowGramLlmNodeRunner`
- `FlowGramNodeExecutor`
- `FlowGramRuntimeListener`

这一层解决“节点如何真正执行”。

### Spring Boot 平台接入层

- `ai4j-flowgram-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/flowgram/springboot`

核心类包括：

- `FlowGramAutoConfiguration`
- `FlowGramTaskController`
- `FlowGramRuntimeFacade`
- `FlowGramTaskStore`
- `JdbcFlowGramTaskStore`
- `InMemoryFlowGramTaskStore`

这一层解决“怎么把 runtime 变成平台可调用的 HTTP / Spring 能力”。

## 3. 主执行链怎么走

最核心的主链是：

```text
Flowgram.ai front-end
  -> task API
  -> FlowGramTaskController
  -> FlowGramRuntimeFacade
  -> FlowGramRuntimeService
  -> LLM runner / node executors
  -> task store / report / trace projection
```

这条链解释了为什么 `Flowgram` 不是单纯前端库接个接口，而是一套完整平台后端。

## 4. 节点体系在架构里的位置

节点体系至少分两层：

- 结构节点：`Start`、`End`
- 可执行节点：`LLM`、`Variable`、`Code`、`Tool`、`HTTP`、`KnowledgeRetrieve`

其中：

- `LLM` 节点由 `FlowGramLlmNodeRunner` 驱动
- 其他内置或自定义节点由 `FlowGramNodeExecutor` 实现

这也是为什么 Flowgram 的扩展点天然是“节点执行器”，不是随意写前端 schema 就完事。

## 5. 和 Agent 的边界再强调一次

`Agent` 偏自由推理 runtime。

`Flowgram` 偏平台化 workflow backend。

两者会复用一些底层 AI 能力，但组织方式不同：

- `Agent`：模型在 loop 中自己决定下一步
- `Flowgram`：节点图先定义，runtime 稳定执行

## 6. 这页之后看什么

- 想看任务 API 和运行链：看 [Runtime](/docs/flowgram/runtime)
- 想看前后端怎么接：看 [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
- 想看节点能力：看 [Built-in Nodes](/docs/flowgram/built-in-nodes)
