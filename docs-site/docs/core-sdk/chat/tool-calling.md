---
sidebar_position: 12
---

# Chat / Function Call 与 Tool 注册

本页说明两件事：

1. 怎么把 Java 方法注册成模型可调用工具
2. Chat 链路如何自动执行工具并回填结果

## 1. 工具注解体系

ai4j 内置三类注解：

- `@FunctionCall`：定义工具名和描述
- `@FunctionRequest`：定义入参类
- `@FunctionParameter`：定义参数描述/必填

示例（简化版）：

```java
@FunctionCall(name = "queryWeather", description = "查询天气")
public class QueryWeatherFunction implements Function<QueryWeatherFunction.Request, String> {

    @Data
    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "城市名", required = true)
        private String location;

        @FunctionParameter(description = "类型: now/daily/hourly")
        private String type;

        @FunctionParameter(description = "天数")
        private int days;
    }

    @Override
    public String apply(Request request) {
        return "...";
    }
}
```

## 2. 在请求里暴露工具

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("查询北京天气并给出建议"))
        .functions("queryWeather")
        .build();
```

关键点：

- `functions(...)` 是显式白名单。
- 不传就不暴露（避免权限过宽）。

## 3. MCP 工具暴露

除了本地 Function，还可以暴露 MCP 服务工具：

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("请读取我的仓库 issue"))
        .mcpServices("github-service")
        .build();
```

底层走 `ToolUtil.getAllTools(functionList, mcpServerIds)`。

## 4. 工具暴露语义（当前行为）

`ToolUtil.getAllTools(...)` 的语义是：

- 传什么，暴露什么
- 不会自动把全部本地 MCP 工具塞给模型

这点对安全非常关键。

## 5. Chat 工具执行有两种模式

### 5.1 直接 SDK 模式（默认兼容行为）

在 `OpenAiChatService` 等 `IChatService` 实现里，默认仍然是经典自动工具循环：

1. 模型返回 `finish_reason=tool_calls`
2. SDK 执行每个 tool call（`ToolUtil.invoke`）
3. 追加 `assistant(tool_calls)` + `tool(result)` 消息
4. 再次请求模型
5. 直到 `finish_reason=stop`

如果你直接写：

- `AiService.getChatService(...)`
- `chatService.chatCompletion(...)`
- `chatService.chatCompletionStream(...)`

通常就是这条路径。

### 5.2 Agent 透传模式（给 runtime 执行工具）

当 `ChatCompletion.passThroughToolCalls = true` 时，provider 不再自己执行工具，而是把 `tool_calls` 直接返回给上层 runtime。

这是给 `Agent` / `Coding Agent` 准备的语义，因为这些场景里的真正工具执行器往往不是 `ToolUtil`，而是：

- `ToolExecutor`
- `MCP tool executor`
- coding runtime 的内置工具执行器

在 AI4J 当前实现里：

- 直接 SDK 调用默认不需要你手动开这个字段；
- `ChatModelClient` 在有 tools 时会自动开启它；
- 所以 Agent / Coding Agent 一般也不需要手动配置。

## 6. 并行工具调用

`ChatCompletion.parallelToolCalls` 控制并行语义。

建议：

- 写操作工具默认关闭并行
- 查询类工具可开启并行

## 7. 常见问题

### 7.1 模型不触发工具

- `functions(...)` 是否传了正确名称
- 工具描述是否足够明确
- 用户指令是否明确“先调用工具再回答”

### 7.2 工具参数解析异常

- 确认 `@FunctionRequest` 入参字段名与模型返回 JSON 一致
- 参数枚举/类型边界要写清楚

### 7.3 工具结果太长

- 先在工具层做裁剪/摘要
- 避免将超长原始 JSON 全量回填

## 8. 与 Agent 的关系

Agent 的 `toolRegistry(...)` 本质也是在构建这层“工具白名单”，
只是把调用策略从 Chat 服务层升级到 Agent runtime 层。

也就是说：

- 直接 SDK：provider 自动执行工具
- Agent：runtime 执行工具
- Coding Agent：runtime 负责内层 tool-loop，`ai4j-coding` 再加任务级 outer loop

如果业务变复杂（多步推理/路由/循环），建议迁移到 Agent。
