---
title: AgentFlow Auto-Configuration
description: 讲解 ai.agentflow.* 如何在 Spring Boot 下自动装配多 profile 的 AgentFlow 注册表（AgentFlowRegistry），把 Coze / Dify / N8N 等外部工作流平台接进 ai4j，以及 default-name、条件 Bean 与默认 AgentFlow 的解析规则。
tags: [integration]
---

# AgentFlow Auto-Configuration

`AgentFlow` 是 ai4j 对接外部工作流平台（Coze、Dify、N8N 等）的薄封装。这一页只讲它在 Spring Boot 下是怎么自动装配的，不讲单个平台的调用细节。

## 1. 配置入口：`ai.agentflow.*`

绑定类是 `AgentFlowProperties`（前缀 `ai.agentflow`），默认不启用：

```yaml
ai:
  agentflow:
    enabled: true
    default-name: coze-prod
    profiles:
      coze-prod:
        type: COZE
        base-url: https://api.coze.cn
        api-key: ${COZE_API_KEY}
        bot-id: ${COZE_BOT_ID}
      dify-internal:
        type: DIFY
        base-url: https://api.dify.ai
        api-key: ${DIFY_API_KEY}
        poll-interval-millis: 2000
        poll-timeout-millis: 60000
```

顶层字段：

- `enabled`：默认 `false`。只有显式设为 `true`，`AgentFlowRegistry` 才会被创建。
- `default-name`：可选，指向 `profiles` 里的一个 key，作为默认 AgentFlow。
- `profiles`：一个 map，key 是业务路由名，value 是一个平台 endpoint 配置。

每个 profile（`EndpointProperties`）支持的字段：

| 字段 | 说明 |
| --- | --- |
| `type` | 平台类型，枚举 `DIFY` / `COZE` / `N8N` |
| `base-url` | 平台 API 地址 |
| `api-key` | 平台密钥 |
| `bot-id` / `workflow-id` / `app-id` | 各平台需要的资源标识 |
| `user-id` / `conversation-id` | 会话上下文标识 |
| `webhook-url` | 回调地址（n8n 等需要） |
| `poll-interval-millis` | 轮询间隔，默认 `1000` |
| `poll-timeout-millis` | 轮询超时，默认 `60000` |
| `headers` | 附加请求头 map |

## 2. 装配链：`ai.agentflow.*` → `AgentFlowRegistry`

AgentFlow 的装配发生在 `AiConfigAutoConfiguration` 里，按条件注册两个 Bean：

```text
ai.agentflow.enabled=true
  -> AgentFlowRegistry (遍历 profiles，每个构造一个 AgentFlow)
     -> AgentFlow (当配置了 default-name 时，暴露默认那个)
```

关键条件：

- `AgentFlowRegistry`：`@ConditionalOnProperty(prefix = "ai.agentflow", name = "enabled", havingValue = "true")` + `@ConditionalOnMissingBean`。启动时遍历 `profiles`，对每个非空 entry 调用 `aiService.getAgentFlow(...)` 构造 `AgentFlow`，再连同 `default-name` 一起放进 registry。
- `AgentFlow`（默认）：`@ConditionalOnBean(AgentFlowRegistry.class)` + `@ConditionalOnProperty(prefix = "ai.agentflow", name = "default-name")` + `@ConditionalOnMissingBean(AgentFlow.class)`。方便业务代码直接注入一个默认的 `AgentFlow` 而不必先拿到 registry。

:::tip 这两个 Bean 都可被接管
`AgentFlowRegistry` 和 `AgentFlow` 都带 `@ConditionalOnMissingBean`。要完全自定义（例如从配置中心动态构建 profile），在自己的 `@Configuration` 里声明同名 Bean 即可，默认装配会自动让位。
:::

## 3. 默认 AgentFlow 怎么解析

`AgentFlowRegistry.getDefault()` 的解析规则是确定的，不是“随便挑一个”：

1. 如果配了 `ai.agentflow.default-name`，就返回该名字对应的 profile；找不到会抛 `IllegalArgumentException`。
2. 没配 `default-name`，但 `profiles` 恰好只有一个，就返回那个唯一的。
3. 既没配 `default-name`、profile 又不止一个，抛 `IllegalStateException`，提示“要么设 `default-name`，要么用 `registry.get(name)` 显式取”。

所以多 profile 场景下，`ai.agentflow.default-name` 不是可选项，而是必须项 —— 否则默认 `AgentFlow` Bean 不会被创建。

## 4. 业务代码怎么用

注入 registry 按名字取，或直接注入默认 AgentFlow：

```java
// ai4j 2.4.2, groupId io.github.lnyo-cly
import io.github.lnyocly.ai4j.AgentFlowRegistry;
import io.github.lnyocly.ai4j.agentflow.AgentFlow;

@Service
public class WorkflowService {
    private final AgentFlow coze;
    private final AgentFlow dify;

    public WorkflowService(AgentFlowRegistry registry) {
        this.coze = registry.get("coze-prod");   // 按名字取
        this.dify = registry.get("dify-internal");
    }
}

// 或者，只配了一个 profile / 配了 default-name 时
@Service
public class DefaultWorkflowService {
    private final AgentFlow agentFlow;
    public DefaultWorkflowService(AgentFlow agentFlow) {
        this.agentFlow = agentFlow; // 默认那个
    }
}
```

`AgentFlow` 内部按 `type` 适配对应平台的 chat / workflow 服务（Coze、Dify 走 chat 与 workflow；N8N 走 workflow），业务层不需要关心平台差异。

## 5. 常见踩坑

- **忘记 `enabled: true`**：`AgentFlowRegistry` 不会被创建，注入会失败。默认是关的。
- **多 profile 不配 `default-name`**：默认 `AgentFlow` Bean 不会创建，注入 `AgentFlow`（单数）会报错；这时要么补 `default-name`，要么改成注入 `AgentFlowRegistry` 自己取。
- **把 `type` 写错**：`type` 是枚举 `DIFY` / `COZE` / `N8N`，大小写或拼写错会导致 profile 无法适配正确平台。

## 6. 继续阅读

- 配置分层与其它 `ai.*` 前缀：见 [Configuration Reference](/docs/integrations/spring-boot/configuration-reference)
- 装配链与失败传播：见 [Spring Boot Auto Configuration](/docs/integrations/spring-boot/auto-configuration)
