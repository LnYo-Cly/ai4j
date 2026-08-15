---
sidebar_position: 10
title: Replay, Recovery & Audit
description: "ai4j-agent 事件流上的四层生产能力逐个讲透：节点 I/O 捕获与重放（IoCaptureAgentListener/NodeReplayer/NodeIoRecord）、resume-cache 故障恢复（ResumeCache/ResumableModelClient/ResumableToolExecutor，内容寻址、副作用不重放）、持久化 session store、以及 SHA-256 哈希链防篡改审计（HashChainedEventLog）。"
tags: [reference]
---

# Replay, Recovery & Audit

`ai4j-agent` 在 runtime 事件流上叠了四层**可选的生产能力**。它们都是**消费者/装饰器**，不改 runtime——复用 runtime 本来就发的事件（`MODEL_REQUEST`/`MODEL_RESPONSE`/`MODEL_REASONING`/`MODEL_RETRY`/`TOOL_CALL`/`TOOL_RESULT`/`STEP_END`）。需要哪层就接哪层。

| 能力 | 主要类 | 解决什么 |
| --- | --- | --- |
| 节点 I/O 捕获 + 重放 | `IoCaptureAgentListener`、`NodeIoRecord`、`NodeReplayer` | 把每个 model/tool 节点的完整输入输出落盘，可单独重放（真实重调或确定性回放）|
| 故障恢复 / resume | `ResumeCache`、`ResumableModelClient`、`ResumableToolExecutor` | 崩溃后重跑，跳过已完成节点，**不重复已生效的副作用** |
| 持久化 session store | `FileAgentSessionStore`、`JdbcAgentSessionStore` | 跨进程重启存活，长任务可续 |
| 防篡改审计 | `HashChainedEventLog` | 证明记录下来的活动事后没被改动 |

## 0. 先抓住几个关键设计决策

这些决策贯穿四层，先讲清，后面每节就不再重复辩护。

### 0.1 全部是事件流的消费者，不是 runtime 改动

四层都靠 `AgentListener` 或装饰器（`AgentModelClient`/`ToolExecutor`）接入。runtime 不知道你在捕获、在 resume、在审计——它照常发事件、照常调模型/工具。这意味着这些能力可以任意组合，也可以随时摘掉，不影响 agent 主流程。

### 0.2 捕获和 resume 是两套独立机制，别混淆

- **捕获（IoCapture）** 把节点 I/O 记成 `NodeIoRecord`，给**重放和审计**用。
- **resume（ResumeCache）** 是内容寻址缓存，给**跳过重跑**用。

两者都"记录"，但目的不同：捕获要完整可回放，resume 只要够判定"这个节点做过没"。你可以只用其一。

### 0.3 这些层都是 best-effort，不阻断主流程

捕获 listener 的 `onEvent` 把所有异常吞掉（`catch (Exception ignored)`）——一个审计/捕获组件**绝不能让 agent run 挂掉**。resume 的缓存查找失败就退化成真实调用。这是刻意的：可观测/恢复是优化层，不是关键路径。

### 0.4 单机内模型，不是分布式运行记录系统

和 trace 一样，这四层是单 JVM 内的轻量机制。跨实例的强一致运行记录不是它们的目标——要那种语义得在外部系统做。

## 1. 节点 I/O 捕获：`IoCaptureAgentListener`

### 1.1 它做什么

一个 `AgentListener`，把事件流配对成 `NodeIoRecord`：

- **MODEL 节点**：`MODEL_REQUEST`（输入 = `AgentPrompt`）配 `MODEL_RESPONSE`（输出 = 原始响应）。配对键是 `runId|turnId|step`。
- **TOOL 节点**：`TOOL_CALL`（输入 = `AgentToolCall`）配 `TOOL_RESULT`（输出 = `AgentToolResult`）。配对键是 `runId|turnId|step|callId`（无 callId 退化成 toolName，再没有就用 `anon`）。

```java
InMemoryIoCaptureSink sink = new InMemoryIoCaptureSink();
Agent agent = Agents.react().anthropicMessages(key, baseUrl).model("glm-5.1").build();

agent.newSession().runStreamResult(
        AgentRequest.builder().input("...").build(),
        new IoCaptureAgentListener(sink));   // 作为 listener 挂上去

List<NodeIoRecord> modelNodes = sink.records(NodeIoRecord.NodeType.MODEL);
List<NodeIoRecord> toolNodes  = sink.records(NodeIoRecord.NodeType.TOOL);
```

