---
sidebar_position: 7
---

# 多模态与 Function Call

这页覆盖两个高频能力：

1. **多模态输入**（文本 + 图片）
2. **函数调用**（Function Tool / MCP Tool）

并给出与 `Chat`、`Responses` 的配合方式。

## 1. Chat 多模态（Vision）

ai4j 在 Chat 链路里统一了多模态消息结构，你可以直接传文本 + 图片地址（或 base64）。

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser(
                "请描述这张图片中的主要内容",
                "https://example.com/demo.jpg"
        ))
        .build();

ChatCompletionResponse response = chatService.chatCompletion(request);
System.out.println(response.getChoices().get(0).getMessage().getContent().getText());
```

底层使用的是 `Content`：

- 纯文本：`Content.ofText(...)`
- 多模态：`Content.ofMultiModals(...)`
- 多模态片段类型：`Content.MultiModal`（`text` / `image_url`）

## 2. 手工构造多模态内容

当你要精细控制多模态片段顺序时：

```java
List<Content.MultiModal> parts = Content.MultiModal.withMultiModal(
        "请比较两张图的差异",
        "https://example.com/a.png",
        "https://example.com/b.png"
);

ChatMessage user = ChatMessage.builder()
        .role("user")
        .content(Content.ofMultiModals(parts))
        .build();
```

## 3. Function Tool 声明方式（注解）

ai4j 的函数工具通过注解扫描生成 JSON Schema：

- `@FunctionCall`：定义工具名与描述
- `@FunctionRequest`：标记请求参数类
- `@FunctionParameter`：标记参数描述/必填

```java
@FunctionCall(name = "queryWeather", description = "查询天气")
public class QueryWeatherFunction implements Function<QueryWeatherFunction.Request, String> {

    @Data
    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "城市名", required = true)
        private String location;

        @FunctionParameter(description = "天气类型: now/daily/hourly")
        private String type;

        @FunctionParameter(description = "查询天数")
        private Integer days;
    }

    @Override
    public String apply(Request request) {
        return "...";
    }
}
```

## 4. Chat 中启用 Function Call

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("查询北京今天天气并给出穿衣建议"))
        .functions("queryWeather")
        .build();

ChatCompletionResponse response = chatService.chatCompletion(request);
```

这里 `functions("queryWeather")` 是关键，它会触发 `ToolUtil` 把工具注入为 `tools`。

## 5. Tool 暴露语义（重要）

`ToolUtil.getAllTools(functionList, mcpServerIds)` 的语义是：

- **你传什么，就只暴露什么**。
- 不会再自动塞入全部本地 MCP 工具。

这可以避免“权限过宽”的工具泄露问题。

## 6. Chat 的函数调用执行模型

在 Chat 服务实现中，SDK 会自动完成以下循环：

1. 模型返回 `tool_calls`
2. SDK 执行工具（`ToolUtil.invoke`）
3. 把工具结果回填到消息
4. 再次请求模型
5. 直到模型返回最终文本

所以你通常不需要自己写“工具循环控制器”。

## 7. Responses 模式下使用工具

`Responses` 这里走的是更底层的 `tools` / `tool_choice` 协议字段，但 AI4J 现在也补上了和 `ChatCompletion.functions(...)` 类似的便捷入口。

也就是说：

- Chat 常写：`.functions("queryWeather")`
- Responses 常写：`.tools(...)`、`.toolChoice(...)`

但这不等于“不能继续用注解工具体系”。

推荐顺序应该是：

1. 本地 Java Tool / Function：优先继续使用注解定义
2. 直接在 `ResponseRequest` 上写 `.functions(...)` / `.mcpServices(...)`
3. SDK 会在发送前通过 `ToolUtil` 自动把注解定义转换成 `tools`
3. 只有在你接第三方原始协议、动态工具或非本地工具时，才手工组装 `Map`

`ResponseRequest` 的 `tools` 字段是 `List<Object>`，所以它既能接注解生成的工具对象，也能接你手工组装的协议对象。

### 7.1 推荐写法：继续使用注解定义工具

如果你的工具本来就是本地 Java 工具，仍然建议：

- 用 `@FunctionCall`
- 用 `@FunctionRequest`
- 用 `@FunctionParameter`

然后直接在 `ResponseRequest` 上声明函数白名单即可。

```java
ResponseRequest request = ResponseRequest.builder()
        .model("doubao-seed-1-8-251228")
        .input("查询北京天气")
        .functions("queryWeather")
        .mcpService("TestService")
        .stream(true)
        .build();
```

这里本质上仍然是“注解注册 -> 自动生成 schema -> 注入 Responses tools 字段”。

### 7.2 手工组装 `Map` 只用于高级场景

下面这种写法只是为了演示 `Responses` 底层协议长什么样，不是推荐你日常都这么写：

```java
Map<String, Object> functionTool = new LinkedHashMap<>();
functionTool.put("type", "function");
functionTool.put("name", "queryWeather");
functionTool.put("description", "查询天气");

ResponseRequest request = ResponseRequest.builder()
        .model("doubao-seed-1-8-251228")
        .input("查询北京天气")
        .tools(Collections.<Object>singletonList(functionTool))
        .stream(true)
        .build();
```

如果你已经用注解定义过本地 Function，也可以继续直接取 schema，而不是手写字段：

```java
Tool.Function function = ToolUtil.getFunctionEntity("queryWeather");
Map<String, Object> functionTool = new LinkedHashMap<>();
functionTool.put("type", "function");
functionTool.put("function", function);
```

这一步只是在复用 schema 定义，不代表 `Responses` 会像 Chat 一样自动帮你执行工具循环。

同时你可以在 `ResponseSseListener` 中观察函数参数增量：

- `getCurrFunctionArguments()`
- `getFunctionArguments()`

## 8. 多工具并行开关

`ChatCompletion` 与 `ResponseRequest` 都支持 `parallel_tool_calls`（对应字段为 `parallelToolCalls`）。

建议：

- 工具有副作用（写库、发消息）时，默认关闭并行。
- 纯查询型工具（天气、检索）可考虑开启。

## 9. 与 MCP 的关系

Function 与 MCP 都会以工具形式进入模型上下文，但来源不同：

- Function：本地 Java 函数，注解扫描注册
- MCP：来自 MCP Server（本地或远程）

在 Agent 里统一通过：

```java
.toolRegistry(functionNames, mcpServerIds)
```

详细见：`MCP / Tool 暴露语义与安全边界`。

## 10. 最佳实践

- 工具描述要写“动作 + 输入约束 + 输出语义”。
- `instructions` 明确“何时必须调用工具”。
- 工具返回值尽量结构化（JSON 字符串优于自由文本）。
- 对高风险工具做白名单限制，不要把全量工具直接暴露给模型。
