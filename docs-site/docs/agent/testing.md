---
title: Agent 测试夹具
description: ai4j-testing 模块提供可复用的 Agent 单测夹具——ScriptedModelClient 脚本化模型回放、DeterministicToolExecutor 确定性工具路由、Recording* 录制系列，让 ReAct/CodeAct 循环在 CI 里零 LLM 依赖跑通。
tags: [how-to]
---

# Agent 测试夹具（ai4j-testing）

测试 Agent 的最大障碍是模型：真实 `AgentModelClient` 要调远端 LLM，单测里既慢又不可复现。`ai4j-testing` 把仓内长期使用的测试私有假实现提升为公开夹具，一行依赖即可在单测里编排完整的 "模型发 tool_call → 工具执行 → 模型给答案" 往返。

```xml
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-testing</artifactId>
    <version>${ai4j.version}</version>
    <scope>test</scope>
</dependency>
```

## ScriptedModelClient：脚本化模型

按队列回放预定的 `AgentModelResult`，同时录制运行时每轮真正发出的 `AgentPrompt`：

```java
ScriptedModelClient model = new ScriptedModelClient()
        .enqueueToolCall("get_weather", "{\"city\":\"sh\"}")
        .enqueueText("上海晴，26℃");

Agent agent = Agents.react()
        .modelClient(model)
        .model("test-model")
        .toolRegistry(registry)
        .toolExecutor(executor)
        .build();

agent.newSession().run("上海天气？");

assertEquals(2, model.getInvocationCount());   // tool_call 一轮 + 终答一轮
```

常用断言面：

| 方法 | 用途 |
|------|------|
| `getPrompts()` | 每轮发给模型的完整 prompt（验证 system/上下文组装） |
| `getLastPrompt()` | 最后一轮 prompt |
| `getInvocationCount()` | 模型被调用的轮数（验证 loop 何时停） |
| `remaining()` | 脚本剩余条数 |
| `failWhenExhausted(true)` | 脚本耗尽后抛异常而非返回空结果——用于证明 loop 停在该停的地方 |

`createStream` 会按同一脚本回放：reasoning delta、text delta、tool-call 事件、`onComplete` 依次回调，流式路径测试与非流式共享一份脚本。

## DeterministicToolExecutor：确定性工具路由

按工具名路由到固定输出或函数，并录制每个到达执行器的调用：

```java
DeterministicToolExecutor tools = new DeterministicToolExecutor()
        .on("get_weather", "{\"temp\":26}")
        .on("echo", call -> call.getArguments())        // 按入参动态返回
        .otherwise(call -> "fallback:" + call.getName()); // 可选兜底

tools.getCallsFor("get_weather");   // 断言工具被调了几次、参数是什么
```

未注册的工具默认抛 `IllegalArgumentException`——工具名漂移会在测试里响亮地失败，而不是静默返回空。

## RecordingToolExecutor / RecordingStreamListener

- `RecordingToolExecutor.returning("固定输出")`：纯录制 + 固定应答；`RecordingToolExecutor.wrapping(realExecutor)`：包在真执行器外面做间谍。
- `RecordingStreamListener`：实现 `AgentModelStreamListener`，录制所有 delta/toolCall/event/complete/error，用于断言流式行为。

## ModelResults / ToolCalls 构造器

测试里高频的 `AgentModelResult`/`AgentToolCall` 形态：

```java
ModelResults.text("答案");
ModelResults.reasoned("推理过程", "答案");
ModelResults.toolCall("get_weather", "{}");           // 单 tool_call
ModelResults.toolCalls(call1, call2);                  // 一步多 tool_call
ModelResults.empty();

ToolCalls.function("get_weather", "{}");               // 自动 callId
ToolCalls.function("call_42", "get_weather", "{}");    // 显式 callId
```

## ReplayModelClient：golden 夹具回放

`ScriptedModelClient` 的脚本写在代码里；`ReplayModelClient` 把脚本外置成**可提交的 JSON 夹具**——把一次真实 provider 对话的每轮响应手工抓下来（如从调试日志/io-capture 抄响应体），落成 `ModelFixture`，回放时零 LLM 依赖：

```json
{
  "name": "deepseek-weather-toolcall",
  "recordedFrom": "deepseek",
  "exchanges": [
    {
      "expectedPromptContains": ["shanghai", "get_weather"],
      "response": {
        "toolCalls": [{"name": "get_weather", "arguments": "{\"city\":\"shanghai\"}", "callId": "call_1"}]
      }
    },
    {
      "expectedPromptContains": ["sunny"],
      "response": {"outputText": "It is sunny in Shanghai."}
    }
  ]
}
```

```java
ReplayModelClient model = ReplayModelClient.load(
        getClass().getResourceAsStream("/fixtures/deepseek-weather-toolcall.json"));
model.failWhenExhausted(true);
// …装配 agent、run、断言与 ScriptedModelClient 完全相同
```

与手写脚本的两点增量：

- `expectedPromptContains` 把**请求侧**也钉进夹具：每轮调用前，断言序列化 `AgentPrompt` 包含全部子串——模型被喂了什么不对，在该轮调用处直接失败，而不是事后翻 `getPrompts()`。
- 夹具是**数据不是代码**：provider 响应字段（reasoningText、toolCalls、token 计数）在 JSON 里直写，团队 review diff 即可审计"录到的真实应答"。

夹具刻意不复用 `agent.replay` 的 io-capture 格式——那是带 runId/时间戳/token 的运行时审计面，golden 夹具需要的是小、稳、逐字可读。夹具里不要放真实 key 或用户数据。

## 一个完整的端到端用例

```java
@Test
public void reactLoop() throws Exception {
    ScriptedModelClient model = new ScriptedModelClient()
            .enqueueToolCall("get_weather", "{\"city\":\"shanghai\"}")
            .enqueueText("上海晴");
    DeterministicToolExecutor tools = new DeterministicToolExecutor()
            .on("get_weather", "{\"temp\":26,\"sky\":\"sunny\"}");

    Agent agent = Agents.react()
            .modelClient(model)
            .model("test-model")
            .toolRegistry(weatherRegistry)
            .toolExecutor(tools)
            .build();

    AgentResult result = agent.newSession().run("上海天气？");
    assertEquals("上海晴", result.getOutputText());
    assertEquals(1, tools.countFor("get_weather"));
}
```

> 源码：`ai4j-testing/src/main/java/io/github/lnyocly/ai4j/testing/`；端到端示例见 `AgentScriptedRunTest`。
