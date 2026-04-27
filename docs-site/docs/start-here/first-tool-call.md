# First Tool Call

这一步的目标不是讲完全部工具体系，而是先让你知道：

- AI4J 里的“第一次工具调用”到底在讲什么
- `Function Call`、`Skill`、`MCP` 为什么必须分开理解
- 你下一步应该进哪条专题树

## 1. 这页先围绕哪种 Tool 说

这一页先说的是最常见的第一条工具主线：

- 本地 Java `Function Call` / `Tool`

也就是：

- tool schema 在本地应用内声明
- tool execution 在本地应用内完成
- 模型通过工具调用语义决定要不要用它

这通常是大多数 Java 用户理解“第一次 Tool Call”的最好入口。

## 2. 先分清三件事

- `Function Call`：本地 Java 工具声明与调用
- `Skill`：可发现、按需读取的方法论资源
- `MCP`：协议化外部能力接入

它们都属于基座能力，但不是同一个概念。

## 3. 第一次 Tool Call 到底证明什么

如果第一条本地工具调用跑通了，你真正验证的是：

- 模型已经看见某个可调用工具
- 模型能够决定调用它
- 工具调用参数能够进入执行层
- 执行结果能够重新回到模型主线

所以“第一次 Tool Call”不只是多了一个函数名，而是模型调用链第一次进入了“可调用能力”阶段。

## 4. 最短 `Function Call` 示例

```java
ChatCompletion req = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("查询北京天气并给出建议"))
        .functions("queryWeather")
        .build();

ChatCompletionResponse resp = chatService.chatCompletion(req);
```

这条链路默认会进入工具暴露与执行流程。

## 5. `Skill` 为什么不是工具

`Skill` 自己不会被模型直接调用。

它更像：

- 一份 `SKILL.md`
- 一份按需读取的方法说明

模型通常是先看到 skill 清单，再通过 `read_file` 读取对应的 `SKILL.md`。

也就是说，`Skill` 更像：

- 说明资产
- 模板资产
- 方法论资产

而不是结构化可执行能力本身。

## 6. `MCP` 为什么也不是本地工具的子集

`MCP` 不只讲工具，还讲：

- `resource`
- `prompt`
- `transport`
- `gateway`
- `server publish`

所以它在文档结构上和 `Tools` 平级。

更准确地说：

- `Function Call` 先解决“本地工具如何暴露和执行”
- `MCP` 再解决“外部能力如何按协议接进来”

## 7. 下一步怎么读

如果你下一步要继续看本地工具主线，继续看：

1. [Core SDK / Tools](/docs/core-sdk/tools/overview)
2. [Core SDK / Function Calling](/docs/core-sdk/tools/function-calling)
3. [Core SDK / Tool Execution Model](/docs/core-sdk/tools/tool-execution-model)

如果你下一步要把概念边界彻底分清，继续看：

1. [Core SDK / Skills](/docs/core-sdk/skills/overview)
2. [Core SDK / Skills / Skill vs Tool vs MCP](/docs/core-sdk/skills/skill-vs-tool-vs-mcp)
3. [Core SDK / MCP](/docs/core-sdk/mcp/overview)

如果这里的 first tool call 没有触发，优先回看：

- [Troubleshooting](/docs/start-here/troubleshooting)
