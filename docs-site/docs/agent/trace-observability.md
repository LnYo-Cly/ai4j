---
sidebar_position: 9
---

# Trace 与可观测性（轻量版链路追踪）

这一页聚焦当前 Agent 框架里的轻量级链路追踪能力：

- 是否记录 prompt、模型输入与输出
- 是否记录 tool 参数与结果
- `RUN/STEP/MODEL/TOOL` 各自代表什么
- 如何接入自己的导出器

## 1. Trace 组件图

- `AgentTraceListener`
  - 监听 Agent 事件，生成 Span
- `TraceConfig`
  - 控制记录开关、脱敏、字段裁剪
- `TraceSpan`
  - 单条链路节点数据结构
- `TraceExporter`
  - 导出接口（控制台/内存/你自己的存储）
- `ConsoleTraceExporter`
  - 打印 `TRACE {...}`
- `InMemoryTraceExporter`
  - 测试里断言用

## 2. 启用方式

```java
Agent agent = Agents.codeAct()
        .modelClient(modelClient)
        .model("doubao-seed-1-8-251228")
        .traceConfig(TraceConfig.builder().build())
        .traceExporter(new ConsoleTraceExporter())
        .build();
```

`AgentBuilder` 行为：

- 只要设置 `traceExporter`，会自动把 `AgentTraceListener` 挂到 `AgentEventPublisher`。

## 3. Span 类型与时机

## `RUN`

- 触发：第一个 `STEP_START`
- 结束：`FINAL_OUTPUT` 或 `ERROR`
- 含义：一次完整 agent 调用

## `STEP`

- 触发：每轮 `STEP_START`
- 结束：对应 `STEP_END`
- 含义：一次 runtime 循环

## `MODEL`

- 触发：`MODEL_REQUEST`
- 结束：`MODEL_RESPONSE`
- 含义：一次模型请求/响应

## `TOOL`

- 触发：`TOOL_CALL`
- 结束：`TOOL_RESULT`
- 含义：一次工具执行（包括 CodeAct 的 code 执行）

> 如果某轮没有工具调用，只看到 RUN/STEP/MODEL 没有 TOOL 是正常的。

## 4. 默认记录策略（你关心的“全记录”）

`TraceConfig.builder().build()` 默认就是：

- `recordModelInput = true`
- `recordModelOutput = true`
- `recordToolArgs = true`
- `recordToolOutput = true`
- `maxFieldLength = 0`（不截断）
- `masker = null`（不脱敏）

也就是你想要的“默认全记录、默认不脱敏”。

## 5. 当前会记录哪些关键内容

## 模型输入（MODEL span attributes）

- `model`
- `systemPrompt`
- `instructions`
- `items`（用户输入/历史/memory）
- `tools` / `toolChoice` / `parallelToolCalls`
- `temperature/topP/maxOutputTokens/reasoning`
- `store/stream/user/extraBody`

## 模型输出

- `output`（原始 payload）或 `delta`
- `finalOutput`（挂在 RUN span）

## 工具调用

- `tool`
- `callId`
- `arguments`

## 工具返回

- 普通工具：`output`
- CodeAct 工具：`result/stdout/error`

这已经覆盖了你提到的 LangSmith 核心可观测字段（prompt / tool 参数 / 模型输出）。

## 6. 一段真实日志如何解读

你看到这种日志：

```text
TRACE {"type":"MODEL", ...}
TRACE {"type":"TOOL", ...}
TRACE {"type":"STEP", ...}
TRACE {"type":"RUN", ...}
```

解读顺序建议：

1. 先看 `RUN` 总耗时与状态
2. 再看每个 `STEP`（是否循环过多）
3. 再看 `MODEL`（模型时延和输入是否正确）
4. 最后看 `TOOL`（参数与输出是否异常）

## 7. 自定义 Exporter（接数据库/ES/OTEL 网关）

你只需要实现一个接口：

```java
public class DbTraceExporter implements TraceExporter {
    @Override
    public void export(TraceSpan span) {
        // 持久化 span
    }
}
```

接入：

```java
.traceExporter(new DbTraceExporter())
```

## 8. 何时开启脱敏与截断

线上建议至少做两件事：

1. 通过 `masker` 脱敏密钥/身份信息
2. 通过 `maxFieldLength` 限制超长字段

示例：

```java
TraceConfig config = TraceConfig.builder()
        .maxFieldLength(4000)
        .masker(text -> text == null ? null : text.replaceAll("(?i)api[_-]?key\\s*[:=]\\s*[^,\\s]+", "apiKey=***"))
        .build();
```

## 9. 轻量版和完整版的边界

当前 Trace 定位是“轻量 SDK 级追踪”：

- 优点：接入快、改造小、字段完整
- 缺点：不含 LangSmith 那种 UI 检索/评估/告警平台

如果你后续想升级到“完整版平台”，建议在 `TraceExporter` 外接：

- 存储（OLAP/ES/ClickHouse）
- Trace 查询 API
- 可视化前端
- 质量评估与告警

## 10. 参考测试

- `CodeActRuntimeWithTraceTest`
- `AgentTraceListenerTest`

先跑这两个测试，你可以很快确认链路字段是否满足你的审计需求。

## 11. 和 RAG Trace 的区别

这一页讲的是 Agent runtime 的链路追踪：

- `RUN`
- `STEP`
- `MODEL`
- `TOOL`

它回答的是：

- 这次 agent 调用做了哪些步骤
- 模型和工具分别收到了什么

如果你要看的是知识检索阶段：

- 召回了哪些片段
- 为什么这条排在前面
- rerank 前后顺序怎么变化

那应该看 RAG 侧的：

- `RagResult.trace`
- `retrievedHits`
- `rerankedHits`

两者是不同层级的可观测性，不要混为同一套 trace。

继续阅读：

- [引用、Trace 与前端展示](/docs/ai-basics/rag/citations-trace-and-ui-integration)
