# Skill vs Tool vs MCP

这三个词是 AI4J 文档里最容易被混掉的部分。

原因很简单：

- 它们最后都可能影响模型“下一步能做什么”
- 在 provider 请求层，它们最终都可能投影成某种模型可消费能力

但工程上它们绝对不是一层东西。只要混掉，你就会同时失去：

- 文档边界
- 安全边界
- 协议边界
- 运行时治理边界

## 1. 先给一句最短定义

- `Skill`
  告诉模型“这类任务应该怎么做”的方法论资源
- `Tool`
  让模型在当前宿主里“真的去做”的执行能力
- `MCP`
  让宿主把外部系统能力标准化接进来的协议层

这三个概念会协作，但职责不同。

## 2. 用一张表先把 3 层拆开

| 维度 | Skill | Tool | MCP |
| --- | --- | --- | --- |
| 主职责 | 方法论与上下文治理 | 宿主内执行能力 | 外部能力协议接入 |
| 主要载体 | `SKILL.md` | `Tool.Function` / `@FunctionCall` / built-in tools | `McpClient` / `McpGateway` / MCP server |
| 会不会直接执行动作 | 不会 | 会 | 最终可能会，但前提是先接入协议层 |
| 典型问题 | “这类任务该怎么做” | “当前宿主能做什么” | “外部系统怎么接进来” |
| 主要安全边界 | 读取哪些说明资产 | 暴露哪些工具、允许哪些本地动作 | 接入哪些服务、服务可见性和认证 |
| 属于 AI 基座吗 | 是 | 是 | 是 |

如果你只记一张表，就记这张。

## 3. 各自的源码入口分别是什么

### Skill

- `skill/Skills.java`
- `skill/SkillDescriptor.java`

关注的是：

- 发现
- 描述
- 目录提示
- 只读根

### Tool

- `annotation/FunctionCall.java`
- `annotation/FunctionRequest.java`
- `annotation/FunctionParameter.java`
- `tool/ToolUtil.java`
- `tool/BuiltInToolExecutor.java`

关注的是：

- 工具声明
- 工具白名单
- 本地执行路由

### MCP

- `mcp/client/McpClient.java`
- `mcp/gateway/McpGateway.java`
- `mcp/transport/*`
- `mcp/server/*`

关注的是：

- transport
- 多服务治理
- 协议握手
- 服务发布和接入

只看这些代码入口，就能发现 3 层的关注点完全不同。

## 4. 为什么大家总会把它们混在一起

因为在模型视角里，它们确实会共同影响下一步动作：

- skill 会改变模型决策路径
- tool 会提供本地执行面
- MCP 会把外部执行面带进来

但这种“都会影响行为”的事实，不等于它们属于同一个抽象层。

更准确的说法是：

- Skill 决定模型怎么思考和选择
- Tool 决定宿主内部能执行什么
- MCP 决定宿主外部能接入什么

## 5. 为什么 MCP 不应该挂在 `Tools` 下面理解

这是最关键的结构问题之一。

从概念上说，MCP 不是 `Tools` 的子集，原因有两个：

1. `Tool` 解决的是“如何把能力暴露给当前宿主里的模型请求”
2. `MCP` 解决的是“如何把外部服务通过标准协议接进宿主”

只是到了最后一步，MCP 里的工具型能力会被投影成 `Tool.Function` 风格 schema，于是“看起来像工具”。

这个现象要和归属关系分开看。

更准确的理解是：

- MCP 工具最终会投影到 tool surface
- 但 MCP 本身仍然是协议接入层

就像：

- 远端 HTTP API 最终也可能被包装成一个本地 SDK 方法
- 但你不会因此说“HTTP API 就是 Java 方法”

## 6. 为什么 Skill 也属于 AI 基座

这也是一个经常被低估的点。

`Skill` 当然属于 AI 基座，只不过它不在执行层，而在：

- 方法论复用层
- 上下文治理层

它解决的是：

- 哪些 SOP 值得暴露
- 说明资产怎么懒加载
- 如何避免把所有长文档永久塞进 system prompt

因此它是基座，只是不是“执行基座”，而是“方法论与上下文基座”。

## 7. 三者在一个真实系统里通常怎样协作

最常见的组合是：

1. 用 `Skill` 告诉模型流程和策略
2. 用本地 `Tool` 提供宿主内动作
3. 用 `MCP` 接浏览器、GitHub、数据库、搜索等外部能力

例如一个 coding-agent 任务：

- skill 告诉模型先读任务、再定位代码、再做回归
- 本地 tool 提供 `read_file`、`apply_patch`、`bash`
- MCP 再接 GitHub、浏览器、第三方搜索

所以常见关系是：

- `Skill` 决定方法
- `Tool` 和 `MCP` 决定执行面

## 8. 一个更实用的判断表

| 你要解决的问题 | 应优先用什么 |
| --- | --- |
| 给模型一套稳定任务方法论 | `Skill` |
| 暴露本地 Java 函数或宿主 built-in 能力 | `Tool` |
| 接 GitHub、浏览器、数据库、内部 API | `MCP` |
| 让模型先按流程思考，再调用本地能力 | `Skill + Tool` |
| 让模型既遵循流程，又使用外部服务 | `Skill + MCP` 或 `Skill + Tool + MCP` |

如果一个需求既涉及“怎么做”，又涉及“做什么”，那通常不是二选一，而是组合问题。

## 9. 这三个概念最容易被写错的地方

### 把 skill 写成权限系统

skill 应该写方法论，不应该承担：

- 权限授予
- 执行动作
- 连接外部服务

### 把 tool 写成审批系统

tool 是执行面，不是审批和治理面。审批应该由更上层 runtime 或宿主完成。

### 把 MCP 写成“远程工具列表”

这太窄了。MCP 还包括：

- transport
- client lifecycle
- gateway
- resources
- prompts
- server publish

## 10. 从模型接口看，为什么它们又会“看起来像同一层”

因为最终进入模型时，会出现一种收敛：

- skill 先以目录提示或正文进入上下文
- tool 进入 provider `tools` 列表
- MCP 工具也被投影成 provider `tools` 列表

所以在模型面前，最终都像是在影响“当前可行动空间”。

但这只是接口层收敛，不是概念层合并。

## 11. 设计时最稳的心智模型

可以这样记：

- `Skill`：指导模型
- `Tool`：让宿主内部执行
- `MCP`：让宿主外部接入

再进一步：

- `Skill` 主要改变决策路径
- `Tool` 主要改变本地能力面
- `MCP` 主要改变外部能力面

这套心智模型在读文档、做设计、做安全边界拆分时都很好用。

## 12. 这页最该记住的结论

AI4J 里的 `Skill`、`Tool`、`MCP` 都属于 AI 基座，但分别属于 3 个不同维度：

- Skill 是方法论与上下文治理层
- Tool 是宿主内执行层
- MCP 是宿主外协议接入层

MCP 最终可以被投影成 tool schema，但概念上它仍然不是 `Tools` 的子页面；同样，skill 也不是“不会执行的 tool”，而是另一种完全不同的基座能力。
