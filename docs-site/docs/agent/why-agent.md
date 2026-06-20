# Why Agent

`Agent` 这一层存在的原因，不是为了把 Core SDK 再包一层，而是因为一旦系统进入多步执行，业务代码迟早会自己长出一套 runtime。

这不是抽象爱好，而是工程事实。

只要你开始处理下面这些事情：

- 一轮结束后要不要继续
- 模型返回 tool call 之后怎么执行
- 工具结果怎么进入下一轮
- 同一个任务跨多步怎样保留状态
- 出问题时怎么知道卡在哪一步

你其实就已经不再只是“调模型”，而是在实现一个 Agent runtime。

## 1. 先抓住 6 个关键设计决策

### 1.1 Core SDK 解决的是“能力访问”，Agent 解决的是“能力组织”

Core SDK 已经能解决很多问题：

- provider 接入
- Chat / Responses 请求
- 工具 schema
- MCP
- RAG / Search / Vector

但它不替你决定：

- 什么时候继续下一轮
- tool call 何时执行
- 工具错误是终止还是回灌
- 会话状态怎么延续

这些都已经超出“调用 API”范畴。

### 1.2 一旦进入模型主导工具调用，业务代码就不再是简单 orchestrator

如果工具是业务代码自己决定调用的，那你还可以把模型当成“纯函数”。

但当模型自己返回 tool call 时，系统马上要回答：

- 调不调用
- 能不能调
- 参数是否合法
- 调完之后下一轮 prompt 怎么变

这时应用层已经不是“调用工具的主人”，而是在托管一个循环系统。

### 1.3 多步任务天然会逼出一份状态源

单次调用不太需要认真讨论状态。

但多步任务一定会逼出这些问题：

- 用户输入存哪
- 模型输出存哪
- 工具结果怎么回灌
- 历史记录怎么裁剪
- 新会话和旧会话怎么分开

AI4J 用 `AgentMemory` 明确承接这层问题，而不是让业务代码到处手写消息列表。

### 1.4 “自己写一套 if/else 主循环”很快会失控

最开始看起来很简单：

1. 调模型
2. 如果有 tool call，就执行
3. 再调一次模型

但很快会长出更多问题：

- `maxSteps`
- stream 事件
- tool call 参数校验
- 并行工具调用
- 错误回灌
- trace
- session

到这一步，代码已经不是“辅助逻辑”，而是一套正在扩张的 runtime。

### 1.5 Agent 的价值不是让模型更聪明，而是让 runtime 更稳定

`ai4j-agent` 不会凭空让模型能力跃迁。

它真正带来的稳定收益是：

- 统一主循环
- 统一状态语义
- 统一工具治理边界
- 统一事件流
- 统一 runtime 切换入口

### 1.6 这层必须贴着 AI4J 基座长，而不是平行再造一套框架

AI4J 的 Agent 不是独立技术栈，而是延续：

- provider 接入
- tool 基础设施
- MCP
- Java 8 兼容边界

这意味着它是 SDK 基座的自然上层，而不是再外接一个完全独立的 Agent Framework。

## 2. 什么时候 Core SDK 已经足够

下面这些场景，通常没必要先上 Agent：

- 单次问答或单次结构化生成
- 工具调用由业务代码显式决定
- 工作流已经由应用层显式编排，模型只是其中一个节点
- 你只需要基础上下文，不需要 tool loop

这类问题本质上还是：

- 一次请求怎么构造
- 一次响应怎么解析

Core SDK 更轻、更直接，也更容易控复杂度。

## 3. 什么时候问题已经升级为 Agent 问题

当系统出现下面这些信号时，问题本质已经变了：

### 3.1 你开始需要“本轮之后怎么办”

这对应的就是 runtime loop 问题，而不是单次请求问题。

### 3.2 工具结果必须回到模型，而不是直接回到业务层

只要工具输出还要继续喂给模型，这就已经进入 Agent loop。

### 3.3 你开始维护一份持续状态

不管它叫：

- memory
- session
- history
- task context

本质上你都在维护可持续运行状态。

### 3.4 你开始关心 step budget、trace、retry、事件流

这些都说明你已经不再只是在“拼一个 API 请求”。

### 3.5 你需要切不同执行策略

比如：

- 普通 ReAct
- CodeAct
- 先规划再执行

这时“调用模型”已经变成“选择 runtime”。

## 4. AI4J Agent 具体解决的不是哪类概念，而是哪类代码

### 4.1 Loop 代码

`BaseAgentRuntime.runInternal(...)` 已经把默认主循环统一下来：

- 写入用户输入
- 组 prompt
- 调模型
- 回写 memory
- 归一化 tool calls
- 校验
- 执行工具
- 回写结果
- 决定是否继续

这避免你在每个项目里重复长出一份 loop。

### 4.2 状态代码

`AgentMemory` 统一承接：

- 用户输入
- 模型输出
- 工具输出

