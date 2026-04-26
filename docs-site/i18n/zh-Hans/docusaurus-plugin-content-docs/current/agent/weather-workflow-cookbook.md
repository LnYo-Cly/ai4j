---
sidebar_position: 10
---

# 实战：天气分析双 Agent Workflow（可观测版）

目标：

1. 第一个 Agent 负责天气分析（可调用工具）
2. 第二个 Agent 负责格式化输出（严格 JSON）
3. 控制台打印每个节点的开始/结束状态

这就是你要求的“完整可运行示例”模式。

## 1. 架构图

- 节点 A：`WeatherAnalysisAgent`（ChatModelClient + queryWeather）
- 节点 B：`FormatOutputAgent`（ResponsesModelClient）
- Workflow：`SequentialWorkflow`

## 2. 为什么两个 Agent 分开更好

- 分析 prompt 与格式 prompt 解耦
- 可分别替换模型与参数
- 排障时能明确知道卡在分析还是格式化

## 3. 完整代码（与测试同风格）

```java
Agent weatherAgent = Agents.react()
        .modelClient(new ChatModelClient(aiService.getChatService(PlatformType.DOUBAO)))
        .model("doubao-seed-1-8-251228")
        .systemPrompt("You are a weather analyst. Always call queryWeather before answering.")
        .instructions("Use queryWeather with the user's location, type=now, days=1.")
        .toolRegistry(Arrays.asList("queryWeather"), null)
        .options(AgentOptions.builder().maxSteps(2).build())
        .build();

Agent formatAgent = Agents.react()
        .modelClient(new ResponsesModelClient(aiService.getResponsesService(PlatformType.DOUBAO)))
        .model("doubao-seed-1-8-251228")
        .systemPrompt("You format weather analysis into strict JSON.")
        .instructions("Return JSON with fields: city, summary, advice.")
        .options(AgentOptions.builder().maxSteps(2).build())
        .build();

SequentialWorkflow workflow = new SequentialWorkflow()
        .addNode(new NamedNode("WeatherAnalysis", new RuntimeAgentNode(weatherAgent.newSession())))
        .addNode(new NamedNode("FormatOutput", new RuntimeAgentNode(formatAgent.newSession())));

WorkflowAgent runner = new WorkflowAgent(workflow, weatherAgent.newSession());
AgentResult result = runner.run(AgentRequest.builder()
        .input("Get the current weather in Beijing and provide advice.")
        .build());

System.out.println("FINAL OUTPUT: " + result.getOutputText());
```

## 4. 节点状态输出模板

```java
private static class NamedNode implements AgentNode {
    private final String name;
    private final AgentNode delegate;

    @Override
    public AgentResult execute(WorkflowContext context, AgentRequest request) throws Exception {
        System.out.println("NODE START: " + name);
        try {
            AgentResult result = delegate.execute(context, request);
            System.out.println("NODE END: " + name + " | status=OK");
            return result;
        } catch (Exception e) {
            System.out.println("NODE END: " + name + " | status=ERROR");
            throw e;
        }
    }
}
```

你会得到类似日志：

```text
NODE START: WeatherAnalysis
NODE END: WeatherAnalysis | status=OK
NODE START: FormatOutput
NODE END: FormatOutput | status=OK
FINAL OUTPUT: {...}
```

## 5. 输入输出传递规则

`SequentialWorkflow` 默认把前一节点 `result.outputText` 作为下一节点输入：

- A 输出自然语言天气分析
- B 把该分析转换为 JSON

如果你要传结构化上下文（不仅是文本），建议把字段放进 `WorkflowContext`。

## 6. 常见增强项

1. 在天气节点前加路由节点（是否天气问题）
2. 在格式节点后加审校节点（Schema 校验）
3. 给每个节点接 trace/event 监听
4. 失败时回退到兜底格式化节点

## 7. 对应测试

- `WeatherAgentWorkflowTest`

建议从这个测试复制模板，把 prompt 和字段名替换成你的业务语义。
