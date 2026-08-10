---
sidebar_position: 6
title: 构建并对外发布 MCP Server
description: 讲解 AI4J 如何用 @McpService/@McpTool 注解、adapter 和 McpServerEngine 把 Java 能力发布成 MCP Server，覆盖 Tool/Resource/Prompt 三类能力链与三种服务端 transport 的真实差异。
tags: [how-to]
---

# 构建并对外发布 MCP Server

在 AI4J 里，“发布 MCP Server”不是给类加几个注解就结束，而是一条完整的服务端链路：

1. 用注解声明能力面
2. 把本地 Java 能力投影为 MCP capability
3. 选择服务端 transport
4. 由 `McpServerEngine` 处理协议请求
5. 决定哪些能力真正对外暴露

这页重点讲的不是“能不能跑”，而是当前实现到底怎么发布、哪些地方已经打通、哪些地方还需要你自己补。

## 1. 服务端主链路先看这几个类

如果你要从源码理解发布流程，主入口是：

- 注解层
  - `@McpService`
  - `@McpTool`
  - `@McpResource`
  - `@McpPrompt`
- 适配层
  - `McpToolAdapter`
  - `McpResourceAdapter`
  - `McpPromptAdapter`
- 协议层
  - `McpServerEngine`
- transport/server 层
  - `StdioMcpServer`
  - `SseMcpServer`
  - `StreamableHttpMcpServer`
  - `McpServerFactory`

可以把它理解成：

- 注解负责声明“本地有什么能力”
- adapter 负责把能力整理成 MCP 视图
- server engine 负责把 MCP 请求路由到本地能力
- server 实现负责把协议跑在不同 transport 上

## 2. 能力声明不是只有 Tool

AI4J 服务端明确把 MCP 能力分成 3 类：

- Tool
  动作型能力，最终响应 `tools/list` / `tools/call`
- Resource
  只读内容能力，响应 `resources/list` / `resources/read`
- Prompt
  模板型能力，响应 `prompts/list` / `prompts/get`

声明方式如下：

```java
@McpService(name = "weather-service", description = "Weather MCP service")
public class WeatherMcpService {

    @McpTool(name = "query_weather", description = "Query weather by city")
    public String queryWeather(@McpParameter(name = "city") String city) {
        return "Weather(" + city + ")";
    }

    @McpResource(uri = "weather://city/{city}", name = "city-weather")
    public String weatherResource(@McpResourceParameter(name = "city") String city) {
        return "Resource(" + city + ")";
    }

    @McpPrompt(name = "weather-summary", description = "Generate weather summary")
    public String weatherPrompt(@McpPromptParameter(name = "city") String city) {
        return "Please summarize weather for " + city;
    }
}
```

这里最重要的不是语法，而是 capability 角色别写混：

- 能触发副作用或动作的，用 Tool
- 只读内容，用 Resource
- 模板化提示，用 Prompt

## 3. 真正对外暴露时，三类能力的实现链不一样

### Tool 链路

`tools/list` 和 `tools/call` 走的是：

1. `McpServerEngine.handleToolsList(...)`
2. `ToolUtil.getLocalMcpTools()`
3. `ToolUtil.scanMcpTools()`
4. 把 `@McpTool` 方法转成 MCP `inputSchema`

调用时则是：

1. `McpServerEngine.handleToolsCall(...)`
2. `ToolUtil.invoke(toolName, argumentsJson)`
3. 按当前工具优先级分发到本地 MCP / Function / Gateway 等实现

这意味着当前 MCP Server 暴露出去的 Tool，并不是一个孤立系统，而是直接复用了 AI4J 现有的 `ToolUtil` 执行栈。

### Resource 链路

`resources/list` 和 `resources/read` 走的是：

1. `McpServerEngine.handleResourcesList(...)`
2. `McpResourceAdapter.getAllMcpResources()`
3. `McpResourceAdapter.readMcpResource(uri)`

这里有个必须说明白的实现边界：

- `McpResourceAdapter` 提供了 `scanAndRegisterMcpResources()`
- 但 `McpServerEngine` 本身不会自动触发资源扫描

:::warning Resource/Prompt 不会自动注册
也就是说，如果你没有在服务启动前显式完成资源注册，`resources/list` 可能就是空的。
:::

### Prompt 链路

`prompts/list` 和 `prompts/get` 走的是：

1. `McpServerEngine.handlePromptsList(...)`
2. `McpPromptAdapter.getAllMcpPrompts()`
3. `McpPromptAdapter.getMcpPrompt(name, arguments)`

