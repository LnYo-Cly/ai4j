---
title: 拦截器扩展
description: 拦截器扩展声明 ExtensionCapability.INTERCEPTOR，通过 context.interceptors() 注册工具调用拦截器（allow/block/modify/routeTo）、Prompt 拦截器（allow/block/modify）与模型请求拦截器（标量字段改写），让插件在 agent 执行的关键节点介入控制流，而不仅仅是观察事件。
tags: [how-to]
---

# 拦截器扩展

`INTERCEPTOR` 是 `ExtensionCapability` 的第七类成员。与 `LIFECYCLE`（只读事件通知）和 `GUARDRAIL`（只允许/否决）不同，拦截器返回**控制流决策**，运行时会如实执行：改写工具参数、把危险调用路由进沙箱、拦截用户输入、逐请求调整模型参数。

典型场景：

- 合规/安全插件：改写工具参数（如强制工作目录）、把 `bash` 类工具路由到 Daytona/E2B 沙箱
- 注入防御 / PII 脱敏：在 prompt 进入 memory 与模型之前改写或拦截
- 多租户策略：按租户改写 systemPrompt、压低 temperature、收紧 maxOutputTokens
- 审计增强：`afterToolCall` 检查工具输出是否泄露敏感信息，必要时替换回灌给模型的结果

