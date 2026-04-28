# Positioning and When to Use

这一页只回答一个问题：**什么时候应该进入 MCP，而不是继续停留在本地 Tool 或 Skill。**

如果这一步判断错了，后面的实现成本会很高。因为你要么会把外部服务能力硬塞成本地工具，要么会把本来只是方法论文档的问题误做成协议接入。

## 1. MCP 在 AI4J 基座里的真实定位

MCP 在 AI4J 里解决的不是“又一种工具写法”，而是：

- 外部能力如何接入宿主
- transport 如何管理
- 多服务如何统一编组
- 远端能力如何按白名单暴露给模型

所以它的本质是：

- **协议层**
- **服务接入层**
- **能力编组层**

不是“本地方法暴露层”。

## 2. 什么时候继续用本地 Tool 就够了

如果能力满足下面三个条件，通常不需要 MCP：

1. 能力就在当前 JVM 进程里
2. 你可以稳定地用 Java 类型描述输入输出
3. 不需要远端服务生命周期和 transport 治理

典型例子：

- 本地文件解析
- 固定业务函数
- 小范围工具集成

这类场景用 `@FunctionCall + ToolUtil` 就已经足够。

## 3. 什么时候该用 Skill

如果你真正要解决的是：

- 教模型一套工作流程
- 复用 SOP、模板、规范
- 按需读取方法论文档

那是 skill 的职责。

也就是说：

- `Skill` 解决“模型该怎么做”
- `Tool` / `MCP` 解决“模型能做什么”

## 4. 哪些信号说明你已经该进入 MCP

出现以下任一情况，基本就该考虑 MCP：

- 要接 GitHub、浏览器、数据库、内部平台 API
- 需要连接多个外部服务
- 服务存在独立 transport 和生命周期
- 你打算把能力发布给别的 agent 或客户端

可以先记成：**只要跨进程、跨服务、跨系统，就优先想 MCP。**

## 5. AI4J 里的关键代码入口

- 配置入口：`config/McpConfig.java`
- 网关：`mcp/gateway/McpGateway.java`
- transport 类型归一化：`mcp/util/McpTypeSupport.java`
- 请求侧挂载点：`ChatCompletion.mcpServices`、`ResponseRequest.mcpServices`

从这些入口你就能看出，MCP 在 AI4J 里已经不是一个“概念性扩展”，而是一条正式进入 `Chat` / `Responses` 请求链的能力面。

## 6. 一个很实用的决策表

| 需求 | 更适合什么 |
|------|------------|
| 把本地 Java 能力开放给模型 | Tool |
| 给模型一套可复用方法论 | Skill |
| 连接外部能力系统 | MCP |
| 多服务统一调度 | MCP |
| 只是在 prompt 里教模型先做什么后做什么 | Skill |

这张表在做方案设计时非常好用，因为它能帮你避免架构误分类。

## 7. MCP 不只是“第三方工具”

这点也要讲清楚。

MCP 不是简单的“远程工具列表”，它还包括：

- transport
- gateway
- 用户级与全局级服务隔离
- resource / prompt 等协议能力

所以如果文档里只把它写成“模型能调的外部工具”，那是明显缩水了。

## 8. 注意事项

### 8.1 把外部 API 临时包一层就当本地 tool

短期能跑，长期会失去 transport、连接、服务边界治理。

### 8.2 把 MCP 写成 skill

skill 是说明文档，不是服务接入协议。

### 8.3 把 MCP 和本地工具混成一个安全面

远端权限和本地副作用风险不是一回事，治理方式也不同。

## 9. 设计摘要

> 在 AI4J 里，MCP 不是 Tools 的子概念，而是协议化外部能力接入层。只要问题开始跨进程、跨服务、跨系统，就不该继续停留在本地 tool 心智，而应该进入 MCP 的 transport、gateway 和服务白名单体系。

## 10. 继续阅读

- [MCP / Client Integration](/docs/core-sdk/mcp/client-integration)
- [MCP / Gateway and Multi-service](/docs/core-sdk/mcp/gateway-and-multi-service)
- [Skills / Skill vs Tool vs MCP](/docs/core-sdk/skills/skill-vs-tool-vs-mcp)
