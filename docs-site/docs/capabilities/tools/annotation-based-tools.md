---
title: "Annotation-based Tools"
description: "深入 @FunctionCall、@FunctionRequest、@FunctionParameter 三注解如何把 Java 类型绑定为 provider tool schema，详解 ToolUtil 生成链、类型映射规则与真实约束。"
tags: [concept]
---

# Annotation-based Tools

AI4J 推荐注解式工具，不是因为“代码更短”，而是因为它把 Java 类型、字段说明和 provider tool schema 绑定到了同一条生成链上。

如果这一页只停留在“三个注解怎么写”，会漏掉两个真正重要的点：

- `ToolUtil` 到底如何从注解生成 schema
- 这套方案当前有哪些真实限制

## 1. 一个标准注解工具长什么样

典型模式如下：

```java
@FunctionCall(name = "query_weather", description = "Query weather by city")
public class QueryWeatherFunction implements Function<QueryWeatherFunction.Request, String> {

    @Override
    public String apply(Request request) {
        ...
    }

    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "City name")
        private String city;

        @FunctionParameter(description = "Forecast days", required = false)
        private Integer days;
    }
}
```

从 `ToolUtil` 的调用方式看，这个模式里真正需要满足的是：

- 类上有 `@FunctionCall`
- 有一个 `@FunctionRequest` 标记的参数类
- 工具类上存在 `apply(RequestType)` 方法

实现 `java.util.function.Function` 是常见写法，但不是 `ToolUtil` 强制要求的接口契约；真正被反射调用的是 `apply(...)` 方法本身。

## 2. 三个注解分别承担什么角色

### `@FunctionCall`

定义工具 identity：

- `name`
- `description`

它标在类上，决定工具最终以什么名字暴露给模型。

### `@FunctionRequest`

标记“哪个类是参数载体”。

当前 `ToolUtil` 的实现是从工具类的 `declaredClasses` 里找这个注解，因此最稳妥的写法是：

- 使用工具类内部静态类作为 request 对象

### `@FunctionParameter`

定义字段级 schema 信息：

- `description`
- `required`

当前没有 `name` 别名字段，因此工具参数名默认直接来自 Java 字段名。

## 3. 这套机制为什么比手写 schema 更稳

手写 tool schema 的典型问题是：

- Java 字段改了，JSON schema 忘了同步
- 同一工具在 Chat、Responses、Agent 三处维护三份描述
- 字段说明和执行逻辑分离

注解式工具的价值就在于：

- schema 源头贴着 Java 类型
- 字段说明贴着字段本身
- 一套定义可以被多个 runtime 复用

本质上，AI4J 是在用 Java 类型系统生成模型工具契约。

## 4. `ToolUtil` 具体怎么生成 schema

核心链路在这些方法里：

- `scanFunctionTools()`
- `getFunctionEntity(...)`
- `setFunctionParameters(...)`
- `createPropertyFromType(...)`

大致流程是：

1. 扫描所有 `@FunctionCall` 类
2. 记录 `toolName -> toolClass`
3. 找到内部 `@FunctionRequest` 类并缓存
4. 遍历 request 类字段
5. 只把标了 `@FunctionParameter` 的字段纳入 schema
6. 按字段类型映射成 `Tool.Function.Property`

这说明两个重要事实：

- 不是 request 类里所有字段都会自动暴露
- 字段 schema 生成依赖字段类型映射，而不是任意深度对象自动展开

## 5. 当前类型映射规则是什么

`createPropertyFromType(...)` 当前大致映射为：

- `String` -> `string`
- `int/Integer/long/Long/...` -> `integer`
- `float/double/...` -> `number`
- `boolean/Boolean` -> `boolean`
- `enum` -> `string + enumValues`
- `array/Collection` -> `array`
- `Map` -> `object`
- 其他复杂对象 -> `object`

这带来几个很实际的限制。

### 复杂对象不会自动展开深层属性

如果字段类型是一个普通 POJO，当前 schema 只会把它当成：

- `type: object`

不会继续反射出它的内部字段。

### 泛型集合元素类型会退化

对 `Collection` 来说，Java 泛型擦除后拿不到精确元素类型，所以很多情况下最终只会得到：

- `array` of `object`

### 参数别名不可配

`FunctionParameter` 只有 `description` 和 `required`，没有参数重命名能力，因此字段命名需要自己保持简洁稳定。

这也是为什么 AI4J 工具参数更适合扁平结构，而不是深嵌套 DTO。

## 6. 真实约束和容易踩坑的地方

### 最好只定义一个 `@FunctionRequest`

从 `ToolUtil` 当前实现看：

- `scanFunctionTools()` 只缓存第一个找到的 request 类
- `setFunctionParameters(...)` 却会遍历所有标记了 `@FunctionRequest` 的内部类

这会让“多个 request 类”变成模糊状态。

实际工程里，建议每个工具只保留一个 request 类。