:::warning
`InterceptorRegistry` 与三个拦截器接口当前标注为 `@Experimental(since = "2.5.1")`，签名可能在小版本间演进；升级时 pin 确切版本，参见 [Extension 分层 - 扩展 SPI 稳定性矩阵](/docs/extending/overview#32-插件-spi-的稳定性矩阵)。
:::

## 1. 声明能力并注册拦截器

需要在 manifest 中声明 `INTERCEPTOR` 能力，否则 `apply(...)` 内调用 `context.interceptors().register*(...)` 会抛出 `ExtensionException("did not declare capability: interceptor")`。

```java
public ExtensionManifest manifest() {
    return ExtensionManifest.builder()
            .id("safety-pack")
            .name("Safety Pack")
            .capability(ExtensionCapability.INTERCEPTOR)
            .build();
}

public void apply(ExtensionContext context) {
    context.interceptors().registerToolCall(new SandboxBashInterceptor());
    context.interceptors().registerPrompt(new PiiRedactor());
    context.interceptors().registerModelRequest(new TenantScope());
}
```

三类拦截器接口，各自对应 agent 运行时的一个原生拦截面：

| 扩展接口 | 对应原生接口 | 决策类型 |
|---------|------------|---------|
| `ExtensionToolCallInterceptor` | `ToolInterceptor`（PreToolUse / PostToolUse） | `allow` / `block` / `modify` / `routeTo` |
| `ExtensionPromptInterceptor` | `PromptInterceptor`（UserPromptSubmit） | `allow` / `block` / `modify` |
| `ExtensionModelRequestInterceptor` | `ModelRequestHook` | `allow` / `modify`（无 block——否决模型调用属于 Guardrail 职责） |

## 2. 工具调用拦截器

`beforeToolCall` 在工具执行前求值；`afterToolCall` 在工具执行后拿到输出再求值（默认 allow）。请求载体是 `ExtensionToolCallRequest`（name / arguments / callId / type 四个可移植字段）。

```java
public class SandboxBashInterceptor implements ExtensionToolCallInterceptor {
    public String name() { return "sandbox-bash"; }

    public ExtensionToolCallDecision beforeToolCall(ExtensionToolCallRequest request) {
        if (!"bash".equals(request.getName())) {
            return ExtensionToolCallDecision.allow();
        }
        // 把 shell 类工具路由进沙箱而不是本地执行
        return ExtensionToolCallDecision.routeTo("daytona", "default", extractCommand(request));
    }

    public ExtensionToolCallDecision afterToolCall(ExtensionToolCallRequest request, String output) {
        if (output != null && output.contains("AKIA")) {
            return ExtensionToolCallDecision.block("tool output contained a credential");
        }
        return ExtensionToolCallDecision.allow();
    }
}
```

`ExtensionToolCallDecision` 四种判定：

- `allow()`：原样放行
- `block(reason)`：否决调用，`reason` 作为工具结果回灌给模型（模型可据此调整）
- `modify(newName, newArguments)`：改写调用后执行——`newName` 传 null 保留原工具名，`newArguments` 替换原始 JSON 参数（callId/type/metadata 保留）
- `routeTo(providerId, profile, command)`：把执行重定向到沙箱 provider（Daytona/E2B/…），插件负责"工具→命令"映射，运行时负责会话创建与执行

## 3. Prompt 拦截器

`ExtensionPromptInterceptor.intercept(ExtensionPromptRequest)` 在用户输入进入 memory / 模型之前求值：

- `allow()`：放行
- `block(reason)`：本轮不运行 agent，`reason` 成为运行输出（`PROMPT_BLOCKED: ...`）
- `modify(modifiedInput)`：改写后的输入继续走流程

```java
context.interceptors().registerPrompt(new ExtensionPromptInterceptor() {
    public String name() { return "pii-redactor"; }
    public ExtensionPromptDecision intercept(ExtensionPromptRequest request) {
        String cleaned = Redactor.maskPhones(request.getInput());
        return cleaned.equals(request.getInput())
                ? ExtensionPromptDecision.allow()
                : ExtensionPromptDecision.modify(cleaned);
    }
});
```

## 4. 模型请求拦截器

`ExtensionModelRequestInterceptor.intercept(ExtensionModelRequest)` 在每次模型调用前求值。请求视图是只读的六个标量字段（model / systemPrompt / instructions / temperature / topP / maxOutputTokens）——改写会话历史属于宿主层职责，不对扩展开放。

决策通过 `ExtensionModelRequestDecision` 的 `with*` 工厂表达，null 字段表示保留原值；`merge` 可叠加多个覆盖：

```java
context.interceptors().registerModelRequest(new ExtensionModelRequestInterceptor() {
    public String name() { return "tenant-scope"; }
    public ExtensionModelRequestDecision intercept(ExtensionModelRequest request) {
        return ExtensionModelRequestDecision.withSystemPrompt("You are tenant acme's assistant.")
                .merge(ExtensionModelRequestDecision.withMaxOutputTokens(2048));
    }
});
```

## 5. 组合顺序与激活语义

- **激活**：拦截器与 lifecycle hook 同语义——`enable(...)` 扩展即生效，不需要 `exposeTool(...)` / `allow*(...)` 这类逐资源授权。
- **宿主优先**：`AgentBuilder` 上 `toolInterceptor(...)` / `promptInterceptor(...)` / `modelRequestHook(...)` 注册的宿主拦截器先求值，扩展拦截器按注册顺序随后。
- **链式改写**：每个拦截器看到的是"当前有效值"——前面的 `MODIFY` 结果会喂给后面的拦截器；`BLOCK` / `ROUTE_TO` 直接短路。
- **模型请求**：host hook 的输出作为第一个扩展拦截器的输入，依次传递。
- **命名唯一**：拦截器按 `name()` 跨扩展唯一，重复名在 `snapshot()` 时 fail-fast（`duplicate ... interceptor id`）。

## 6. 启用与装配

```java
ExtensionRegistry registry = ExtensionRegistry.of(new SafetyPackExtension())
        .enable("safety-pack");

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("doubao-seed-1-8-251228")
        .toolRegistry(tools)
        .sandboxProvider(daytonaProvider)   // routeTo 决策需要配置沙箱 provider
        .extensions(registry)
        .build();
```

`AgentBuilder.extensions(registry)` 会把扩展拦截器与宿主拦截器组合成一条链交给运行时；运行时仍只认原生 `ToolInterceptor` / `PromptInterceptor` / `ModelRequestHook` 接口——扩展侧契约与运行时侧适配由 `ExtensionInterceptors` 桥接，`ai4j-extension-api` 不依赖 `ai4j-agent`。

:::tip 拦截器 vs Guardrail vs Lifecycle
- **Guardrail**：只允许/否决，语义最简单，按资源授权（`allowGuardrail`）。
- **Interceptor**：能改写、能路由、能拦输入——"决策型"钩子，随 enable 生效。
- **Lifecycle**：只读通知，永远不该改变控制流。
:::
