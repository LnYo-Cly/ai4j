---
sidebar_position: 3
---

# AgentFlow 总览

`AgentFlow` 解决的不是“直接调用模型”的问题，而是“调用已经在第三方平台上编排并发布好的 Agent / Bot / Workflow 端点”。

这一层和 `PlatformType + IChatService` 是两套不同的接入语义：

- `IChatService`
  - 你在直接调用模型平台
  - 关注的是 `model`、messages、tool call、stream
  - 典型对象是 OpenAI、DeepSeek、豆包、Minimax
- `AgentFlow`
  - 你在调用已经发布好的应用端点
  - 关注的是 `botId`、`workflowId`、`webhookUrl`、业务入参
  - 典型对象是 Dify、Coze、n8n

如果把这两类能力强行混在一起，调用语义会非常混乱，所以 ai4j 把它们拆成了两条并行能力线。

## 1. 这层能力的定位

`AgentFlow` 面向三类场景：

1. 团队已经在 Dify / Coze / n8n 上把流程编排好了，现在 Java 服务只需要稳定调用
2. 你不想在 Java 侧重复实现整套工作流，而是把 Java 作为业务接入层
3. 你需要把“模型调用”和“外部 Agent / Workflow 调用”同时放进一个系统里，但又不希望抽象互相污染

它的核心价值不是替代 `IChatService`，而是补上“已发布端点接入”这一块空白。

## 2. 对外 API 结构

顶层入口仍然从 `AiService` 拿：

```java
Configuration configuration = new Configuration();
configuration.setOkHttpClient(new OkHttpClient());

AiService aiService = new AiService(configuration);

AgentFlow agentFlow = aiService.getAgentFlow(AgentFlowConfig.builder()
        .type(AgentFlowType.DIFY)
        .baseUrl("https://api.dify.ai")
        .apiKey("app-xxx")
        .userId("demo-user")
        .build());
```

然后分成两条能力：

- `agentFlow.chat()`
- `agentFlow.workflow()`

`agentflow` 包内部采用以下设计原则：

- `chat` 只处理聊天型 published endpoint
- `workflow` 只处理工作流型 published endpoint
- provider 的差异收敛在各自 adapter 中，不污染顶层模型

## 3. 核心类职责

### 3.1 `AgentFlowType`

当前内置：

- `DIFY`
- `COZE`
- `N8N`

### 3.2 `AgentFlowConfig`

统一描述第三方已发布端点的接入参数。常用字段包括：

- `baseUrl`
- `webhookUrl`
- `apiKey`
- `botId`
- `workflowId`
- `appId`
- `userId`
- `conversationId`
- `headers`
- `pollIntervalMillis`
- `pollTimeoutMillis`

`AgentFlowConfig` 描述的不是模型参数，而是第三方发布端点所需的接入上下文。

它现在还额外支持：

- `traceListeners`

这组 listener 用来接收 `AgentFlow` 的调用生命周期事件，包括：

- 调用开始
- stream 中间事件
- 调用完成
- 调用失败

这一层只暴露中立事件，不直接依赖 `ai4j-agent` 的 `TraceSpan` 类型。这样 `ai4j` 保持底层模块定位不变，是否桥接到 trace/exporter，由上层自行决定。

### 3.3 `AgentFlow`

一个轻量 facade，只做两件事：

- 持有 `Configuration`
- 根据 `AgentFlowConfig.type` 返回对应的 chat / workflow service

它故意没有塞太多行为，避免把 provider-specific 逻辑堆到顶层。

### 3.4 Request / Response / Event

chat 和 workflow 都有自己独立的：

- request
- response
- stream event
- listener

这样做的原因很直接：

- Dify / Coze 的 chat 有 conversation 语义
- workflow 的结果更接近 outputs / status
- n8n webhook 甚至不适合伪装成 chat

把 chat 和 workflow 混成一个统一 request/response，最终一定会把抽象拉坏。

## 4. 如何选择 `IChatService` 还是 `AgentFlow`

两类入口的区分标准如下：

- 你是在“对模型说话”，用 `IChatService`
- 你是在“调用已经编排好的应用端点”，用 `AgentFlow`