和 Resource 一样，Prompt 也有同样的当前边界：

- 有扫描注册能力
- 但不是由 server engine 自动初始化

所以当前实现里：

- Tool 暴露链是最完整的
- Resource / Prompt 可用，但你要自己确保启动前完成注册

## 4. 协议引擎到底处理哪些请求

Tool、Resource 和 Prompt 的处理入口保持一致，但协议启动方式取决于 transport profile。

### Streamable HTTP：AUTO 默认与现代分支

`StreamableHttpMcpServer` 和 `McpServerFactory.ServerConfig` 默认使用 `AUTO`。AUTO 在同一个 `/mcp` endpoint 接受现代请求和 initialization-era Streamable HTTP 请求；它通过现代 headers 或 `_meta` 识别现代请求。

被识别为 `2026-07-28` 的现代请求会处理：

- `server/discover`
- `tools/list` / `tools/call`
- `resources/list` / `resources/read`
- `prompts/list` / `prompts/get`

每个请求必须带现代 `_meta` 和 HTTP headers。它不使用 `initialize`、`notifications/initialized` 或协议 session。

### Initialization-era Streamable HTTP 与其他 legacy transport

AUTO server 的同一 `/mcp` endpoint 也保留 initialization-era Streamable HTTP 处理。STDIO、显式 `sse` transport 和具体 legacy Streamable HTTP profile 同样保留 session-era 处理：

- `initialize`
- `notifications/initialized`
- `tools/list` / `tools/call`
- `resources/list` / `resources/read`
- `prompts/list` / `prompts/get`
- `ping`（仅当 server transport 启用）

这说明 AI4J 不是只做“工具执行接口”，但也不能把 legacy handshake 写成所有 MCP transport 的共同前提。

## 5. 三种服务端 transport 的真实差异

### `StdioMcpServer`

- 通过标准输入输出收发 JSON-RPC
- 适合被本地宿主当子进程拉起
- 内部 `McpServerEngine` 固定支持 `2024-11-05`
- `initializationRequired = false`

它更像“嵌入式本地工具进程”。

### `SseMcpServer`

- `GET /sse` 建立事件流
- `POST /message` 发送 MCP 消息
- 支持 `ping`
- `initializationRequired = true`
- 协议版本固定 `2024-11-05`

它更适合兼容旧 SSE 风格客户端，但端点模型比 streamable HTTP 更分裂。

### `StreamableHttpMcpServer`

- 统一主端点 `/mcp`
- 默认 `McpProtocolProfile.AUTO`，在同一 `/mcp` 同时接受现代和 initialization-era Streamable HTTP
- 现代请求使用无状态 `POST /mcp`，可在同一请求上返回 JSON 或 SSE
- 现代请求验证 `MCP-Protocol-Version`、`Mcp-Method`，并在需要时验证 `Mcp-Name` 和 schema 声明的 `Mcp-Param-*`
- 现代请求提供 `server/discover` 和响应缓存 hints
- 不提供 MRTR 或 subscriptions
- 可显式选择 `McpProtocolProfile.MODERN_2026_07_28` 仅允许现代请求，或选择具体 legacy profile 以固定 session、`GET`/`DELETE` 和初始化握手
- deprecated HTTP+SSE 仍是独立的 `sse` transport，不是 AUTO 的第三种分支

如果你打算给外部系统、平台、网关长期消费，这个是当前最应该优先选的 server 形态。

## 6. 用 `McpServerFactory` 启动服务

```java
McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig(
        "weather-server", "1.0.0"
).withPort(8081).withProtocolProfile(
        McpProtocolProfile.AUTO
);

McpServer server = McpServerFactory.createServer("streamable_http", config);
server.start().join();
```

`McpServerFactory` 负责两件事：

- 规范化类型字符串
- 创建对应 server 实例

支持的类型：

- `stdio`
- `sse`
- `streamable_http`
- `http`
  仅兼容别名，最终映射为 `streamable_http`

`http` 只是 `streamable_http` 的 transport 类型别名，并保留 AUTO 的有限 Streamable HTTP compatibility 语义。已知的 session-era peer 可以在 `ServerConfig` 或客户端 `TransportConfig` 中固定具体 legacy profile；deprecated HTTP+SSE 则应使用 `sse`。详见 [Streamable HTTP](/docs/mcp/streamable-http)。

## 7. 服务端安全默认值与 HTTP 层扩展

这一节只对 `sse` / `streamable_http` 两类 HTTP 服务端生效；`stdio` 没有网络层，不涉及认证、绑定和 CORS。