### 1.2 MODEL 节点的输出累积规则（容易看错的地方）

流式响应会发多条 `MODEL_RESPONSE`。listener 的处理是：

- `outputText`：把每条的 `message`（delta 文本）**累加拼接**——这是给人看的人类可读输出。
- `outputs`：只保留**最新一条非空的原始 payload**——覆盖式，不拼接。

这样 `outputText`（可读文本）和 `outputs`（provider 特定的响应对象）分开存，互不污染。`NodeReplayer.replayModelMock(...)` 因此能优先用 raw payload，没有时回退到累加文本。

### 1.3 MODEL 节点的额外富化（TOOL 节点这些字段为空/0）

| 字段 | 来源事件 | 说明 |
| --- | --- | --- |
| `reasoningText` | `MODEL_REASONING` | 模型思维链；流式时多段按换行拼接 |
| `retryCount` | `MODEL_RETRY` | 该 step 的重试次数 |
| `inputTokens` / `outputTokens` | raw response 的 `usage` 块 | best-effort 解析，可能为 `null` |

token 解析是 **provider 无关的多键容错**：输入认 `prompt_tokens`/`promptTokens`/`input`/`input_tokens`，输出认 `completion_tokens`/`completionTokens`/`output`/`output_tokens`。槽位 `-1` 表示"键不存在"，和真实的 `0` 区分开。

### 1.4 节点何时落盘

- MODEL 节点：`STEP_END` 时 flush（一个 step 的模型调用结束）。
- TOOL 节点：`TOOL_RESULT` 时立即 flush。
- 只有 `TOOL_RESULT` 没有配对 `TOOL_CALL`（比如外部直接塞结果）：best-effort 捕获，不带 input。

### 1.5 `NodeIoRecord` 的字段全景

每条记录自描述，不必反序列化 `outputs` 就能做成本核算和审计：

| 字段 | 含义 |
| --- | --- |
| `recordId` | UUID（未指定时随机生成）|
| `runId`/`sessionId`/`turnId`/`step` | 定位回原 run |
| `nodeType` | `MODEL` 或 `TOOL` |
| `nodeId` | 节点定位串，前缀 `model@` 或 `tool@`，后接 stepKey（TOOL 再接 callId）|
| `modelId` | MODEL 节点的模型名（从 `AgentPrompt.getModel()`）|
| `inputs` | MODEL=`AgentPrompt`，TOOL=`AgentToolCall`（原始对象，供 live 重放）|
| `outputText` | MODEL 累加文本 |
| `outputs` | MODEL 最新 raw payload / TOOL 的 `AgentToolResult` |
| `startedAtEpochMs` / `capturedAtEpochMs` | 节点开始/完成；`getDurationMs()` 给单节点延迟 |
| `reasoningText` / `retryCount` / `inputTokens` / `outputTokens` | MODEL 富化（见 1.3）|

### 1.6 两个 sink

| Sink | 用途 |
| --- | --- |
| `InMemoryIoCaptureSink` | 内存里按 `NodeType` 查；测试、临时分析 |
| `JsonlIoCaptureSink` | 一节点一行 JSON，append-only；**持久化的审计/重放产物** |

要跨进程重放或留档，用 `JsonlIoCaptureSink`。

## 2. 节点重放：`NodeReplayer`

重放一个已捕获的 `NodeIoRecord`。MODEL 节点有两种模式，语义截然不同：

| 模式 | 方法 | 是否调模型 | 是否确定性 | 用途 |
| --- | --- | --- | --- | --- |
| live | `replayModelLive(record, modelClient)` | **是**（真实 LLM 调用）| 否 | 重跑节点、A/B 对比模型、用新鲜输出复现流程 |
| mock | `replayModelMock(record)` | 否 | **是** | 精确复现过去某次 turn |

```java
NodeReplayer replayer = new NodeReplayer();
// live：拿捕获的 prompt 重新调真实模型
AgentModelResult fresh = replayer.replayModelLive(modelNodes.get(0), modelClient);
// mock：不调模型，从捕获输出重建结果（优先 raw，回退 outputText）
AgentModelResult same  = replayer.replayModelMock(modelNodes.get(0));
```

要点：

