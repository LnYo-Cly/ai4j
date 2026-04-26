# First Tool Call

这一步的目标不是讲完全部工具体系，而是先让你知道 AI4J 的工具能力是怎么分层的。

## 1. 先分清三件事

- `Function Call`：本地 Java 工具声明与调用
- `Skill`：可发现、按需读取的方法论资源
- `MCP`：协议化外部能力接入

它们都属于基座能力，但不是同一个概念。

## 2. 最短 `Function Call` 示例

```java
ChatCompletion req = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("查询北京天气并给出建议"))
        .functions("queryWeather")
        .build();

ChatCompletionResponse resp = chatService.chatCompletion(req);
```

这条链路默认会进入工具暴露与执行流程。

## 3. `Skill` 为什么不是工具

`Skill` 自己不会被模型直接调用。

它更像：

- 一份 `SKILL.md`
- 一份按需读取的方法说明

模型通常是先看到 skill 清单，再通过 `read_file` 读取对应的 `SKILL.md`。

## 4. `MCP` 为什么也不是本地工具的子集

`MCP` 不只讲工具，还讲：

- `resource`
- `prompt`
- `transport`
- `gateway`
- `server publish`

所以它在文档结构上和 `Tools` 平级。

## 5. 下一步

继续看：

1. [Core SDK / Tools](/docs/core-sdk/tools/overview)
2. [Core SDK / Skills](/docs/core-sdk/skills/overview)
3. [Core SDK / MCP](/docs/core-sdk/mcp/overview)