### 7.1 默认绑定回环地址

`ServerConfig` 的默认 host 是回环地址，而不是通配符：

- `ServerConfig.DEFAULT_HOST = "127.0.0.1"`（默认，只对本机可见）
- `ServerConfig.WILDCARD_HOST = "0.0.0.0"`（绑定所有网卡，工厂会记录一条 WARNING）

需要对外暴露时显式指定：

```java
McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig("orders", "1.0.0")
        .withPort(8081)
        .withHost("0.0.0.0"); // 绑定所有网卡；McpServerFactory 会打印安全告警
```

默认端口是 `8080`，可用 `withPort(int)` 覆盖。回环默认值的含义是：一个刚启动的 MCP server 不会自动暴露到外网，除非你显式改 host。

### 7.2 Bearer Token 认证默认开启

`ServerConfig` 默认 `authEnabled = true`。当没有显式配置 `McpAuthProvider` 时，`resolveAuthProvider()` 会在首次调用时懒创建一个 `BearerTokenAuthProvider`，并用 `SecureRandom` 生成随机 token。也就是说，HTTP/SSE 服务端**默认就带 Bearer Token 认证**，不再是裸奔状态。

内置实现 `BearerTokenAuthProvider` 的行为：

- 校验 `Authorization: Bearer <token>` 请求头
- 常量时间比较，抵抗时序侧信道
- 无参构造时生成随机 token（至少 32 字节十六进制）
- `getToken()` 取出 token，供启动时打印或带外下发
- `describe()` 返回脱敏描述（如 `Bearer token: abcd...wxyz`），用于启动日志

```java
BearerTokenAuthProvider auth = new BearerTokenAuthProvider("my-secret-token");
McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig("orders", "1.0.0")
        .withPort(8081)
        .withAuth(auth); // 注入自定义 provider，认证开启
```

认证失败时，服务端统一由 `McpHttpServerSupport.requireAuth(...)` 响应 HTTP 401 并带 `WWW-Authenticate: Bearer`。`/health` 这类探活端点不要求认证，业务端点（`/mcp`）才校验。

### 7.3 自定义认证 SPI：`McpAuthProvider`

要接 OAuth、JWT、API key 或内部鉴权系统，实现 `McpAuthProvider` 这个两方法 SPI：

```java
import com.sun.net.httpserver.HttpExchange;
import io.github.lnyocly.ai4j.mcp.server.McpAuthProvider;

public class JwtAuthProvider implements McpAuthProvider {
    @Override
    public boolean authenticate(HttpExchange exchange) {
        // 读取 Authorization / Cookie / 自定义头；true 放行，false 拒绝
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        return header != null && validateJwt(header.replace("Bearer ", ""));
    }

    @Override
    public String describe() {
        return "JWT (HS256)"; // 仅用于启动日志，不要泄露密钥
    }
}
```

要点：

- `authenticate(HttpExchange)` 对每个入站 HTTP 请求调用一次；返回 `false` 即触发 401。
- `describe()` 仅用于启动日志，不要在其中输出敏感信息。
- 通过 `withAuth(new JwtAuthProvider())` 注入即可，无需改动 server 实现。

如果服务端已位于反向代理或受控网络之后、由上层完成认证，可以用 `withNoAuth()` 显式关闭：

```java
McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig("orders", "1.0.0")
        .withPort(8081)
        .withNoAuth(); // 不安全，仅用于已由上层网关认证的场景
```

:::warning 认证不等于鉴权
`McpAuthProvider` 解决的是“请求能不能进来”（认证）。租户隔离、按工具授权、配额限制等鉴权策略仍需在业务层补齐。
:::

### 7.4 CORS 配置

默认不发送任何 CORS 头（同源策略，最安全）。需要跨域访问时用 `withCorsAllowedOrigin(...)`：

```java
McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig("orders", "1.0.0")
        .withPort(8081)
        .withCorsAllowedOrigin("https://app.example.com"); // 指定可信来源
```

要点：

- 传 `null`（默认）= 同源，不发送 `Access-Control-Allow-Origin`。
- 传 `"*"` 允许任意来源，但对受 token 保护的端点不推荐。
- 浏览器 `Origin` 校验（`McpHttpServerSupport.isAllowedOrigin(...)`）不接受 `"*"` 作为来源验证——通配 CORS 不等于来源校验。
- 工具 schema 声明的 `Mcp-Param-*` 头会在 CORS 预检里按语法白名单追加，不会被任意反射（见下文）。

### 7.5 工具 inputSchema 的 `x-mcp-header` 扩展