避免业务层自己维护“半结构化消息数组 + 一些临时变量”的状态拼装。

### 4.3 治理代码

`AgentToolRegistry` 和 `ToolExecutor` 的拆分，直接把：

- 工具可见性
- 工具执行权限

这两层边界固定下来。

所以权限审批、拦截、审计不再需要靠“把工具藏起来”这种脆弱办法实现。

### 4.4 观测代码

`AgentEventPublisher` 和 trace 体系让：

- `STEP_START`
- `MODEL_REQUEST`
- `MODEL_RESPONSE`
- `TOOL_CALL`
- `TOOL_RESULT`
- `FINAL_OUTPUT`

这些关键阶段都变成正式事件，而不是外围猜测。

## 5. 为什么不是直接在业务代码里自己写一套

当然可以自己写。

但自己写一套会很快遇到 5 个问题：

### 5.1 协议逻辑和运行逻辑会缠在一起

你会把：

- Chat / Responses 协议细节
- tool loop
- 状态管理

写在同一层代码里。

### 5.2 每个项目都会重复长一套近似但不兼容的主循环

刚开始差别不大，后面越来越难复用。

### 5.3 想切 runtime 时会推倒重来

例如从：

- ReAct

切到：

- CodeAct

如果没有统一运行时边界，通常等于重写一大截调用链。

### 5.4 trace、session、subagent 会变成补丁

没有统一 runtime 的情况下，这些能力只能靠外围 patch 进去，越来越碎。

### 5.5 错误语义会不一致

例如：

- 工具失败要不要终止
- 校验错误怎么处理
- 超步数算不算失败

如果每个项目都自己定义，最后系统行为很难预测。

## 6. 为什么是 AI4J 里的 Agent，而不是另一套独立框架

这点对 Java 工程尤其重要。

AI4J 的 Agent 不是希望你：

1. 先引入一个独立 Agent Framework
2. 再桥接回 Java SDK
3. 再桥接回 MCP / Tool / RAG / 宿主系统

它选择的是另一条路线：

- Agent 直接建立在 AI4J 基座之上

这带来很现实的收益：

### 6.1 能继续复用现有能力面

- provider
- tools
- MCP
- RAG
- memory

### 6.2 能继续留在现有 Java 兼容边界

这对 monorepo 里维持 Java 8 兼容尤其重要。

### 6.3 能自然衔接更上层模块

- `ai4j-agent`
- `ai4j-coding`
- `ai4j-cli`
- `ai4j-flowgram-*`

可以沿同一组基础抽象逐层上升。

## 7. Agent 真正带来的主要收益

### 7.1 主循环统一

不再每个项目都手写一套“模型 -> 工具 -> 再模型”的循环。

### 7.2 执行策略可替换

通过 `AgentRuntime` 抽象，可以切：

- ReAct
- CodeAct
- DeepResearch

### 7.3 状态语义统一

通过 `AgentMemory`，状态不再只是 prompt 拼接副产物，而是正式对象。

### 7.4 工具治理边界统一

通过 registry / executor 分离，权限治理终于有了稳定落点。

### 7.5 可观测性统一

通过标准事件流和 trace 投影，运行过程不再是黑盒。

## 8. Agent 不解决什么

这层也有明确边界。

### 8.1 不替代 Core SDK

如果只是单次调用模型，Core SDK 仍然更适合。

### 8.2 不自动变成产品级 coding assistant

Agent 不自动具备：

- workspace-aware 权限
- 进程管理
- checkpoint / compact outer loop
- CLI / TUI / ACP host

这些属于 `ai4j-coding` 和 `ai4j-cli`。

### 8.3 不替代显式 workflow 设计

如果问题天然是图式节点流转，`workflow` / `StateGraph` / `Flowgram` 更合适。

### 8.4 不自动替你定义安全策略

框架提供的是治理边界，不是你业务场景的最终审批策略。

## 9. 使用 Agent 的代价和约束

进入 Agent 之后，复杂度确实会上升。

你要开始认真面对：

- step budget
- session / state
- 工具暴露面和执行面
- 回归验证
- 观测与排障

所以“进入 Agent”既是能力升级，也是工程责任升级。

## 10. 一个更实用的决策表

| 问题类型 | 更适合的选择 |
| --- | --- |
| 单次回答、单次结构化输出 | Core SDK |
| 模型按需调用工具、需要多轮闭环 | Agent |
| 本地代码仓交互、审批、会话恢复 | Coding Agent |
| 显式节点图、任务 API、平台化执行 | Flowgram |

## 11. 推荐阅读源码顺序

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agent.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/AgentMemory.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/AgentToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/event/AgentEventPublisher.java`

## 12. 继续阅读

1. [Agent Overview](/docs/agent/overview)
2. [Architecture](/docs/agent/architecture)
3. [Quickstart](/docs/agent/quickstart)
4. [Tools and Registry](/docs/agent/tools-and-registry)
5. [Memory and State](/docs/agent/memory-and-state)
