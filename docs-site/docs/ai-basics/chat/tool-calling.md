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

## 5. Chat 自动工具循环

在 `OpenAiChatService` 等实现里，工具循环是自动的：

1. 模型返回 `finish_reason=tool_calls`
2. SDK 执行每个 tool call（`ToolUtil.invoke`）
3. 追加 `assistant(tool_calls)` + `tool(result)` 消息
4. 再次请求模型
5. 直到 `finish_reason=stop`

你不需要手写循环控制器。

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

如果业务变复杂（多步推理/路由/循环），建议迁移到 Agent。