### 更适合 `IChatService` 的场景

- 你自己控制 prompt、messages、model、tool
- 你要做统一的大模型调用层
- 你要自己在 Java 侧实现 agent loop / tool loop / memory

### 更适合 `AgentFlow` 的场景

- 业务流程已经在 Dify / Coze / n8n 中编排完成
- Java 只需要把用户输入和业务参数送进去
- 你要复用第三方平台的 workflow / bot / app 发布结果

## 5. Dify、Coze、n8n 的支持边界

### Dify

支持：

- `chat()` blocking
- `chatStream()` streaming
- `workflow().run()` blocking
- `workflow().runStream()` streaming

### Coze

支持：

- `chat()` blocking
- `chatStream()` streaming
- `workflow().run()` blocking
- `workflow().runStream()` streaming

### n8n

支持：

- `workflow().run()` blocking webhook

当前不支持：

- `chat()`
- `workflow().runStream()`

原因在于 n8n 的自然接入对象就是 published webhook / workflow endpoint。第一阶段优先稳定 webhook 语义，不额外扩展伪 chat 抽象。

## 6. 最小调用示例

### 6.1 Dify Chat

```java
AgentFlow dify = aiService.getAgentFlow(AgentFlowConfig.builder()
        .type(AgentFlowType.DIFY)
        .baseUrl("https://api.dify.ai")
        .apiKey("app-xxx")
        .userId("user-1")
        .build());

AgentFlowChatResponse response = dify.chat().chat(AgentFlowChatRequest.builder()
        .prompt("给我一份东京三日旅行建议")
        .inputs(Collections.<String, Object>singletonMap("locale", "zh-CN"))
        .build());

System.out.println(response.getContent());
```

### 6.2 Coze Workflow

```java
AgentFlow coze = aiService.getAgentFlow(AgentFlowConfig.builder()
        .type(AgentFlowType.COZE)
        .baseUrl("https://api.coze.com")
        .apiKey("pat-xxx")
        .workflowId("workflow-xxx")
        .botId("bot-xxx")
        .build());

AgentFlowWorkflowResponse response = coze.workflow().run(AgentFlowWorkflowRequest.builder()
        .inputs(new HashMap<String, Object>() {{
            put("city", "Paris");
            put("days", 4);
        }})
        .build());

System.out.println(response.getOutputText());
System.out.println(response.getOutputs());
```

### 6.3 n8n Webhook

```java
AgentFlow n8n = aiService.getAgentFlow(AgentFlowConfig.builder()
        .type(AgentFlowType.N8N)
        .webhookUrl("https://n8n.example.com/webhook/travel-plan")
        .build());

AgentFlowWorkflowResponse response = n8n.workflow().run(AgentFlowWorkflowRequest.builder()
        .inputs(Collections.<String, Object>singletonMap("city", "Paris"))
        .build());

System.out.println(response.getOutputText());
```

## 7. Spring Boot 自动装配

`ai4j-spring-boot-starter` 已支持 `AgentFlow` 自动装配。

### 7.1 配置方式

```yaml
ai:
  agentflow:
    enabled: true
    default-name: dify
    profiles:
      dify:
        type: DIFY
        base-url: https://api.dify.ai
        api-key: app-xxx
        user-id: demo-user
      coze:
        type: COZE
        base-url: https://api.coze.com
        api-key: pat-xxx
        bot-id: bot-123
        workflow-id: workflow-123
```

### 7.2 Bean 暴露规则

- 开启 `ai.agentflow.enabled=true` 后，会自动注册 `AgentFlowRegistry`
- 配置了 `ai.agentflow.default-name` 后，会额外注册一个默认 `AgentFlow` bean

因此可以按两种方式使用：

```java
@Resource
private AgentFlow agentFlow;
```

或者：

```java
@Resource
private AgentFlowRegistry agentFlowRegistry;

public void run() throws Exception {
    AgentFlow dify = agentFlowRegistry.get("dify");
    AgentFlow coze = agentFlowRegistry.get("coze");
}
```

`AgentFlowRegistry` 适合多 profile 场景，默认 `AgentFlow` bean 适合单一主端点场景。

