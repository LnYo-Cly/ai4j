# Coding Agent Architecture

`Coding Agent` 的架构重点不是“在 `AgentBuilder` 外面再包一层 API”，而是把通用 agent 运行时、workspace 语义、长期会话、宿主交互和外部工具接入拼成一条稳定的本地交付链。

如果不从“执行链”看，你会低估很多类存在的必要性。

---

## 1. 先看整条主链

当前最接近真实运行的主链可以压成下面这样：

```text
CodeCommand / AcpJsonRpcServer
  -> DefaultCodingCliAgentFactory
  -> CodingAgentBuilder
  -> CodingSession
  -> CodingAgentLoopController
  -> built-in tools / approval decorators / MCP tools / delegate tools
  -> Agent / AgentSession / model client
```

如果再把 session 管理和持久化补进去，更完整的图是：

```text
host runtime
  -> session manager + session runtime
  -> coding runtime
  -> tool surface + approval + MCP
  -> base agent runtime
```

这几层不是文档上的抽象划分，而是当前代码里真实分离的职责边界。

---

## 2. 最底层还是 `Agent`，但不会直接暴露给用户

最底层仍然是通用 `Agent` 能力：

- `Agent`
- `AgentBuilder`
- `AgentSession`
- `AgentRuntime`
- `AgentModelClient`
- `AgentToolRegistry`
- `ToolExecutor`

这一层负责的事情仍然是：

- 模型调用
- step 内部 tool loop
- 基础 memory
- 工具调用抽象

但这里还没有 Coding Agent 最关心的东西：

- workspace 路径边界
- session 持久化
- approvals
- 进程管理
- auto-continue
- compact / checkpoint
- CLI / TUI / ACP 接入

所以 `Coding Agent` 的架构不是替换它，而是在它上面叠加多层运行语义。

---

## 3. `CodingAgentBuilder` 是真正的分叉点

从“通用 agent”切到“coding agent”的第一个关键分叉就是 `CodingAgentBuilder.build()`。

它做的不是简单赋值，而是一次完整装配：

1. 校验 `modelClient` 和 `model`
2. 归一化 `WorkspaceContext`
3. 调 `CodingSkillDiscovery.enrich(...)`
4. 准备 `CodingAgentOptions`、`AgentOptions`
5. 准备 `CodingAgentDefinitionRegistry`
6. 准备 `CodingRuntime`
7. 创建 built-in tool registry
8. 创建 built-in tool executor
9. 合并外部 tools，例如 MCP
10. 合并 subagent tools
11. 按配置把 workspace prompt prepend 到 system prompt
12. 最后调用通用 `AgentBuilder` 构建底层 `Agent`

所以 `CodingAgentBuilder` 的真实责任是：

- 把“仓库交付所需的外围语义”压进一个普通 `Agent`

如果你要判断一个能力该落在 `Agent` 还是 `Coding Agent`，一个很实用的问题就是：

- 这个能力是否依赖 workspace / session / host / coding tool policy？

如果依赖，通常就不该直接塞进通用 `Agent` 层。

---

## 4. workspace 层为什么必须独立

`WorkspaceContext` 是整个 coding 架构的边界基线。

它当前至少定义了这些事情：

- root path
- excluded paths
- 是否允许显式越出 workspace
- skill directories
- allowed read roots
- available skills

更关键的是它有两套路径解析语义：

- `resolveWorkspacePath(...)`
- `resolveReadablePath(...)`

这说明当前架构明确区分：

- 能写到哪里
- 能读到哪里

skill roots 之所以能放在 workspace 外部，就是因为 `allowedReadRoots` 只扩展读取面，而不扩展写入面。

这层如果没有独立出来，很多 coding-specific 规则只能散落到各个 tool executor 里，最终会很难维护。

---

## 5. 工具层不是单一 registry，而是多来源合并

当前可见工具面至少来自四类来源：

