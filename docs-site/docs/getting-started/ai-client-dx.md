---
title: AiClient DX 与声明式服务
description: 介绍面向日常开发者的 AiClient、DefaultAiClient、JDK 8 动态代理 @AiService、实例级 @AiTool、ToolOut 双通道输出与 JsonCodec 结构化自愈。
tags: [quickstart, dx, ai-client]
---

# AiClient DX 与声明式服务

在保留底层 `ModelClient` 与多协议细节可控的同时，`2.x` 在 `io.github.lnyocly.ai4j.dx`、`io.github.lnyocly.ai4j.codec` 与 `ai4j-agent` 中引入了一套面向 Java 业务开发者的零样板高层入口（Developer Experience, DX）。

---

## 1. `AiClient` 与流式构建入口

`AiClient`（默认实现 `DefaultAiClient`）将多厂商路由、中间件装配、对话记忆、实例工具循环与结构化输出收敛到一个直观的流式 DSL：

### 1.1 创建 `AiClient`

- `AiClient.fromEnv()`：自动读取环境变量中已配置的厂商预设（基于 `ProviderPresets.availableFromEnv()`）
- `AiClient.of(ModelClient)` / `AiClient.of(ProviderPreset, apiKey)`：围绕单个客户端或预设快速启动
- `AiClient.builder()`：自定义多个 `.preset(preset, apiKey[, baseUrl])`、`.client(providerId / Platform, client)`、`.defaultClient(...)`、`.defaultModel(...)`、`.middlewares(...)`、`.memoryStore(...)`、`.tools(...)`、`.defaultMaxToolRounds(8)` 与 `.defaultMaxSelfHealRetries(2)`

```java
AiClient client = AiClient.builder()
        .preset(ProviderPresets.GEMINI, System.getenv("GEMINI_API_KEY"))
        .preset(ProviderPresets.DEEPSEEK, System.getenv("DEEPSEEK_API_KEY"))
        .defaultModel("gemini:gemini-2.5-flash")
        .tools(new OrderService())
        .build();
```

### 1.2 `AiPromptSpec` 流式调用与多模态附件

调用 `client.prompt()` 或 `client.prompt("provider:model")`（自动通过 `ProviderPresets.parseModelSpec` 解析平台与模型）即可链式构造请求：

- 消息与上下文：`.system(...)`、`.user(...)`（支持参数模板）、`.sessionId(...)`
- 多模态附件 `.attach(Attachment...)`：`Attachment.fromPath(...)`、`fromFile(...)`、`fromBytes(...)`、`fromInputStream(...)`、`fromBase64(...)`、`fromUri(...)`、`fromUrl(...)` 与 `fromProviderFileId(...)`
- 推理与循环预算：`.tools(...)`、`.temperature(...)`、`.topP(...)`、`.maxOutputTokens(...)`、`.maxToolRounds(...)`、`.maxSelfHealRetries(...)`
- 终结动作：
  - `.call()` / `.call(Class<T> | TypeReference<T> | JavaType)` -> 阻塞返回 `AiReply<T>`
  - `.stream()` / `.stream(...)` -> 非阻塞返回 [`AiStream<T>`](/docs/capabilities/models/ai-stream-and-middleware)

---

## 2. `@AiService` 声明式代理（JDK 8 原生动态代理）

`AiServiceProxyFactory.create(Class<T>, AiClient, Object... toolBeans)` 直接使用标准 JDK `Proxy.newProxyInstance` 生成代理实现，**不引入 CGLIB 或字节码增强依赖**，天然兼容 Java 8 及 GraalVM / AOT 编译环境。

### 2.1 支持的注解

| 注解 | 作用域 | 用途 |
| --- | --- | --- |
| `@AiService` | 接口 | 声明 `model`、`system`、`memorySessionParam`、`maxToolRounds` |
| `@SystemMessage` | 方法 / 接口 | 声明方法级系统提示词 |
| `@UserMessage` | 方法 | 声明用户提示词模板，支持 `{{var}}` 与 `{var}` 参数插值 |
| `@MemoryId` | 方法参数 | 绑定会话级 `sessionId` 用于持久化上下文记忆 |

### 2.2 支持的方法返回类型

- `String`：直接提取最终文本回复
- `void`：执行工具或记忆副作用
- 任意 POJO 或泛型类型（基于反射提取泛型签名）：自动执行结构化解析与自愈
- `AiReply<T>`：返回含正文/强类型对象、Token 用量与工具旁路数据的完整回执
- `AiStream<T>`：返回异步事件流，配合 SSE 或后台消费

```java
@AiService(model = "deepseek:deepseek-chat", system = "你是订单查询助手。")
public interface OrderAssistant {

    @UserMessage("帮用户 {{user}} 查询订单 {{orderId}} 的最新状态并给出结构化摘要")
    OrderSummary summarize(@MemoryId String sessionId, String user, String orderId);
}

OrderAssistant assistant = AiServiceProxyFactory.create(OrderAssistant.class, client, orderToolBean);
OrderSummary summary = assistant.summarize("sess-1001", "张三", "ORD-2026");
```

