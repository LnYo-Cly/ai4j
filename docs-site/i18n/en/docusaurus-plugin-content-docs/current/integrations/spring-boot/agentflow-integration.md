---
title: "AgentFlow Auto-Configuration"
description: "Explains how ai.agentflow.* auto-configures the multi-profile AgentFlow registry (AgentFlowRegistry) under Spring Boot to wire external workflow platforms such as Coze, Dify, and N8N into ai4j, plus the resolution rules for default-name, conditional beans, and the default AgentFlow."
tags: [integration]
---

# AgentFlow Auto-Configuration

`AgentFlow` is ai4j's thin wrapper around external workflow platforms (Coze, Dify, N8N, etc.). This page only covers how it is auto-configured under Spring Boot; it does not go into the call details of any single platform.

## 1. Configuration entry point: `ai.agentflow.*`

The bound class is `AgentFlowProperties` (prefix `ai.agentflow`), disabled by default:

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

Top-level fields:

- `enabled`: defaults to `false`. `AgentFlowRegistry` is created only when this is explicitly set to `true`.
- `default-name`: optional, points to a key in `profiles` to use as the default AgentFlow.
- `profiles`: a map whose key is a business routing name and whose value is a platform endpoint configuration.

Fields supported by each profile (`EndpointProperties`):

| Field | Description |
| --- | --- |
| `type` | Platform type, one of the enum values `DIFY` / `COZE` / `N8N` |
| `base-url` | Platform API address |
| `api-key` | Platform secret |
| `bot-id` / `workflow-id` / `app-id` | Resource identifiers required by each platform |
| `user-id` / `conversation-id` | Session context identifiers |
| `webhook-url` | Callback URL (required by n8n and similar) |
| `poll-interval-millis` | Polling interval, defaults to `1000` |
| `poll-timeout-millis` | Polling timeout, defaults to `60000` |
| `headers` | Map of additional request headers |

## 2. Wiring chain: `ai.agentflow.*` → `AgentFlowRegistry`

AgentFlow wiring happens inside `AiConfigAutoConfiguration`, which registers two beans conditionally:

```text
ai.agentflow.enabled=true
  -> AgentFlowRegistry (iterates profiles, building one AgentFlow per entry)
     -> AgentFlow (when default-name is configured, exposes the default one)
```

Key conditions:

- `AgentFlowRegistry`: `@ConditionalOnProperty(prefix = "ai.agentflow", name = "enabled", havingValue = "true")` + `@ConditionalOnMissingBean`. At startup it iterates `profiles`, and for each non-empty entry calls `aiService.getAgentFlow(...)` to build an `AgentFlow`, then places it in the registry along with the `default-name`.
- `AgentFlow` (default): `@ConditionalOnBean(AgentFlowRegistry.class)` + `@ConditionalOnProperty(prefix = "ai.agentflow", name = "default-name")` + `@ConditionalOnMissingBean(AgentFlow.class)`. This lets business code inject a default `AgentFlow` directly without first having to obtain the registry.

:::tip Both beans can be taken over
`AgentFlowRegistry` and `AgentFlow` both carry `@ConditionalOnMissingBean`. To take full control (for example, to build profiles dynamically from a configuration center), declare a bean with the same name in your own `@Configuration`; the default wiring will step aside automatically.
:::

## 3. How the default AgentFlow is resolved

The resolution rules of `AgentFlowRegistry.getDefault()` are deterministic, not "pick any one":

1. If `ai.agentflow.default-name` is configured, the profile for that name is returned; if it cannot be found, an `IllegalArgumentException` is thrown.
2. If `default-name` is not configured but `profiles` happens to contain exactly one entry, that single entry is returned.
3. If `default-name` is not configured and there is more than one profile, an `IllegalStateException` is thrown, advising you to "either set `default-name`, or call `registry.get(name)` explicitly".

So in a multi-profile scenario, `ai.agentflow.default-name` is not optional but required — otherwise the default `AgentFlow` bean will not be created.

## 4. How business code uses it

Inject the registry and look up by name, or inject the default AgentFlow directly:

```java
// ai4j 2.4.2, groupId io.github.lnyo-cly
import io.github.lnyocly.ai4j.AgentFlowRegistry;
import io.github.lnyocly.ai4j.agentflow.AgentFlow;

@Service
public class WorkflowService {
    private final AgentFlow coze;
    private final AgentFlow dify;

    public WorkflowService(AgentFlowRegistry registry) {
        this.coze = registry.get("coze-prod");   // look up by name
        this.dify = registry.get("dify-internal");
    }
}

// Or, when only one profile is configured / default-name is set
@Service
public class DefaultWorkflowService {
    private final AgentFlow agentFlow;
    public DefaultWorkflowService(AgentFlow agentFlow) {
        this.agentFlow = agentFlow; // the default one
    }
}
```

Internally, `AgentFlow` adapts to the chat / workflow service of the matching platform based on `type` (Coze and Dify use chat and workflow; N8N uses workflow), so the business layer does not need to care about platform differences.

## 5. Common pitfalls

- **Forgetting `enabled: true`**: `AgentFlowRegistry` will not be created and injection will fail. It is off by default.
- **Multiple profiles without `default-name`**: the default `AgentFlow` bean will not be created, and injecting `AgentFlow` (singular) will fail; either add `default-name`, or switch to injecting `AgentFlowRegistry` and look it up yourself.
- **Misspelling `type`**: `type` is the enum `DIFY` / `COZE` / `N8N`; wrong casing or spelling will prevent the profile from adapting to the correct platform.

## 6. Further reading

- Configuration layering and other `ai.*` prefixes: see [Configuration Reference](/docs/integrations/spring-boot/configuration-reference)
- Wiring chain and failure propagation: see [Spring Boot Auto Configuration](/docs/integrations/spring-boot/auto-configuration)