1. built-in coding tools
2. 外部注入工具，例如 MCP
3. delegate / subagent tools
4. 更底层 `Agent` 已支持的工具抽象

`CodingAgentBuilder` 会先生成 built-ins，再合并外部 registry / executor。

这意味着工具架构的核心不是“列出有哪些工具”，而是：

- 不同来源的工具如何合并
- 冲突在哪里处理
- 宿主如何在装配期挂载自己的工具面

这也是为什么 MCP 接入被设计成宿主层先准备 `CliMcpRuntimeManager`，再把其 `toolRegistry` / `toolExecutor` 塞进 builder，而不是写死在 built-ins 里。

---

## 6. `CodingSession` 才是长期任务的运行容器

如果只从 API 名字看，很容易把 `CodingSession` 当成“聊天 session 包装器”。

这会误判它的职责。

当前它承担的是：

- 绑定 workspace context
- 绑定 process registry
- 绑定 coding runtime
- 支撑 state export / restore
- 记录 loop 决策
- 汇总 compact 结果

它的地位更接近：

- “一个可持久化、可恢复、可分叉的本地工作会话容器”

而不是普通聊天产品里的“conversation id”。

这就是为什么 session 架构在 Coding Agent 里被单独抬高，而不是挂在 host 层边上做个小工具类。

---

## 7. outer loop 为什么单独成层

通用 `Agent` 更接近“单次调用内的 tool loop”。

`Coding Agent` 还需要处理更高一层的任务连续性：

- 当前轮要不要自动继续
- 因为什么原因继续或停止
- 什么时候 compact
- 被 block 时怎么记录

这部分责任不应该塞进 CLI，也不应该塞进通用 `Agent`。

当前更合适的位置就是 coding session / loop 层，核心类包括：

- `CodingAgentLoopController`
- `CodingLoopDecision`
- `CodingStopReason`

这层存在的意义是：

- 把“一个用户任务可能跨多轮模型调用”的语义独立出来

否则 host 层会被迫自己猜什么时候该继续，什么时候该停。

---

## 8. `DefaultCodingRuntime` 不是 UI runtime，而是 delegation runtime

`DefaultCodingRuntime` 很容易被误读成“整个 coding agent 的总 runtime”。

更准确地说，它当前最重要的职责是：

- 管理 delegated tasks
- 创建 child session
- 跟踪 `CodingTask`
- 维护 `CodingSessionLink`
- 应用 per-definition tool policy

从 `createChildSession(...)` 能看出这层的真实地位：

1. 继承父 session 的 runtime 和上下文骨架
2. 重新创建 child 的 built-in tools 和 process registry
3. 合并 custom tools、subagent tools
4. 按 definition 应用 tool policy
5. 创建全新的 child `CodingSession`
6. 按需 restore seed state

这说明 `DefaultCodingRuntime` 解决的是：

- “子任务如何作为独立工作单元运行”

而不是：

- “终端怎么显示”

所以它和 CLI/TUI/ACP 不是同一层。

---

## 9. `DefaultCodingSessionManager` 解决的是持久化和谱系，不是执行

`DefaultCodingSessionManager` 负责：

- `create`
- `resume`
- `fork`
- `save`
- `load`
- `list`
- event append / list

注意它不负责：

- 跑模型
- 继续 outer loop
- 执行 tool

它解决的是另一类问题：

- 一个运行中的工作会话如何被命名、落盘、恢复、分叉并留下事件账本

所以在架构上应该把它看成：

- session lifecycle management

而不是：

- runtime core

这也是为什么 `HeadlessCodingSessionRuntime` 和 `CodingCliSessionRunner` 仍然需要它，但不能由它替代执行层。

---

## 10. 审批为什么放在 executor decorator 层

当前审批不是操作系统 hook，也不是 JVM instrumentation。

它是 `ToolExecutor` 装配过程里的 decorator。

CLI/TUI 路径走的是：

- `CliToolApprovalDecorator`