---

## 3. 实例级 `@AiTool`、`ToolOut<D>` 与 `ToolProgress`

与传统静态无状态函数相比，企业 Java 应用中的工具往往是带有 `@Autowired`、事务注解或连接池的 Spring / POJO Bean。`InstanceToolRegistry` 允许直接扫描**对象实例**，在不破坏代理和依赖注入的前提下暴露工具方法：

- `@AiTool(name, description, value)`：标注实例方法
- `@AiToolParam(name, description, value, required)`：标注业务入参并生成 JSON Schema

### 3.1 `ToolOut<D>` 双通道输出（模型上下文与 UI / 审计隔离）

很多工具需要返回给前端丰富卡片（如订单列表表格、Chart 数据、内部 Trace ID），但这些大体积或结构化展示载荷**不应污染大模型上下文**：

- 返回 `ToolOut.of(content, details)`、`ToolOut.text(content)` 或 `ToolOut.error(content, details)`；
- `content`（精简文本/JSON）写回模型的 `tool` 角色消息供模型继续推理；
- `details`（强类型旁路负载 `D`）不会塞进 LLM Prompt，而是进入 `AiReply.getToolDetails()` 或 `AiStreamEvent.getToolDetails()`，直接交给前端渲染或业务落库。

### 3.2 `ToolProgress` 实时工具进度推送

在工具方法签名中声明 `ToolProgress progress` 参数后，运行期由 `InstanceToolRegistry` 自动注入，**不向模型 Schema 暴露该参数**：

```java
@AiTool(name = "query_order", description = "查询订单及物流轨迹")
public ToolOut<OrderDetailDto> queryOrder(
        @AiToolParam(name = "orderId", description = "订单编号") String orderId,
        ToolProgress progress) {
    progress.emit("正在检索订单主记录...", null);
    OrderDetailDto dto = orderRepository.findDetail(orderId);
    progress.emit("已获取物流节点", Collections.singletonMap("count", dto.getTracks().size()));
    return ToolOut.of("订单 " + orderId + " 当前状态：" + dto.getStatus(), dto);
}
```

每次调用 `progress.emit(...)`，若上层以 `stream()` 消费，均会实时派发一条 `AiStreamEventType.TOOL_EXEC_PROGRESS` 事件。

---

## 4. `JsonCodec` 单栈与结构化自愈（Self-Heal）

### 4.1 统一收口到 Jackson（`JsonCodec`）

在 `2.x` 中，AI4J 全仓库移除了 `fastjson2` 与历史冗余反射依赖（并由根工程 `maven-enforcer-plugin` 禁止重新引入），将协议编解码与业务 JSON 处理统一收敛至 `io.github.lnyocly.ai4j.codec.JsonCodec`：

- `JsonCodec.protocolMapper()` / `mapper()`：严格的协议级 Jackson `ObjectMapper`（可复现、零意外类型猜测）。
- `JsonCodec.lenientMapper()`：宽松模式解析器，允许 Java 注释、单引号、未加引号的字段名与尾随逗号。
- `JsonCodec.extractJsonPayload(raw)` / `fromJsonLenient(...)`：自动剥离模型偶尔夹带的 ` ```json ` Markdown 代码围栏与前后解释文本。
- `StructuredOutputs` 也随之升级，支持 `TypeReference<T>` 泛型目标与 `JsonCodec.fromJsonLenient` 解析。

### 4.2 双层结构化自愈机制

针对小参数或非严格模式模型偶发输出缺字段、格式畸形 JSON 的问题，AI4J 同时提供 SDK 级与 Agent 级自愈能力：

1. **SDK 级（`DefaultAiClient`）**：配置 `.defaultMaxSelfHealRetries(2)` 或单次 `.maxSelfHealRetries(...)` 后，若反序列化为目标 POJO / `TypeReference<T>` 失败，会将解析错误自动回喂给模型发起纠偏重试。
2. **Agent 级（`StructuredOutputSelfHealer`）**：位于 `io.github.lnyocly.ai4j.agent.StructuredOutputSelfHealer` 并集成于 `Agent.call(...)`。它通过 Jackson `BeanDescription` 抽取结构化约束，校验 `JsonNode` 树、必填属性与 `@JsonAlias`，支持接入自定义 `Validator<T>`；在校验不通过时将精确错误原因注入有限次重试轮次（`maxHealAttempts`），直至输出合规结构或触发显式异常。

## 下一步建议

- 了解底层 Schema 约束与严格 JSON 模式：阅读 [结构化输出与 Schema](/docs/capabilities/models/structured-output)
- 深入事件流与多模型中间件流水线：阅读 [AiStream 异步流与模型中间件](/docs/capabilities/models/ai-stream-and-middleware)
- 回顾传统的注解工具注册方式：阅读 [注解式 Function Calling](/docs/capabilities/tools/annotation-based-tools)
