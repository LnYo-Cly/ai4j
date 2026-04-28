# Skill vs Tool vs MCP

这三个词是 AI4J 文档里最容易被混掉的部分。如果不把它们拆开，你会把“说明文档”“本地执行能力”“外部服务协议”全部看成一个东西。

## 1. 先给一句最短定义

- `Skill`：告诉模型“这类任务应该怎么做”的方法论资源
- `Tool`：让模型在当前宿主里“真的去做”的可执行能力
- `MCP`：让模型用标准协议接入外部能力系统

这三个概念**会协作**，但它们不是一层东西。

## 2. 为什么大家总会把它们混在一起

因为在运行时，它们都可能最终影响“模型下一步能干什么”：

- skill 会影响模型决策
- tool 会提供本地执行面
- MCP 会引入外部执行面

结果就是很多人会把它们都统称成“给模型的工具”。这在直觉上没错，但在工程上是错的。

因为一旦这样看，你就会失去：

- 文档边界
- 安全边界
- 协议边界
- 运行时治理边界

## 3. 各自的源码入口在哪

### Skill

- `skill/Skills.java`
- `skill/SkillDescriptor.java`

### Tool

- `annotation/FunctionCall.java`
- `tool/ToolUtil.java`
- `tools/*Function.java`

### MCP

- `config/McpConfig.java`
- `mcp/gateway/McpGateway.java`
- `ChatCompletion.mcpServices`
- `ResponseRequest.mcpServices`

单看这些入口，你就能发现：

- skill 关心的是发现和读取
- tool 关心的是声明和本地执行桥接
- MCP 关心的是协议接入和服务编组

## 4. 为什么 MCP 不应该挂在 `Tools` 下面理解

这是一个你前面已经指出来的关键问题。

**概念上**，MCP 不应该被理解成 `Tools` 的子集。原因有两个：

1. `Tool` 解决的是本地宿主如何暴露可执行能力
2. `MCP` 解决的是外部服务如何通过标准协议接进来

只是到了“发给模型”的最后一步，MCP 里的工具型能力会被转换成 `Tool.Function` 风格 schema，所以运行时看起来像 tool。

这就像：

- `HTTP API` 最终也可能被包装成某个 SDK 方法
- 但你不能因此说 “HTTP API 就是一个 Java 方法”

MCP 在架构上是**协议接入层**，不是本地工具声明层。

## 5. 三者最典型的协作方式

在实际系统里，最常见的组合是：

1. 用 `Skill` 规定方法
2. 用本地 `Tool` 提供宿主内动作
3. 用 `MCP` 接浏览器、GitHub、数据库、搜索等外部系统

例如一个 coding agent 任务：

- skill 先告诉模型“先读任务说明，再定位源码，再执行回归”
- 本地 tool 提供 `read_file`、`apply_patch`、`bash`
- MCP 再接 GitHub、浏览器、第三方搜索

所以 `Skill` 常常决定路径，`Tool/MCP` 决定执行面。

## 6. 一个很实用的判断表

| 你要解决的问题 | 应优先用什么 |
|----------------|--------------|
| 给模型一套任务方法论 | `Skill` |
| 暴露本地 Java 函数 | `Tool` |
| 连接 GitHub / 浏览器 / 数据库 / 内部服务 | `MCP` |
| 让模型先学会流程，再按流程执行 | `Skill + Tool` |
| 把远端能力标准化接进宿主 | `MCP` |

这张表适合快速判断“这个需求到底落在基座哪一层”。

## 7. 这些概念最容易被写错的地方

### 7.1 把 skill 写成执行说明 + 权限声明 + 工具实现

skill 应该写方法论，不应该承担执行宿主职责。

### 7.2 把 MCP 描述成“远程工具”

这太窄了。MCP 还包括 resources、prompts、transport、网关、客户端生命周期。

### 7.3 把 tool 当成审批系统

tool 只是执行面。审批和权限控制应该由宿主完成。

## 8. 从“AI 基座”角度怎么理解

如果从基座角度拆：

- `Skill`：上下文治理和方法论复用
- `Tool`：宿主内能力暴露
- `MCP`：宿主外能力接入

这三者确实都属于 AI 基座，但它们承担的是不同维度的基座职责。

这也是为什么你前面问“skills 难道不是 ai 基座吗”，答案是：**是，而且它是基座里的方法论/上下文层**，不是附属物。

## 9. 设计摘要

AI4J 里 `Skill`、`Tool`、`MCP` 是三种不同维度的基座能力：skill 负责按需读取的方法论，tool 负责本地执行，MCP 负责外部服务协议接入。MCP 最终可以被展开成 tool schema，但概念上它仍然是协议层，不是 tools 的子页面。

## 10. 继续阅读

- [Skills / Discovery and Loading](/docs/core-sdk/skills/discovery-and-loading)
- [MCP / Positioning and When to Use](/docs/core-sdk/mcp/positioning-and-when-to-use)
- [Tools / Tool Execution Model](/docs/core-sdk/tools/tool-execution-model)
