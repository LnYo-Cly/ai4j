---
sidebar_position: 12
---

# Chat / Function Call 与 Tool 注册

这页只讲 `Chat` 这条消息式主线里的工具暴露和执行语义。

如果你想先看更宽的工具体系，再连读：[Tools / Function Calling](/docs/core-sdk/tools/function-calling) 和 [Model Access / Chat](/docs/core-sdk/model-access/chat)。

## 1. 先给一句工程结论

AI4J 里的 `Chat` tool calling 分成两层：

- 本地注册层：`functions`、`mcpServices`
- provider 协议层：`tools`

再往后还有第三层：

- 工具执行权究竟留在 chat service，还是上交给更高层 runtime

真正理解这三层边界，才不会把“工具暴露”和“工具执行”混成一件事。

## 2. 关键源码入口

建议重点看：

- `platform/openai/chat/entity/ChatCompletion.java`
- `platform/openai/chat/OpenAiChatService.java`
- `tool/ToolUtil.java`
- `listener/SseListener.java`

此外，从仓库实现可以看出，`passThroughToolCalls` 和 follow-up tool loop 已经出现在多家 chat provider service 中，不是只给 OpenAI 写的一次性逻辑。

## 3. 注解体系解决的是什么问题

本地 Java function tool 这层，核心注解仍然是：

- `@FunctionCall`
- `@FunctionRequest`
- `@FunctionParameter`

它们解决的是“如何把 Java 代码描述成模型可调用的工具契约”，而不是“模型一定会调用它”。

例如：

```java
@FunctionCall(name = "queryWeather", description = "查询天气")
public class QueryWeatherFunction implements Function<QueryWeatherFunction.Request, String> {

    @Data
    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "城市名", required = true)
        private String location;
    }

    @Override
    public String apply(Request request) {
        return "...";
    }
}
```

模型最终是否选择调用，还取决于：

- 你是否把它暴露进本次请求
- tool description 是否足够清晰
- prompt 是否真的需要它

## 4. `functions` / `mcpServices` 和 `tools` 不是一回事

在 `ChatCompletion` 里：

- `functions(...)` 是本地 function tool 白名单
- `mcpServices(...)` 是 MCP service 白名单
- `tools` 才是最终发给 provider 的协议字段

发送前，chat service 会调用：

- `ToolUtil.getAllTools(chatCompletion.getFunctions(), chatCompletion.getMcpServices())`

再把解析出的结果写回 `chatCompletion.tools`。

这说明 `Chat` 已经把：

- “哪些能力允许本轮暴露”
- “provider 实际收到的工具数组是什么”

分成了两步。

## 5. 白名单语义为什么很关键

下面这段代码：

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("查询北京天气并给出建议"))
        .functions("queryWeather")
        .build();
```

它表达的不是“系统里存在一个叫 `queryWeather` 的工具”，而是：

- 本轮请求明确允许模型看到 `queryWeather`

同理：

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("请读取我的仓库 issue"))
        .mcpServices("github-service")
        .build();
```

表达的是：

- 本轮允许暴露 `github-service` 里的 MCP tools

这层白名单是权限边界，不是语法糖。

## 6. 默认执行模式下，`Chat` 会自动闭环工具调用

在 `OpenAiChatService` 以及多家 chat provider 实现里，默认同步路径都是：

1. 请求模型
2. 如果 `finishReason=tool_calls`，取出 tool call 列表
3. 用 `ToolUtil.invoke(...)` 在本地执行工具
4. 把 assistant tool call message 和 tool output message 追加回 `messages`
5. 再发下一轮请求
6. 直到不再返回 `tool_calls`

所以直接调用 `chatCompletion(...)` 时，你拿到的通常是“本地工具闭环后的最终回答”，而不是“原始 tool planning 中间态”。

## 7. 流式模式下，工具参数先由 `SseListener` 聚合

流式场景更复杂一些。

`SseListener` 会先把这些信息聚合起来：

- 文本 delta
- reasoning 片段
- 碎片化 function arguments
- 最终 `toolCalls`
- `finishReason`

等到本轮流结束后，service 再判断：

- 是否收到了完整的工具调用
- 是否应该继续本地执行下一轮

这就是为什么 AI4J 的 `Chat` 流式不是简单打印器，而是可继续闭环的运行时入口。

## 8. `passThroughToolCalls` 决定工具执行权归谁

这是最重要的分界线。

### `passThroughToolCalls=false`

含义是：

- 模型负责规划工具
- chat service 也负责直接执行工具

适合：

- 快速接入
- demo
- 业务层不关心中间状态

### `passThroughToolCalls=true`

含义是：

- 模型仍然可以规划工具
- 但 chat service 遇到 `tool_calls` 就会把结果返回给上层，不再自动执行

适合：

- agent runtime
- coding runtime
- 需要审批、trace、沙箱、权限控制的系统

所以这个字段控制的不是“要不要 tool calling”，而是“tool loop 在哪一层运行”。

## 9. `parallelToolCalls` 的真实含义

`parallelToolCalls` 首先是 provider 请求语义，表示模型是否可以规划并行工具调用。

它不应该被误解成：

- SDK 一定会帮你做并发调度
- 所有工具都天然适合并行

更稳的理解是：

- 读操作、查询型工具可以考虑开放并行规划
- 写操作、有副作用的工具应当更保守
- 真正的调度、隔离和审批仍应由上层 runtime 决定

## 10. 常见失败路径

### 10.1 模型完全不调用工具

优先检查：

- 工具名是否真的出现在 `functions(...)`
- 工具描述是否足够像“可调用能力”而不是普通注释
- prompt 是否明确要求先调用工具

### 10.2 工具参数结构不稳定

优先检查：

- `@FunctionRequest` 字段名是否与模型自然生成的 JSON 心智一致
- 枚举、单位、必填项是否写清楚

### 10.3 工具输出太长，把后续回答污染了

这不是 chat service 的问题，而是工具设计问题。

更稳的做法是：

- 在工具层先裁剪或摘要
- 不要把巨大的原始 JSON 直接回填给模型

## 11. 什么时候该把工具循环上提

如果你的需求已经变成：

- 工具调用前要审批
- 工具执行要进沙箱
- 工具结果要写 trace
- 工具失败后要重试或改写参数

那就不应该继续依赖 `Chat` service 的默认本地闭环，而应该把 tool loop 上提到 `Agent` 或 `Coding Agent` runtime。

## 12. 这一页的结论

> `Chat` 里的 tool calling 不是单个字段开关，而是“本地白名单注册 -> provider 工具协议 -> 执行权归属”三层组合。默认情况下，AI4J 会在 chat service 内自动闭环工具调用；而当你开启 `passThroughToolCalls`，这条闭环就会被交还给更高层 runtime。这也是为什么 `Chat` 既适合快速接入，也能自然过渡到 agent 级编排。
