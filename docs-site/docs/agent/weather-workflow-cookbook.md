---
sidebar_position: 10
---

# 实战：天气分析双 Agent Workflow

这页不是为了给一个“能跑就行”的 demo，而是用一个最小双节点 workflow 把 `ai4j-agent` 的几条关键边界讲清楚：

- `SequentialWorkflow` 默认如何传递节点输出
- `WorkflowAgent` 和 node 自己持有的 `AgentSession` 是什么关系
- 为什么一个节点用 `ChatModelClient`，另一个节点用 `ResponsesModelClient`
- 为什么 cookbook 里还要自己包 `NamedNode` 做开始/结束日志

对应测试源码：

- `ai4j-agent/src/test/java/io/github/lnyocly/agent/WeatherAgentWorkflowTest.java`

## 1. 这个例子到底证明什么

测试里实际做的是一个两阶段串行流程：

1. 天气分析节点调用工具，生成自然语言分析
2. 格式化节点把分析结果收口成严格 JSON

这意味着它不是在证明“一个 Agent 能查天气”，而是在证明：

- 两个 Agent 可以有不同模型客户端和不同职责
- `SequentialWorkflow` 适合“上一节点文本输出 -> 下一节点文本输入”的结构
- Workflow 层可以把推理和格式化拆成显式节点，而不是混在一个 prompt 里

## 2. 为什么这两个节点要拆开

测试代码中的角色分工是：

- `WeatherAnalysisAgent`：`ChatModelClient + queryWeather`
- `FormatOutputAgent`：`ResponsesModelClient + strict JSON formatting`

这样拆的核心好处不是“看起来优雅”，而是运行边界更清晰：

- 分析节点负责工具使用和结论形成
- 格式节点负责输出协议收口

如果把这两步塞进一个 Agent，常见问题会变成：

- 工具调用 prompt 和格式 prompt 互相污染
- 一旦 JSON 格式不稳定，很难判断是工具分析错了，还是格式阶段失控
- 想换模型时不能只替换其中一个阶段

## 3. 这个 workflow 为什么选 `SequentialWorkflow`

测试里用的是：

- `SequentialWorkflow`

而不是 `StateGraphWorkflow`。

原因很直接：

- 这里只有固定的线性两步
- 没有条件分支
- 没有循环
- 没有失败回退节点

因此它是最小正确抽象层。

如果此时直接上 `StateGraphWorkflow`，只是把一个线性问题写成图结构，并没有带来真正收益。

## 4. `SequentialWorkflow` 的默认传递语义决定了这个示例为什么成立

`SequentialWorkflow.executeNodes(...)` 的关键逻辑是：

- 节点执行后拿到 `lastResult`
- 只要 `lastResult.getOutputText()` 非空
- 下一节点收到的新 `AgentRequest.input` 就是这段文本

也就是说，默认链路只传：

- `result.outputText`

它不会自动把前一节点的原始结构化数据、工具结果、附加字段一起传下去。

这正是这个天气例子成立的原因：

- 第一节点输出的就是给第二节点消费的自然语言分析

同时这也是它的边界：

- 如果你想传结构化上下文，而不仅是文本，不能只靠默认接力

## 5. `WorkflowContext` 在这个例子里的真正作用

源码：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/WorkflowContext.java`

`WorkflowContext` 当前只有几块东西：

- `session`
- `state`
- `eventPublisher`

其中最重要的是：

- `state` 是 side channel

也就是说，workflow 的主输入通道仍然是：

- `AgentRequest.input`

而 `WorkflowContext.state` 用来承载：

- 路由结果
- 中间结构化对象
- 节点间共享元数据

所以如果你想让天气节点直接把 `{city, temperature, advice}` 这样的结构化对象传给格式节点，更稳的做法不是让第一节点输出一大段中间文本，而是：

- 把结构化对象显式写进 `WorkflowContext.state`

## 6. `WorkflowAgent` 和节点 session 不是一回事

测试里有一个很容易被忽略的细节：

```java
SequentialWorkflow workflow = new SequentialWorkflow()
        .addNode(new NamedNode("WeatherAnalysis", new RuntimeAgentNode(weatherAgent.newSession())))
        .addNode(new NamedNode("FormatOutput", new RuntimeAgentNode(formatAgent.newSession())));