现代 Streamable HTTP（`2026-07-28`）允许工具在 `inputSchema` 里声明：把某个原始类型属性镜像成一次 `tools/call` 的 `Mcp-Param-<Name>` HTTP 头。这样代理、网关、缓存层可以不解析 JSON body 就按参数做路由、缓存或鉴权。声明方式是在 `properties` 下某个**原始类型**属性上加 `"x-mcp-header": "<Header-Name>"`：

```json
{
  "type": "object",
  "properties": {
    "region": { "type": "string",  "x-mcp-header": "Region" },
    "count":  { "type": "integer", "x-mcp-header": "Count" }
  }
}
```

调用 `tools/call` 时，客户端会自动从参数推导出 `Mcp-Param-Region` 和 `Mcp-Param-Count` 头。该机制由 `McpHttpHeaderSupport` 实现，规则如下（不满足则该工具 schema 被判为非法，`tools/list` 时被跳过并告警）：

- 只接受 `properties` 下的原始类型（`string` / `integer` / `boolean`）；嵌套对象、数组 `items` 上的注解会被拒绝。
- header 名必须是合法 HTTP token（不能含空格等非法字符）。
- 大小写不敏感的重复 header 名会被拒绝。
- `integer` 必须是精确整数且在 JavaScript safe integer 范围内，编码为十进制字符串。
- `string` 含非 ASCII 可打印字符时，编码为 `=?base64?<b64>?=`。
- 服务端会把 header 值与 JSON-RPC 请求体交叉校验，而不是把 header 当作唯一真相来源。

:::note 注解路径暂不暴露 x-mcp-header
`@McpParameter` 目前只生成 `name/description/required/defaultValue`，不携带 `x-mcp-header`。要用这个扩展，需要在自定义 inputSchema（例如手工构造的 `McpToolDefinition` 或远端工具 schema）上直接声明。AI4J 的现代客户端和服务端会自动识别并处理合法的 `x-mcp-header`。
:::

## 8. 当前实现里哪些能力已经自动化，哪些还没有

### 已经打通的部分

- Tool 注解扫描与参数 schema 投影
- Tool 调用到本地执行链
- 三类 server transport
- AUTO 同端点兼容现代无状态与 initialization-era Streamable HTTP，另保留显式 SSE transport

### 你仍然要自己补的部分

- Resource / Prompt 启动前扫描注册
- 鉴权策略、租户隔离（注：HTTP/SSE 服务端的 Bearer Token 认证已内置默认开启，自定义认证可走 `McpAuthProvider` SPI，详见第 7 节）
- 服务端超时、并发控制、审计
- 对外版本治理和兼容策略

这部分必须讲清楚，否则文档会把“协议能跑”误写成“平台能力已经完备”。

## 9. 对外发布时该怎么做边界设计

至少要先定清楚下面 5 件事：

1. 命名空间
   - `tool/resource/prompt` 名称是否稳定、是否会冲突
2. capability 分类
   - 不要把只读内容和模板都硬塞成 Tool
3. transport 形态
   - 本地宿主优先 `stdio`
   - 服务化发布优先 `streamable_http`
4. 版本兼容
   - 参数新增尽量向后兼容
5. 安全面
   - 发布能力不等于默认允许所有客户端调用

:::note 发布层只负责能力可接入
发布层只负责“能力可接入”，不负责“谁都能随便用”。
:::

## 10. 常见失败点

### `tools/list` 为空

优先检查：

- `@McpService` / `@McpTool` 是否在扫描范围内
- tool 名称是否冲突
- 本地 class 是否能被无参构造实例化

### `resources/list` 或 `prompts/list` 为空

优先检查：

- 是否真的定义了 `@McpResource` / `@McpPrompt`
- 是否在启动前调用了注册扫描逻辑

### HTTP 服务能起，但客户端调不通

优先检查：

- 端点是否真的是 `/mcp`
- 客户端是否把 `streamable_http` 和 `sse` 混用了
- 是否存在代理层路径改写

## 11. 推荐的最小发布姿势

如果你想先把一条链路跑稳，推荐顺序是：

1. 先只发布 Tool
2. 优先用 `streamable_http`
3. AUTO server 分别用现代 `server/discover -> tools/list -> tools/call` 与 legacy `initialize -> tools/list -> tools/call` 自测；HTTP+SSE 再按 `sse` transport 单独验证
4. 再补 Resource / Prompt
5. 最后再接 `McpGateway` 或外部平台

这是因为当前实现里 Tool 链最成熟，最适合作为第一条闭环。