- `replayModelLive` 要求 record 的 `inputs` 是 `AgentPrompt`，否则抛异常——它就是把捕获的 prompt 原样喂给 `modelClient.create(...)`。
- `replayModelMock` 优先用 `outputs`（raw payload），`outputText` 作回退；都没有时 `outputText` 也容许是 `String` 类型的 outputs。
- TOOL 节点的 live 重放是 `replayToolLive(record, reinvoker)`：SDK **不假设工具怎么重新绑定**，调用方传一个从 `AgentToolCall` 映射到 `AgentToolResult` 的 `Function` 来决定重调方式。
- 非 MODEL/TOOL 节点传错类型会抛 `IllegalArgumentException`——是硬校验，不是静默跳过。

## 3. 故障恢复 / resume：`ResumeCache` + 装饰器

### 3.1 核心机制：内容寻址缓存

`ResumeCache` 用**内容寻址**判定"这个节点做过没"：

| 节点类型 | 缓存键 | 怎么算 |
| --- | --- | --- |
| MODEL | 序列化的 prompt | `JSON.toJSONString(prompt)` |
| TOOL | `name\|arguments` | 工具名 + `|` + 参数串 |

同一输入键命中同一个缓存项——这就是"跳过已完成工作"的本质。

### 3.2 两个装饰器：同一实例既捕获又 resume

```java
ResumeCache cache = new ResumeCache();
Agent agent = Agents.react()
        .modelClient(new ResumableModelClient(realModelClient, cache))
        .toolExecutor(new ResumableToolExecutor(realToolExecutor, cache))
        .build();
agent.newSession().run(request);   // 第一次：每节点 miss → 真实调用 + 记录
agent.newSession().run(request);   // 同输入再跑：全 hit → 零真实调用
```

- `ResumableModelClient.create`：lookup 命中 → 返回缓存的 `AgentModelResult`，**不调 delegate**；miss → delegate 调用 + 记录。
- `ResumableToolExecutor.execute`：lookup 命中 → 返回缓存的输出，**不调 delegate**；miss → delegate 调用 + 记录。

### 3.3 副作用不重放——这是 resume 的安全核心

`ResumableToolExecutor` 命中缓存时**不执行真实工具**。这至关重要：崩溃后重跑，已经生效的副作用（文件写入、API 调用、扣费）不能再来一遍。resume 的语义不是"重做"，是"接着上次做完的地方继续"。

### 3.4 流式的陷阱（容易踩）

`createStream` 也查缓存，命中时**直接返回缓存结果，不会重新给 listener 发 delta**。也就是说：

- resume 主要面向非流式 `create` 路径。
- 流式 resume 时，下游的流式 listener 不会收到逐 token 重放——拿到的是终态结果。

如果你的逻辑依赖流式 delta 序列，别指望 resume 重放它。

### 3.5 跨进程持久化

```java
cache.saveToJson(Path.of("/var/ai4j/resume.json"));     // 第一次 run 后存盘
ResumeCache loaded = ResumeCache.loadFromJson(Path.of("/var/ai4j/resume.json"));  // 重启后加载
```

`saveToJson` 把 `modelResults` + `toolOutputs` 写成一个 JSON 文件；`loadFromJson` 文件不存在时返回空缓存（不抛）。跨进程 resume = run1 捕获+存盘 → 重启 → run2 加载+resume。

### 3.6 计数与崩溃模拟（测试用）

- `getModelHits()/getModelMisses()/getToolHits()/getToolMisses()`：断言多少节点是重放 vs 真实执行。
- `removeLastModelEntry()`：删掉最近一条 model 记录，**模拟"最后一步前崩溃"**——配合 resume 测"崩溃后重跑能从倒数第二步继续"。

## 4. 持久化 session store

`AgentSessionStore` 后端中立，给 session 做 checkpoint 以跨重启存活：

- **`FileAgentSessionStore(dir)`**：一个快照一个 JSON 文件，零依赖（只用文件系统）。轻量默认。
- **`JdbcAgentSessionStore(config)`**：一个快照一行，只用 JDK `javax.sql`（自带驱动）。适合共享/多实例生产库。

```java
AgentSessionStore store = new FileAgentSessionStore(Path.of("/var/ai4j/sessions"));
store.save(session.snapshot());
AgentSessionSnapshot restored = store.load(sessionId);   // 重启后
session.restore(restored);
```

注意：session store 存的是 **session 快照**（memory + 事件日志 + compact 结果），和 §3 的 `ResumeCache`（节点级内容寻址缓存）是两套东西。session store 让 session 跨重启存活；ResumeCache 让单次 run 跳过已完成节点。两者可叠加。

## 5. 防篡改审计：`HashChainedEventLog`

