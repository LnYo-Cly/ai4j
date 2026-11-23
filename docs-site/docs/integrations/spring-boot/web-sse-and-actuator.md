---
title: Web SSE 协议适配与 Boot 2/3/4 Actuator
description: 说明 ai4j-web 的 AiWebStreams、AguiSseCodec、OpenAiSseCodec，以及 ai4j-spring-boot-starter 的 AiWebAutoConfiguration、/actuator/ai4j 与 Boot 2/3/4 单包双栈兼容实践。
tags: [spring-boot, sse, actuator]
---

# Web SSE 协议适配与 Boot 2/3/4 Actuator

为了让开发者在不手写 `SseEmitter` 线程生命周期管理、多行 `data:` 转义、心跳保活与前端流式协议适配的前提下，快速把 [`AiStream`](/docs/capabilities/models/ai-stream-and-middleware) 或底层 `ModelStreamListener` 暴露成标准 HTTP SSE 接口，`2.x` 新增了独立模块 `ai4j-web`，并在 `ai4j-spring-boot-starter` 中增加了开箱即用的 Web SSE 自动装配与 `/actuator/ai4j` 端点，同时保证 **Spring Boot 2 / 3 / 4 单包兼容**。

---

## 1. 引入模块

如果只需核心 Web SSE 适配能力，可直接引入 `ai4j-web`（其中 `spring-webmvc` 为 `provided` + `optional` 作用域，跟随宿主 Spring Boot 版本）：

```xml
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-web</artifactId>
</dependency>
```

在 Spring Boot 应用中直接引入 `ai4j-spring-boot-starter` 即可；`AiWebAutoConfiguration` 会根据类路径是否存在 `AiWebStreams` 与 Spring Web MVC 自动生效。

---

## 2. `ai4j-web` 核心组件

### 2.1 `AiSseEvent` 与 `AiSseSink`

`AiSseEvent` 负责标准 SSE 事件帧（`id`、`event`、`data`、`comment`、`retryMs`）封装：

- `toWireFormat()` 自动按 SSE 规范处理多行 `data:` 与 `comment:` 前缀拆行和转义，杜绝由于模型输出换行导致的协议帧破坏；
- `AiSseSink` 抽象统一输出汇聚口，可接 Spring `SseEmitter` 也可用于自定义网关测试。

### 2.2 双协议流式编解码器：`AguiSseCodec` 与 `OpenAiSseCodec`

| 编解码器 | 适用协议 | 覆盖的事件与输出格式 |
| --- | --- | --- |
| `AguiSseCodec` | **AG-UI 协议**（适合丰富交互式前端、Agent 工具卡片与思考状态） | `RUN_STARTED`、`STEP_STARTED` / `STEP_FINISHED`、`TEXT_MESSAGE_*`、`REASONING_MESSAGE_*`、`TOOL_CALL_*`、`STATE_SNAPSHOT`、`RUN_FINISHED` / `RUN_ERROR` |
| `OpenAiSseCodec` | **OpenAI Chat SSE 兼容协议**（适合现有 OpenAI 前端生态直接复用） | `chat.completion.chunk` JSON 帧及流结束标记 `[DONE]` |

### 2.3 `AiWebStreams` 与 `AiWebStreamOptions`

`AiWebStreams` 提供面向 `SseEmitter` 的非阻塞桥接方法：

- `sseEmitter(...)`：将 [`AiStream`](/docs/capabilities/models/ai-stream-and-middleware) 按指定编码器非阻塞写入 `SseEmitter`
- `toSseListener(...)` / `sseEmitterFromListener(...)`：直接把底层 `ModelStreamListener` 桥接为标准 SSE 流
- **保活与防泄漏**：支持配置 `AiWebStreamOptions`（`timeoutMs`、`heartbeatIntervalMs`、`heartbeatComment`、`includeErrorEvent`）；内置定时心跳调度，并在 `SseEmitter` 触发 `onCompletion`、`onTimeout` 或 `onError` 时以**至多一次（at-most-once）**语义自动取消上游流与关闭定时器，防止客户端断开后大模型或工具仍在后台空跑。