ACP 路径走的是：

- `AcpToolApprovalDecorator`

这背后的架构判断非常清晰：

- 审批属于“工具执行前的宿主决策”
- 不属于模型层
- 也不属于底层 OS 拦截层

这种放法有三个好处：

1. 底层 tool executor 可以保持纯执行逻辑
2. CLI/TUI 和 ACP 可以共享审批语义，但用不同宿主通道
3. 审批策略能随着 host 变化，而不污染通用 coding runtime

---

## 11. MCP 为什么单独成一个活的运行层

MCP 在当前架构里不是一组静态工具定义。

`CliMcpRuntimeManager` 需要负责：

- 读取 resolved config
- 建立连接
- 拉取 tool definitions
- 做 built-in / cross-server 冲突校验
- 维护状态快照
- 生成 tool registry / executor

也就是说它是一个带连接状态的 runtime，而不是 builder 帮你顺手 parse 一下 JSON。

这就是为什么架构图里把 MCP 单独拎出来是合理的。

否则你会误以为：

- “MCP 只是 tools 的一种配置形式”

实际上当前实现已经明显更重：

- 它是外部能力接入层
- 有自己的错误模型和运行状态

---

## 12. host runtime 为什么不是“纯 UI”

`CodeCommand`、`CodingCliSessionRunner`、`JlineCodeCommandRunner`、`AcpJsonRpcServer` 这一层经常被低估。

但它们实际负责的是：

- 解析运行选项
- 选择 CLI / TUI backend
- 创建 session manager
- 创建和切换 session runtime
- 输出 MCP 启动告警
- 响应 slash command
- 在 ACP 中转 permission request 与结构化事件

这些行为已经远远超出“把文本显示出来”。

所以 host runtime 的准确理解应该是：

- 产品入口与交互编排层

而不是：

- 渲染 UI 的最薄外壳

---

## 13. 从扩展角度看，优先改哪一层

这个问题在实际开发里非常重要。

### 想改模型调用或基础 tool loop

先看 `ai4j-agent`

### 想改 workspace 语义、coding tools、session state、delegation

先看 `ai4j-coding`

### 想改 CLI/TUI/ACP 交互、审批通道、MCP 配置、session 存储

先看 `ai4j-cli`

### 想加团队规则或任务工作流说明

优先看 skill 体系，不要先改 runtime

架构上最常见的错误不是“写不出来”，而是“把宿主逻辑写进 runtime”或“把通用逻辑写死在 CLI”。

---

## 14. 这页和相邻页面怎么分工

- `overview`：先建立全局心智
- `why-coding-agent`：解释为什么需要独立产品线
- `architecture`：解释层次、责任、主执行链
- `runtime-architecture`：更细地拆运行时部件
- `session-runtime`：解释长期任务、事件、保存与恢复
- `tools-and-approvals`：解释执行面和审批拦截
- `mcp-integration`：解释 MCP 在 coding 场景下的接入链

如果你现在想继续追“长期任务为什么能自动继续、又如何被保存”，下一页最该看的是 `session-runtime`。

---

## 15. 这页最该记住的结论

- `CodingAgentBuilder` 是从通用 `Agent` 分叉成 `Coding Agent` 的关键装配点
- `WorkspaceContext`、tool merge、skills、MCP、approval 都是架构级部件，不是零散特性
- `CodingSession` 负责长期工作容器语义，`DefaultCodingSessionManager` 负责持久化和谱系管理
- `DefaultCodingRuntime` 的重点是 delegation 和 child session，不是 UI
- host runtime 负责真正的产品交互编排，不是纯展示层

---

## 16. 推荐继续阅读

1. [Runtime Architecture](/docs/coding-agent/runtime-architecture)
2. [Session Runtime](/docs/coding-agent/session-runtime)
3. [Tools and Approvals](/docs/coding-agent/tools-and-approvals)
4. [MCP 对接](/docs/coding-agent/mcp-integration)