### 5.1 哈希链怎么连

`InMemoryAgentSessionEventLog` 的 drop-in 替代（实现同样的 `AgentSessionEventLog` + `AgentListener` 接口）。每条事件封进一个 Link：

```
hash = sha256( prevHash || "|" || canonical(event) )
```

- `canonical(event)` = `JSON.toJSONString(event)`（fastjson2 序列化的规范形）。
- `prevHash` = 上一条 Link 的 hash；第一条用 genesis = 64 个 `0`。
- hash 输出小写十六进制。

每条 Link 存三样：`event`、`prevHash`、`hash`。链式相依：改任何一条事件的 payload，它自己的 hash 变，之后所有 Link 的 hash 全跟着变。

### 5.2 `verifyChain()` 抓得到什么

从 genesis 重算整条链，逐 Link 比对，**报告第一个断掉的 index**。它同时校验两件事：

1. 重算的 `hash` 与存储的 `hash` 一致；
2. 存储的 `prevHash` 与期望的上一条 hash 一致。

因此这些事后改动**都被检测**：

| 篡改 | 检测点 |
| --- | --- |
| 改某条事件 payload（不重封） | 该 Link hash 不匹配 |
| 删掉中间一条 | 后续 Link 的 prevHash 断链 |
| 调换两条顺序 | 涉及 Link 的 hash/prevHash 都不对 |

```java
HashChainedEventLog auditLog = new HashChainedEventLog();
agent.newSession().runStreamResult(request, auditLog);   // 作为 listener，每事件封进链

ChainVerification v = auditLog.verifyChain();
if (!v.isValid()) {
    // v.getFirstBrokenIndex() 指向第一个断裂的 Link
}
```

### 5.3 `restore` 会重新封链

`restore(events)` 不是直接信存储的 hash——它**清空链、逐条重新计算 hash 重建**。所以从可信源恢复的事件会被重新封口。`sequence` 计数器按恢复事件的最大值对齐。

### 5.4 测试用的 `tamperEvent`

`tamperEvent(index, replacement)` 替换某 Link 的 payload **但不重封 hash**——专门模拟"事后编辑"。下一次 `verifyChain()` 必须把这个 index 报成断裂。这是给审计测试用的后门，生产代码别调。

### 5.5 它证明什么、不证明什么

哈希链证明的是**完整性与事后不可篡改**：记录下来的事件序列没被改。它**不**证明事件本身真实发生过（那要可信采集），也**不**加密——任何人能读 JSON 就能读事件。要机密性得在外层加密，哈希链只管完整性。

## 6. 边界与容易误判的语义

- **捕获和 resume 是两套记录**：捕获要完整可回放（`NodeIoRecord`），resume 只要够查重（内容键 → 结果）。别指望用 `ResumeCache` 做 replay，也别指望用 `IoCaptureAgentListener` 做 resume。
- **MODEL 缓存键是整个序列化 prompt**：prompt 里任何字段变了（包括 temperature、tools 列表顺序影响序列化）就 miss。这是内容寻址的代价——精确但敏感。
- **流式 resume 不重放 delta**：见 §3.4。
- **审计链不加密**：见 §5.5。
- **四层都单机内**：跨实例强一致不是它们的设计目标。
- **token 解析 best-effort**：`NodeIoRecord` 的 token 字段可能 `null`，别在审计逻辑里假设它一定有值。

## 7. 四层怎么组合

| 场景 | 组合 |
| --- | --- |
| 本地调试复现某次 turn | 捕获（InMemory）+ `replayModelMock` |
| A/B 对比两个模型在同一节点的输出 | 捕获 + `replayModelLive`（换 modelClient）|
| 崩溃后无副作用重跑 | resume（`ResumableModelClient` + `ResumableToolExecutor`），跨进程再加 `saveToJson` |
| 长任务跨重启续跑 | session store（File/Jdbc）|
| 合规审计"记录没被改" | `HashChainedEventLog` + `verifyChain` |
| 生产全配 | 捕获（Jsonl 落盘）+ resume + session store + 审计链 |

## 8. 继续阅读

- **Tracing**（span、OTel/Langfuse 导出）：[Trace & Observability](/docs/agent/observability/trace-observability)
- **Session 生命周期**（snapshot/restore）：[Session Runtime](/docs/agent/session-runtime)
- **重放/恢复的工具在哪儿跑**：[Sandbox SPI](/docs/agent/governance/sandbox-spi)