WorkflowAgent runner = new WorkflowAgent(workflow, weatherAgent.newSession());
```

这里出现了三份 session：

- 天气节点自己的 session
- 格式节点自己的 session
- `WorkflowAgent` 自己持有的一份 session

而 `RuntimeAgentNode.execute(...)` 的实现是：

- 直接调用构造时传入的 `session.run(request)`

它不会改成使用 `WorkflowContext.session`。

这意味着：

- `WorkflowAgent` 持有的 session 不是所有节点共享的统一执行上下文
- 每个 `RuntimeAgentNode` 是否共享 memory，取决于你构造它时传入的 session

这是 workflow 设计里非常关键的边界：节点隔离与共享，不是框架自动帮你推断出来的。

## 7. 为什么 cookbook 里还要包一层 `NamedNode`

测试没有直接把 `RuntimeAgentNode` 扔进 workflow，而是用一个 `NamedNode` 包了一层：

- 打印 `NODE START`
- 打印 `NODE END | status=OK / ERROR`

原因不是装饰，而是当前 workflow 默认没有一套天然节点级日志外壳。

这说明一个工程事实：

- 如果你需要节点级开始/结束、状态和错误可见性，应该自己包 node 或接更明确的事件层

也正因为如此，这个示例本质上是在展示一种最小可观测 workflow 写法，而不是单纯展示“两个节点怎么串起来”。

## 8. 为什么一个节点用 `ChatModelClient`，另一个用 `ResponsesModelClient`

这不是随意混搭，而是在利用两种客户端的不同强项：

- 天气分析节点需要稳定工具调用语义，所以用 `ChatModelClient`
- 格式化节点更像“输出协议收口”，所以用 `ResponsesModelClient`

这也说明一个工程原则：

- workflow 节点之间不必绑定同一种模型协议

只要它们都遵守：

- 输入来自 `AgentRequest`
- 输出回到 `AgentResult.outputText`

就可以在节点级自由选择更合适的模型客户端。

## 9. 这个示例的几个隐含约束

如果你照抄代码但不理解这些约束，很容易把它用坏。

### 9.1 第一节点必须产出“第二节点可消费的文本”

因为默认接力只传 `outputText`，所以第一节点最好输出：

- 稳定、清晰、适合格式化的文本结论

如果第一节点输出冗长、噪声高、夹杂大量工具细节，第二节点的 JSON 收口质量通常会下降。

### 9.2 第二节点不是在读工具结果，而是在读第一节点的结论文本

工具结果并不会通过 workflow 自动结构化穿透到第二节点；第二节点看到的只是上一节点的文本输出。

### 9.3 节点 memory 是否共享取决于 session 复用方式

如果你给多个 `RuntimeAgentNode` 传的是同一份 `AgentSession`，它们就会共享那份 session 的状态；如果分别 `newSession()`，则是隔离的。

当前测试选的是：

- 节点各自独立 session

这更适合“职责分离”的两阶段样板。

## 10. 什么时候这个 cookbook 应该升级

这个双 Agent workflow 很适合作为：

- 线性两阶段样板
- 工具节点 + 格式节点样板
- 最小节点级日志样板

但当你出现以下需求时，就该升级模型了：

- 需要条件路由：上 `StateGraphWorkflow`
- 需要把结构化对象跨节点传递：显式使用 `WorkflowContext.state`
- 需要统一节点 trace：补节点级事件桥或更高层可观测方案
- 需要共享复杂长期状态：重新设计 session / memory 策略，而不是只靠默认文本接力

## 11. 一个更值得复制的思路

这个示例最值得复制的不是某个模型名或 prompt，而是这四个设计动作：

1. 把“工具分析”和“格式收口”拆成两个节点
2. 为不同节点选不同的模型客户端
3. 用 `newSession()` 明确隔离节点状态
4. 用包装 node 补最小观测信息

这四点才是它的工程价值。

## 12. 继续阅读

1. [Workflow StateGraph](/docs/agent/workflow-stategraph)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Memory and State](/docs/agent/memory-and-state)
4. [Model Client Selection](/docs/agent/model-client-selection)