```java
@GetMapping(value = "/chat/agui", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamAgui(@RequestParam String prompt) {
    AiStream<String> stream = aiClient.prompt().user(prompt).stream();
    AiWebStreamOptions options = AiWebStreamOptions.builder()
            .timeoutMs(120_000L)
            .heartbeatIntervalMs(15_000L)
            .build();
    return AiWebStreams.sseEmitter(stream, new AguiSseCodec(), options);
}
```

---

## 3. `AiWebAutoConfiguration` 配置参考

`ai4j-spring-boot-starter` 通过 `AiWebProperties`（前缀 `ai.web`）装配默认 SSE 编解码 Bean 与传输选项，详见 [配置项参考](/docs/integrations/spring-boot/configuration-reference) 与 [自动装配说明](/docs/integrations/spring-boot/auto-configuration)：

| 配置属性 | 默认值 | 说明 |
| --- | --- | --- |
| `ai.web.enabled` | `true` | 是否启用 Web SSE 自动装配 |
| `ai.web.stream.*` | — | 默认流超时时间（`timeout-ms`）、心跳间隔（`heartbeat-interval-ms`）、心跳注释及是否发送错误事件 |
| `ai.web.agui.include-event-name` | — | 控制 `AguiSseCodec` 是否在 SSE 帧中附带 `event:` 字段名 |
| `ai.web.openai.*` | — | 控制 `OpenAiSseCodec` 输出参数 |

---

## 4. `/actuator/ai4j` 运行期诊断端点

当宿主引入 `spring-boot-actuator` 时，`Ai4jActuatorAutoConfiguration` 自动激活 `Ai4jActuatorEndpoint`（`@Endpoint(id = "ai4j")`），通过 `@ReadOperation` 对 `/actuator/ai4j` 暴露**脱敏、无密钥**的系统状态总览：

- `status`：SDK 与自动装配总体就绪状态
- `services`：已注册的厂商平台服务列表（不含任何 API Key 或凭证）
- `extensions`：通过 [Cordis 微内核](/docs/extending/plugins/cordis-microkernel) 已发现与启用的扩展摘要
- `capabilities`：当前可用的能力开关与运行特性投影

---

## 5. Spring Boot 2 / 3 / 4 单包双栈兼容机制

`ai4j-spring-boot-starter` 在不拆成多套 artifact 的前提下，单个 JAR 同时支持 **Spring Boot 2.x（`javax.servlet`）**、**Spring Boot 3.x（`jakarta.servlet`）** 与 **Spring Boot 4.x**：

1. **双元数据清单严格一致**：所有自动配置类同时声明在 `META-INF/spring.factories`（`EnableAutoConfiguration`）与 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中，确保 Boot 2.x 与 Boot 3.x/4.x 均能完整扫描加载。
2. **彻底清除 JSR-250 / Servlet 包路径强依赖**：内部生命周期钩子统一使用 Spring `InitializingBean`（`afterPropertiesSet()`），完全避免 `@PostConstruct`（`javax.annotation` vs `jakarta.annotation` 冲突）或直接引用 `javax.servlet` / `jakarta.servlet`。
3. **可选 Actuator 隔离**：Starter 对 `spring-boot-actuator` 采用 `provided` 作用域并排除全部传递依赖，未启用 Actuator 的 Web 或非 Web 应用无需承担任何多余依赖。
4. **字节码常量池门禁校验**：由 Starter 单测 `BootDualStackCompatibilityTest` 在构建期扫描全部 `.class` 文件常量池，强校验不得包含 `javax/annotation/`、`javax/servlet/` 与 `com/alibaba/fastjson2/`，且逐行比对两份自动配置注册表完全一致。

## 下一步建议

- 了解 `AiStream` 事件流生命周期与中间件体系：阅读 [AiStream 异步流与模型中间件](/docs/capabilities/models/ai-stream-and-middleware)
- 回顾 Starter 自动装配全貌与 Bean 替换方式：阅读 [自动装配说明](/docs/integrations/spring-boot/auto-configuration) 与 [Bean 扩展与替换](/docs/integrations/spring-boot/bean-extension)