## 8. Streaming 怎么看

chat 与 workflow 都有 listener：

- `AgentFlowChatListener`
- `AgentFlowWorkflowListener`

回调结构统一分成：

- `onOpen`
- `onEvent`
- `onError`
- `onComplete`

其中：

- `onEvent` 用来接每个增量事件
- `onComplete` 给最终聚合后的 response
- `raw` 字段保留了原始 provider 事件，方便排障

这层设计的重点不是把所有第三方协议强行揉成完全一致，而是把公共字段稳定下来，同时保留原始细节。

## 9. Trace 与可观测

`AgentFlow` 现在可以把自己的生命周期事件接到现有 trace 体系里，但接法是分层的：

- `ai4j`
  - 只产出 `AgentFlowTraceListener` 事件
- `ai4j-agent`
  - 提供 `AgentFlowTraceBridge`
  - 把这些事件投影成统一的 `TraceSpan`

这样做的原因是：

- `ai4j` 不能反向依赖 `ai4j-agent`
- `AgentFlow` 需要保持为独立能力层，不和 Agent runtime 强耦合
- 但上层又希望复用既有的 `ConsoleTraceExporter / JsonlTraceExporter / OpenTelemetryTraceExporter / LangfuseTraceExporter`

### 9.1 直接接入 trace exporter

```java
TraceExporter exporter = new CompositeTraceExporter(
        new ConsoleTraceExporter(),
        new JsonlTraceExporter("logs/agentflow-trace.jsonl")
);

AgentFlow agentFlow = aiService.getAgentFlow(AgentFlowConfig.builder()
        .type(AgentFlowType.DIFY)
        .baseUrl("https://api.dify.ai")
        .apiKey("app-xxx")
        .traceListeners(Collections.singletonList(
                new AgentFlowTraceBridge(exporter, TraceConfig.builder().build())
        ))
        .build());
```

这里的桥接关系很明确：

- `AgentFlowTraceListener`
  - 是核心层 hook
- `AgentFlowTraceBridge`
  - 是 `ai4j-agent` 里的 trace projection
- `TraceExporter`
  - 决定最终打到哪里

### 9.2 当前会投哪些 trace 语义

桥接后会产出 `AGENT_FLOW` span，统一覆盖：

- `chat()` blocking
- `chatStream()` streaming
- `workflow().run()` blocking
- `workflow().runStream()` streaming
- `n8n workflow webhook` blocking

span attributes 重点包含：

- provider 类型
- operation 类型
- streaming 标识
- 端点配置摘要，例如 `baseUrl / webhookUrl / botId / workflowId`
- 请求输入摘要
- 最终 output / status / taskId / conversationId / workflowRunId`

stream 场景下，增量事件不会拆成很多独立 span，而是追加为 span event：

- `agentflow.chat.event`
- `agentflow.workflow.event`

这样读时间线时可以同时看到：

- 一次外部托管 Agent / Workflow 调用总耗时
- 中间收到过哪些 provider stream 事件
- 最终 token usage 与输出结果

### 9.3 和 Agent runtime trace 的关系

这条链路和 `AgentTraceListener` 是并列关系，不是替代关系：

- `AgentTraceListener`
  - 处理 ai4j 自己的 agent runtime 事件
- `AgentFlowTraceBridge`
  - 处理外部 Dify / Coze / n8n 端点调用

这样最终 exporter 看到的是统一的 `TraceSpan` 模型，但来源仍然清晰可分。

## 10. 这层能力和 ai4j 其他模块的关系

`AgentFlow` 与下面这些能力是并列关系，不是替代关系：

- `IChatService`
- `IResponsesService`
- `RAG`
- `MCP`
- `Tool`
- `Agent`

一个典型系统完全可能同时存在：

- Java 内部 agent 用 `IChatService` / `Responses`
- 业务流程调用外部 Dify / Coze app 用 `AgentFlow`
- 检索增强继续走 RAG / MCP / Tool

这也对应了 ai4j 当前没有将 Dify / Coze / n8n 并入 `PlatformType` 的设计边界。
