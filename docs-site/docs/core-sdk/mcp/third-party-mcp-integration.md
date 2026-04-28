# Third-party MCP Integration

接第三方 MCP 服务时，重点从来都不是“能不能连”，而是：**怎样把外部能力安全地接进宿主，并保持暴露边界稳定。**

## 1. 先确认三件事

- transport 类型是什么
- 对方到底暴露哪些 capability
- 你准备让当前请求开放哪些服务 id

如果这三件事没确认，就算 demo 能跑，后续也很容易失控。

## 2. 在 AI4J 里怎么接

最关键的抓手有：

- `McpConfig`
- `McpServerReference`
- `McpGateway`
- `McpTypeSupport`
- `mcpServices(...)`

从这组对象就能看出，AI4J 接第三方 MCP 不是“临时透传一个 URL”，而是纳入正式的配置、transport 和请求白名单体系。

## 3. 最大的风险其实不是协议，而是权限

第三方 MCP 往往背后挂着真实系统权限：

- GitHub 写权限
- 数据库访问权限
- 浏览器登录态
- 内部系统操作权限

如果你只是把它看成“一个远端工具”，就会低估它的安全面。

所以第三方 MCP 最核心的问题是：

- 它本身能做什么
- 当前模型到底应该被允许看到什么

## 4. AI4J 当前做对了什么

AI4J 已经把最关键的第一层边界做好了：

- 服务要先接进 gateway
- 请求要再显式写 `mcpServices(...)`

这就避免了“只要连上就全量开放”的危险默认值。

## 5. 注意事项

### 5.1 第三方服务全量开放

这是最常见也最危险的错误。

### 5.2 把业务权限问题留给模型自己判断

模型不能替代宿主权限系统。

### 5.3 忽略 transport 和生命周期

远端连接失败、重连、关闭都不是小问题，尤其在长任务场景里。

## 6. 设计摘要

> AI4J 接第三方 MCP 的关键不在“能不能连”，而在“gateway 接入 + 请求白名单 + 模型外权限控制”三层边界。第三方 MCP 本质上带着真实外部系统权限，所以不能把它当成普通本地 tool 来看。

## 7. 关键对象

如果你要继续往代码验证这一页的判断，优先看：

- `config/McpConfig.java`
- `mcp/entity/McpServerReference.java`
- `mcp/gateway/McpGateway.java`
- `mcp/util/McpTypeSupport.java`

这组对象覆盖了配置、服务描述、接入管理和 transport 归一化四个关键点。

## 8. 为什么权限审批不能交给模型

第三方 MCP 背后往往连接真实系统，所以权限边界必须由宿主控制：

- 模型只能在可见能力面内做选择
- 审批、白名单和身份权限应当在模型外完成
- 是否真正执行某个高风险操作，不应该由 prompt 自行兜底

这也是第三方 MCP 和普通本地工具最本质的工程差异之一。

## 9. 继续阅读

- [MCP / Client Integration](/docs/core-sdk/mcp/client-integration)
- [MCP / Transport Types](/docs/core-sdk/mcp/transport-types)