### `FunctionRequest.description` 当前基本不进入最终 schema

这个注解虽然有 `description` 字段，但 `ToolUtil` 目前主要消费的是字段上的 `@FunctionParameter`，而不是 request 类整体描述。

所以真正应该认真写的是字段描述，而不是指望 request 类描述直接进 provider schema。

### 字段没加 `@FunctionParameter` 就不会暴露

这不是 bug，而是当前设计：只有显式标注字段才进入模型工具契约。

### 工具名稳定性要自己治理

:::warning
`@FunctionCall(name = "...")` 是最终暴露给模型和上层 runtime 的名字。改名就会影响：

- 请求里的 `functions(...)` 白名单
- 模型历史记忆
- 上层工具路由

不要频繁修改。
:::

## 7. 什么时候最适合用注解工具

这套方案最适合：

- 能力就在当前 JVM 内
- 输入参数相对稳定
- 需要被 Chat、Responses、Agent 复用
- 想长期维护成正式契约

如果问题已经变成：

- 远端服务接入
- 多 transport 生命周期
- 多服务网关治理

那就不该继续往本地注解工具里塞逻辑，而应该进入 MCP。

## 8. Built-in tools 和注解工具是什么关系

仓库里像 `ReadFileFunction`、`BashFunction`、`WriteFileFunction` 这些 built-in coding tools，表面上也用了同一套注解：

- `@FunctionCall(name = "read_file", ...)`
- `@FunctionRequest`
- `@FunctionParameter`

但它们执行时不会走普通业务函数路径，而是被 `BuiltInToolExecutor` 先拦截。

这说明注解层和执行层是分开的：

- 注解层负责暴露契约
- 执行层可以是普通 `apply(...)`
- 也可以是 built-in 专用执行器

## 9. 本地 MCP 工具也有一套对应注解

前面三节讲的都是 `@FunctionCall` 这套**本地 Function 工具**注解。如果工具要走 MCP 协议（transport、服务化、被 `McpGateway` 统一治理），AI4J 另有一组**方法级**注解，无需写 `Function<Request,String>`，直接标在类和方法上即可：

- `@McpService`（标在类上）—— 定义服务 identity：`name` / `version` / `description` / `transport`（`stdio`/`sse`/`streamable_http`）/ `port` / `autoStart`
- `@McpTool`（标在方法上）—— 定义工具：`name`（默认方法名）/ `description` / `inputSchema`（不填则按方法参数自动生成）
- `@McpParameter`（标在参数上）—— 定义参数：`name`（默认参数名）/ `description` / `required` / `defaultValue`

一个最小例子：

```java
@McpService(name = "weather-service", description = "Weather MCP service")
public class WeatherMcpService {

    @McpTool(name = "query_weather", description = "Query weather by city")
    public String queryWeather(@McpParameter(name = "city", description = "City name") String city,
                               @McpParameter(name = "days", required = false, defaultValue = "3") int days) {
        return "Weather(" + city + ", " + days + "d)";
    }
}
```

它和 Function 注解的关键区别：

| | `@FunctionCall` 工具 | `@McpService`/`@McpTool` 工具 |
|---|---|---|
| 标注粒度 | 类 + 内部 request 类 + 字段 | 类 + 方法 + 方法参数 |
| 参数载体 | `@FunctionRequest` 静态内部类 | 直接是方法形参 |
| 执行链 | 反射调 `apply(Request)` | 先 parse 成 `Map` 再按参数逐个类型转换 |
| 暴露名 | `@FunctionCall(name)` 原样 | 由 `generateApiFunctionName(service, tool)` 生成（仅保留字母/数字/下划线/连字符，最长 64，必要时加 `tool_` 前缀） |
| 扫描入口 | `ToolUtil.scanFunctionTools()` | `ToolUtil.scanMcpTools()` |

两套注解最终都会被 `ToolUtil` 扫描、缓存，并投影成统一的 `Tool.Function` 视图进入请求白名单（见 [Tool Execution Model](/docs/capabilities/tools/tool-execution-model)）。完整的 transport 启停、网关治理、远端/本地投影语义属于 MCP 协议层，见 [构建 MCP 服务](/docs/capabilities/mcp/build-your-mcp-server)。

## 10. 最稳的设计建议

基于当前实现，最稳妥的工具设计通常是：

- 工具名短且稳定
- request 只保留一个内部静态类
- 参数尽量扁平
- 复杂对象不要深嵌套
- 字段名直接作为模型参数名来设计
- 每个字段都写清楚 `description`

这会显著降低 schema 漂移和模型误调用概率。

## 11. 这页最该记住的结论

AI4J 的注解式工具，不是“给类贴几个标签”，而是一条从 Java 类型到 provider tool schema 的生成链。

它的优势在于契约统一，限制在于：

- request 结构最好保持简单
- 深层类型不会自动展开
- 多 request 类会带来歧义

理解这些真实边界，比只会写注解更重要。
