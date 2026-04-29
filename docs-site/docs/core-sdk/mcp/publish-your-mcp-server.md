# Publish Your MCP Server

这一页讲的是：**怎样把自己的 Java 能力正式发布成 MCP server，而不是只做一个本地 demo。**

在 AI4J 里，这件事至少包含四层决策：

1. 你的能力是 Tool、Resource 还是 Prompt
2. transport 准备用哪种
3. 名称与参数面怎么保持稳定
4. 客户端未来会怎样消费它

服务端这条线的主要源码入口是：

- `mcp/server/McpServerEngine.java`
- `mcp/server/StdioMcpServer.java`
- `mcp/server/SseMcpServer.java`
- `mcp/server/StreamableHttpMcpServer.java`
- `mcp/annotation/*`
- `mcp/util/McpToolAdapter.java`
- `mcp/util/McpResourceAdapter.java`
- `mcp/util/McpPromptAdapter.java`

## 1. 先决定你发布的到底是什么 capability

### Tool

适合：

- 动作
- 调用
- 查询
- 写入

使用注解：

- `@McpService`
- `@McpTool`
- `@McpParameter`

### Resource

适合：

- 只读内容
- 文档
- URI 可定位数据

使用注解：

- `@McpResource`
- `@McpResourceParameter`

### Prompt

适合：

- 模板化提示
- 参数化说明
- 可复用交互片段

使用注解：

- `@McpPrompt`
- `@McpPromptParameter`

如果一开始 capability 分类就错了，后面客户端虽然能调用，但语义会很乱。

## 2. `@McpService` 实际提供了什么

`@McpService` 当前至少允许你声明：

- `name`
- `version`
- `description`
- `port`（HTTP transport 场景）

它的工程意义不是“加个分组标签”，而是：

- 给一组 capability 建立稳定命名空间
- 为后续服务端发布提供元信息

尤其是 Prompt capability，最终名称会拼成：

- `serviceName.promptName`

所以 service 名称一开始就应该按长期兼容来设计。

## 3. Tool 发布链是怎么走的

服务端 Tool 发布当前会经过两条相关链路：

### 协议列举链

`McpServerEngine.handleToolsList(...)`

会调用：

`ToolUtil.getLocalMcpTools()`

再把本地 MCP tool 转成 `McpToolDefinition` 和 `inputSchema`。

### 执行链

`McpServerEngine.handleToolsCall(...)`

最终调用：

`ToolUtil.invoke(toolName, JSON.toJSONString(arguments))`

这意味着 server 发布出去的 tool，并不是“另起一套执行器”，而是复用 AI4J 统一工具执行分发。

## 4. 本地 MCP tool 的命名规则要注意什么

对本地 `@McpService + @McpTool`，`ToolUtil.scanMcpTools()` 会用：

`generateApiFunctionName(serviceName, toolName)`

生成最终 tool id。

这个规则会：

- 把 `serviceName` 和 `toolName` 拼起来
- 非法字符替换成 `_`
- 去掉多余下划线
- 长度截断到 64
- 如果开头不是字母，则补 `tool_`

所以对本地发布来说，AI4J 已经帮你做了 provider 友好的命名规范化。

这也意味着：

- service 名和 tool 名不要依赖大小写或特殊字符来区分
- 超长名字会被截断，设计时要避免语义冲突

## 5. Resource 发布链的真实语义

`McpResourceAdapter` 会：

1. 扫描 `@McpResource`
2. 以 `uriTemplate` 为 key 注册资源
3. 在 `readMcpResource(uri)` 时匹配模板
4. 提取 `{param}` 占位符
5. 把参数转换后注入方法调用

这说明 Resource 发布面最关键的不是“有没有 description”，而是：

- URI 模板是否稳定
- 参数命名是否直观
- MIME type 是否可信

如果 URI 设计随意，客户端后续很难把它当成正式资源面使用。

## 6. Prompt 发布链的真实语义

`McpPromptAdapter` 会：

1. 扫描 `@McpPrompt`
2. 生成 `serviceName.promptName`
3. 根据 `@McpPromptParameter` 生成 arguments schema
4. 支持 `required` 与 `defaultValue`
5. 在 `getMcpPrompt(...)` 时把参数注入方法

这意味着 Prompt 不只是字符串仓库，而是可参数化、可列举的服务端提示词能力。

发布 Prompt 时应该重点考虑：

- 名称是不是稳定
- 参数是不是最小必要
- 默认值会不会掩盖调用错误

## 7. transport 选型就是服务发布模型

发布 MCP server 时，transport 不是后置细节。

### 选 `stdio`

更适合：

- 本地子进程
- 工具型服务
- 开发机集成

### 选 `sse`

更适合：

- 独立服务
- 双端点事件流模型
- 明确的 GET/POST 分离

### 选 `streamable_http`

更适合：

- 正式 HTTP 服务发布
- 需要 session 和流式协商
- 面向更通用客户端

如果 transport 一开始选错，后面客户端接入成本会急剧上升。

## 8. 你发布的是“可接入”，不是“默认开放”

把 Java 能力发布成 MCP server，只代表：

- 它可以通过 MCP 被发现和调用

不代表：

- 所有宿主都该默认信任它
- 所有请求都该默认看见它

在 AI4J 里，消费侧仍然要再经过：

- `McpGateway` 接入
- 请求级 `mcpServices` 白名单

这层分离很重要。发布侧负责规范能力面，消费侧负责控制暴露面。

## 9. 发布前的检查清单

建议至少检查下面几件事：

1. Tool / Resource / Prompt 分类是否正确
2. 名称是否稳定且可长期兼容
3. 参数 schema 是否最小、清晰、可验证
4. 返回内容是否适合该 capability
5. transport 是否匹配部署模型
6. 是否考虑到未来被多个客户端消费

## 10. 一个容易被忽略的工程事实

本地发布链和远端消费链并不完全对称。

本地发布时，AI4J 会对本地 tool 名做 `serviceName_toolName` 规范化；但远端多服务消费时，gateway 层不会再给远端全局工具自动加服务前缀。

这意味着如果你发布给别人用：

- 最好自己保证远端 tool 名在服务边界内足够清晰
- 不要假设消费侧一定会替你做冲突消解

## 11. 这一页的结论

> 在 AI4J 里发布 MCP server，不是“写几个注解就结束”。你实际上是在定义一个长期可消费的协议能力面：它要先分清 Tool / Resource / Prompt，再确定 transport、命名、参数和返回语义。发布解决的是“可接入”，消费侧再决定“是否开放给本次请求”。
