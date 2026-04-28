# Function Calling

`Function Calling` 是 AI4J 基座层最核心的一条执行链。它不是简单的“把一个 Java 方法暴露给模型”，而是把本地能力稳定地转成模型可理解的 schema，再把调用结果交回当前 runtime 处理。

如果这页没讲清楚，后面的 `Tool`、`Skill`、`MCP`、`Agent`、`Coding Agent` 都会看起来像混在一起的“模型扩展”。

## 1. 先讲定位

在 AI4J 的架构里，`Function Calling` 只负责三件事：

1. 声明工具是什么
2. 决定当前请求暴露哪些工具
3. 把工具 schema 挂进模型请求，并读取 tool call 返回

它**不负责**：

- 审批
- 权限判定
- 多步状态推进
- checkpoint / resume
- 长任务治理

这些都属于更上层 runtime。

所以你可以把它理解为：

- `Function Calling` 是基座层的“工具协议桥”
- `Agent`/`Coding Agent` 才是上层的“工具治理宿主”

## 2. 源码入口在哪里

这条链的关键入口很集中：

- 注解定义：`ai4j/src/main/java/io/github/lnyocly/ai4j/annotation/FunctionCall.java`
- 请求对象标记：`FunctionRequest.java`
- 参数字段标记：`FunctionParameter.java`
- 工具扫描和 schema 生成：`ai4j/src/main/java/io/github/lnyocly/ai4j/tool/ToolUtil.java`
- `Chat` 请求挂载点：`platform/openai/chat/entity/ChatCompletion.java`
- `Responses` 请求挂载点：`platform/openai/response/entity/ResponseRequest.java`

可以将这套机制概括为：

> AI4J 用注解描述工具，再由 `ToolUtil` 在请求发送前把本地函数白名单解析成统一 tool schema，`Chat` 和 `Responses` 共用这条链。

## 3. 工具是怎么声明的

AI4J 推荐的声明方式不是“手写一段 JSON schema”，而是用 Java 类型和注解把 schema 固定下来。

一个典型工具长这样：

```java
@FunctionCall(name = "queryWeather", description = "查询目标地点的天气预报")
public class QueryWeatherFunction {

    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "目标城市")
        private String city;

        @FunctionParameter(description = "查询天数")
        private Integer days;
    }
}
```

这段定义里，三层信息分别对应：

- `@FunctionCall`：工具 identity
- `@FunctionRequest`：参数对象
- `@FunctionParameter`：字段说明和 required 语义

这样做的好处不是“注解看起来整齐”，而是 **Java 类型系统和模型 schema 绑定在了一起**。

## 4. `ToolUtil` 到底做了什么

`ToolUtil` 是这条链的核心。

它主要做四件事：

1. 扫描 `@FunctionCall` 标记的类
2. 找到内部 `@FunctionRequest` 类
3. 把字段上的 `@FunctionParameter` 转成 schema
4. 根据当前请求的白名单组装真正的 `tools`

你可以从这些方法看得最清楚：

- `scanFunctionTools()`
- `getFunctionEntity(...)`
- `setFunctionParameters(...)`
- `getAllTools(...)`

其中 `setFunctionParameters(...)` 会把字段类型映射成 provider 能接受的参数描述；枚举、字符串、数值、布尔值等都会被翻译成统一的 schema 结构。

这就是为什么 AI4J 的 `Function Calling` 不是“反射随便扫一下类名”，而是明确有一层 schema 生成逻辑。

## 5. 请求是怎么挂工具的

工具不会自动全量暴露。请求侧必须显式指定：

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .messages(memory.toChatMessages())
        .functions("queryWeather")
        .build();
```

或者在 `Responses` 里：

```java
ResponseRequest request = ResponseRequest.builder()
        .model("gpt-4.1")
        .input(memory.toResponsesInput())
        .functions("queryWeather")
        .build();
```

这里最重要的不是 builder 写法，而是**白名单语义**：

- 你传了什么函数名，模型才看得到什么工具
- 没传的工具，即便类在 classpath 里，也不应该默认暴露

这也是 AI4J 现在的安全默认值。

## 6. 一次调用到底怎么跑

把全链路串起来，流程其实很清晰：

1. 你声明工具类
2. 运行时扫描到 `@FunctionCall`
3. 你在请求里用 `functions(...)` 选出本次需要的工具
4. provider service 在发送前调用 `ToolUtil.getAllTools(...)`
5. 本地函数被转换成 provider `tools` payload
6. 模型返回 tool call / function arguments
7. 当前 runtime 决定是自动执行，还是把 tool call 透传到上层

这一步里最容易被误解的是第 7 步。

Core SDK 负责把“工具能被模型调用”这件事打通；但 **是不是自动执行**，是 runtime 语义，不是 `Function Calling` 本身的全部职责。

## 7. 和 `Chat`、`Responses` 的关系

`ChatCompletion` 和 `ResponseRequest` 都保留了两组辅助字段：

- `functions`
- `mcpServices`

它们本身不会直接原样发给 provider，而是先被解析成真正的 `tools` 结构。

区别在于：

- `Chat` 侧更偏消息式 tool call 消费
- `Responses` 侧更偏事件式 tool call / args 消费

但两者底层共享的是同一条基座工具暴露链。

这就是为什么 `Function Calling` 不应该被理解成“OpenAI Chat 的一个特性”，而应该被理解成 **AI4J Core SDK 的统一工具桥**。

## 8. 它和 `MCP` 的关系是什么

这也是最容易混的地方。

本地 `Function Calling` 和 `MCP` 的关系不是替代，而是并列：

- 本地 `Function Calling`：暴露当前 JVM 进程内能力
- `MCP`：接入外部服务能力

到了“发给模型”这一步，`MCP` 工具最终也会被展开成 `Tool.Function` 风格的 schema，所以**运行时表现类似**；但概念上，`MCP` 是协议接入层，不是本地函数声明层。

## 9. 注意事项

### 9.1 以为 classpath 上有工具类，模型就能直接看见

不对。AI4J 当前默认是显式白名单，不传 `functions(...)` 就不该暴露。

### 9.2 把 `Function Calling` 当成完整 agent loop

不对。它只解决 tool schema 和请求挂载，不自动给你补齐审批、重试、状态推进。

### 9.3 参数对象设计得太复杂

模型对深层嵌套参数并不天然友好。工具参数最好保持扁平、清晰、动作明确。

### 9.4 副作用工具和查询工具混在一起暴露

这会让模型看到过大的执行面。写文件、执行命令、远端修改类工具应单独治理。

## 10. 什么时候该停在这一层，什么时候该升级

只要你处于这些场景，停留在 Core SDK 的 `Function Calling` 通常就够了：

- 本地 Java 工具不多
- 目标是打通模型和工具
- 还不需要复杂审批和长任务编排

当你开始需要：

- 工具审批
- 多工具路由治理
- checkpoint / compact / resume
- 工作区级执行隔离

就应该升级到 `ai4j-agent` 或 `ai4j-coding`。

## 11. 设计摘要

AI4J 不是手写 tool JSON，而是用注解把 Java 工具契约固定下来，再由 `ToolUtil` 在请求发送前按白名单解析成统一 schema。  
这层只负责工具桥，不负责 agent 级治理，因此可以被 `Chat`、`Responses`、`Agent`、`Coding Agent` 复用。

## 12. 继续阅读

- [Tools / Annotation-based Tools](/docs/core-sdk/tools/annotation-based-tools)
- [Tools / Tool Execution Model](/docs/core-sdk/tools/tool-execution-model)
- [Skills / Skill vs Tool vs MCP](/docs/core-sdk/skills/skill-vs-tool-vs-mcp)
