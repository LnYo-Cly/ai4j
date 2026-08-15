---
sidebar_position: 12
title: A2A 协议 (Agent-to-Agent)
description: "Discover agents, exchange JSON-RPC tasks, stream SSE updates, and expose your ai4j agent as an A2A service with optional auth — JDK stdlib only."
tags: [integration]
---

# A2A 协议 (Agent-to-Agent)
ai4j provides a Java 8-compatible A2A integration surface for agent discovery, JSON-RPC task
exchange, task lifecycle, SSE streaming, push-notification configuration, Skills metadata, and
standard API-key or Bearer authentication. An ai4j agent can call an A2A endpoint as a client or
be exposed as an A2A service. JDK stdlib only, no new dependency.

> Scope note: `SendMessage` and `SendStreamingMessage` have bidirectional, opt-in verification
> against the official `a2a-sdk==1.1.0` Python implementation. Task lookup, listing,
> cancellation, push configuration, and standard security schemes have deterministic local
> regression coverage. Task state is intentionally in-memory and is not restart-durable.

## 0. 先理解 A2A 是什么（它和 SubAgent/Teams 完全不同）

A2A 是一个**跨实现的开放协议**，不是一个进程内的调用机制。理解它的关键在与前面两个能力的对比：

| 维度 | SubAgent / Agent Teams | A2A |
| --- | --- | --- |
| 作用范围 | 单 JVM 进程内 | 跨进程、跨语言、跨实现 |
| 对方是谁 | 你自己代码里的 `Agent` 对象 | 任何遵循 A2A 协议的远端服务（LangChain、CrewAI、Google、另一个 ai4j 实例……） |
| 怎么通讯 | 工具调用 / 共享内存（message bus） | HTTP + JSON-RPC（网络协议） |
| 需要发现吗 | 不需要，直接持有对象引用 | 需要——通过 AgentCard 发现对方能力 |

一句话：**SubAgent/Teams 是「我自己的 agent 之间协作」，A2A 是「我的 agent 和别人的 agent 协作」**。前者靠内存，后者靠标准协议。

### A2A 的三块基石

**1. AgentCard——能力发现**。每个 A2A 服务在 `/.well-known/agent-card.json` 暴露一张"名片"，声明自己的 name、description、skills（能做什么）、支持的接口、鉴权方式。调用方先 discover 这张卡，才知道对方能干什么、怎么调。

**2. JSON-RPC task——请求-响应**。核心操作是 `SendMessage`（同步）和 `SendStreamingMessage`（SSE 流式），走 JSON-RPC 2.0 over HTTP，带 `A2A-Version: 1.0` 头。请求里带 message，响应里带 `task.artifacts[].parts[].text`。ai4j 同时保留了旧版的 `tasks/send` / `message:send` HTTP 别名做向后兼容。

**3. Task 生命周期——异步状态机**。一个 task 不是"发完就结束"的单次 RPC，它有状态流转：

```
SUBMITTED → WORKING → COMPLETED
                  ↘ → FAILED
                  ↘ → CANCELED
              WORKING → INPUT_REQUIRED  （需要更多输入）
              WORKING → AUTH_REQUIRED   （需要鉴权）
```

`COMPLETED/FAILED/CANCELED/INPUT_REQUIRED/AUTH_REQUIRED` 是终态或需干预态。长任务可以先返回，client 用 `GetTask` 轮询、`CancelTask` 取消、或 `SubscribeToTask` 订阅更新（SSE 推送直到终态）。

### 为什么是 JSON-RPC + SSE 这套组合

A2A 任务可能跑很久（agent 要调工具、推理、多步）。纯 HTTP 请求-响应撑不住长耗时，所以协议设计成：**JSON-RPC 做请求结构 + SSE 做流式更新 + push-notification 做异步回调**。这是 agent 场景的标准解法（OpenAI Responses、Anthropic Messages 也用类似组合）。ai4j 的 push 回调默认只允许公网 HTTPS、不跟随重定向、拒绝内网地址——防止 SSRF。

:::note 与 OpenAI Responses 的区别
A2A 是 **agent-to-agent**（两个 agent 互相对话），Responses 是 **client-to-agent**（你的应用调一个 agent）。A2A 的 task 生命周期和 AgentCard 发现机制是它比 Responses 多出来的东西，因为对等 agent 需要协商"你是谁、你能干什么"。
:::

## A2A Client — call external agents

```java
A2AClient client = new A2AClient();
// optional auth:
// A2AClient client = new A2AClient("shared-secret-key");

AgentCard card = client.discover("https://other-agent.example.com");
// discover() tries /.well-known/agent-card.json first, then the legacy /.well-known/agent.json.
String response = client.sendTask("https://other-agent.example.com", "What is 2+2?");
```

When the standard AgentCard advertises a JSON-RPC 1.0 interface, `sendTask()` sends
`SendMessage` to that interface URL with `A2A-Version: 1.0` and reads the returned
`result.task.artifacts[].parts[].text`. If no standard interface is advertised, it retains the
legacy `POST /tasks/send` behavior.

### Tasks and streaming

Standard JSON-RPC peers can return immediately and be polled, canceled, or subscribed to:

```java
JSONObject result = client.sendTaskResponse("https://other-agent.example.com", "Process this", true);
String taskId = result.getJSONObject("task").getString("id");

JSONObject current = client.getTask("https://other-agent.example.com", taskId);
JSONObject all = client.listTasks("https://other-agent.example.com");
client.cancelTask("https://other-agent.example.com", taskId);

client.sendStreamingTask("https://other-agent.example.com", "Stream this", event -> {
    // event contains task, statusUpdate, or artifactUpdate.
});
client.subscribeToTask("https://other-agent.example.com", taskId, event -> {
    // receives the current task followed by updates until terminal state.
});
```

Wrap the client as a tool so your agent can call external agents. `A2ATool` is only the
`ToolExecutor` — the model also needs a tool *definition* in the registry. Build a
`Tool.Function` named whatever you like (here `ask_remote_agent`) with a single `message`
parameter, and register it via `StaticToolRegistry`:

```java
A2ATool a2a = new A2ATool("https://other-agent.example.com");

Tool.Function fn = new Tool.Function();
fn.setName("ask_remote_agent");
fn.setDescription("Ask a remote A2A agent a question.");
Tool.Function.Parameter param = new Tool.Function.Parameter();
Map<String, Tool.Function.Property> props = new HashMap<>();
Tool.Function.Property msgProp = new Tool.Function.Property();
msgProp.setType("string");
msgProp.setDescription("The message to send");
props.put("message", msgProp);
param.setProperties(props);
param.setRequired(Collections.singletonList("message"));
fn.setParameters(param);
AgentToolRegistry registry = new StaticToolRegistry(
        Collections.singletonList(new Tool("function", fn)));

Agent agent = Agents.react()
        .modelClient(modelClient).model("glm-5.1")
        .toolExecutor(a2a)      // routes ask_remote_agent to the A2A endpoint
        .toolRegistry(registry) // exposes the tool definition to the model
        .build();
// the agent can now call the external A2A agent as a tool
```

The tool name is not hardcoded in `A2ATool` — it executes whatever call your registry
advertises, extracts the `message` argument, and sends it via `sendTask`.

## Client push-notification configuration (CRUD)

Long-running tasks can call you back. The client side of that is a per-task CRUD over
JSON-RPC (`CreateTaskPushNotificationConfig` / `Get…` / `List…` / `Delete…`):

```java
// register a callback for a task (optionally with a bearer token and A2A AuthenticationInfo)
JSONObject config = client.createTaskPushNotificationConfig(
        baseUrl, taskId, "https://my-host.example.com/a2a/callback", "callback-secret");

// with explicit A2A authentication fields (sent as the callback Authorization header):
Map<String, Object> auth = new HashMap<>();
auth.put("scheme", "bearer");
// auth.put("credentials", "...");   // write-only on the server side
client.createTaskPushNotificationConfig(baseUrl, taskId, callbackUrl, token, auth);

JSONObject one   = client.getTaskPushNotificationConfig(baseUrl, taskId, configId);
JSONObject all   = client.listTaskPushNotificationConfigs(baseUrl, taskId);
client.deleteTaskPushNotificationConfig(baseUrl, taskId, configId);
```

Notes: a task accepts at most **32** push configurations — delete stale ones before adding
more. Callback tokens and `authentication.credentials` are write-only: create/get/list
responses never disclose them.

## A2A Server — expose your agent

```java
A2AServer server = new A2AServer(
        myAgent,      // the ai4j Agent to expose
        0,            // port (0 = auto-assign)
        "my-agent",   // name (shown in AgentCard)
        "does stuff", // description
        "secret-key"  // optional shared-secret auth (null = open)
);
int port = server.getPort();        // actual port when 0 = auto-assign
String baseUrl = server.getBaseUrl(); // http://localhost:<port>

// External agents (LangChain, CrewAI, etc.) can now call:
//   GET  http://localhost:PORT/.well-known/agent-card.json  → AgentCard
//   POST http://localhost:PORT/                             → A2A 1.0 SendMessage JSON-RPC
//   GET  http://localhost:PORT/.well-known/agent.json       → legacy alias
//   POST http://localhost:PORT/tasks/send                   → JSON-RPC task
//   POST http://localhost:PORT/message:send                 → compatible task alias
server.close();
```

The standard card advertises `supportedInterfaces`, default `text/plain` modes, standard Skill
fields (`id`, modes, tags, and examples), streaming and push capabilities, and security schemes
when configured. The root endpoint supports `SendMessage`, `GetTask`, `ListTasks`, `CancelTask`,
`SendStreamingMessage`, `SubscribeToTask`, and task push-configuration CRUD. It accepts
`A2A-Version: 1.0` and rejects an explicit unsupported version; omission remains accepted for
official SDK compatibility. The existing aliases keep their previous lower-case task-state
contract.

### Server internals — how push delivery actually works

The server's push policy in one sentence: **public HTTPS only, revalidated immediately before
delivery, redirects not followed.** The mechanism behind each piece:

- **Callback URL policy (SSRF protection).** Validation happens twice: once when the push
  config is created, and **again immediately before each delivery** — the second check exists
  because DNS can be repointed between creation and delivery. Public HTTPS only; local and
  private targets are rejected; redirects are not followed (a redirect could bounce a public
  URL to an internal one).
- **Known residual gap (by design of the JDK).** DNS resolution and the actual URL connection
  cannot be made atomic with `java.net.URL`/`HttpURLConnection` — a DNS rebinding race remains
  theoretically open. High-risk deployments should additionally restrict egress destinations at
  the network layer. This is documented rather than worked around.
- **Delivery isolation.** Callbacks are delivered on a **separate bounded executor**, so a slow
  or hanging callback receiver consumes callback slots, never Agent execution slots.
- **Credential handling.** Callback tokens and `authentication.credentials` are write-only:
  create/get/list never return them; the server keeps them solely to build the callback
  `Authorization` header (`scheme` + `credentials` from the A2A `authentication` fields).
- **Per-task cap.** Each task accepts at most **32** push configurations; adding more requires
  deleting stale ones first.

To run push against a local/test receiver (blocked by the public-HTTPS default), override the
policy explicitly:

```java
A2AServer server = new A2AServer(agent, 0, "name", "desc", null)
        .withPushNotificationUrlValidator(url -> url.startsWith("http://localhost:")); // test only!
```

`withPushNotificationUrlValidator(Predicate<String>)` replaces the default validator — the
predicate receives the raw callback URL and returns whether delivery is allowed.

## Publish ai4j Skills to the AgentCard

`A2ASkill` describes a capability in an A2A AgentCard; an ai4j `SkillDescriptor` describes a local `SKILL.md` Skill (see [Agent 技能](/docs/agent/skills)). They are different models, so the SDK ships a small bridge — `io.github.lnyocly.ai4j.agent.a2a.A2ASkillMapper` — that publishes your resolved ai4j Skills as A2A skills on the server's card.

`A2ASkillMapper` is a final utility class with three static entry points:

| 方法 | 作用 |
| --- | --- |
| `mapToA2ASkill(SkillDescriptor)` | 把单个 ai4j skill 映射成 `A2ASkill`；descriptor 为 `null` 或 name 为空时返回 `null`。 |
| `mapToA2ASkills(List<SkillDescriptor>)` | 批量映射，自动跳过 `null`。 |
| `addSkillsToServer(A2AServer, List<SkillDescriptor>)` | 逐个映射并调 `server.withSkill(name, description, inputSchema)`，把 skill 直接挂到 AgentCard。 |

当前只映射 `name` 和 `description`；`inputSchema` 留空（A2A 中可选）。后续如果 `SKILL.md` frontmatter 定义了 input schema 字段，可以在此处提取。

典型用法：把一个 `AgentSkillResolver` 解析出的允许 skill 列表发布到 A2A 服务上。

```java
import io.github.lnyocly.ai4j.agent.a2a.A2AServer;
import io.github.lnyocly.ai4j.agent.a2a.A2ASkillMapper;
import io.github.lnyocly.ai4j.skill.SkillDescriptor;

import java.util.List;

A2AServer server = new A2AServer(myAgent, 0, "my-agent", "does stuff", null);

// allowedSkills 来自你的 AgentSkillResolver / 租户授权，不是 SDK 内置来源
List<SkillDescriptor> allowedSkills = resolver.resolveAllowedSkills(currentTenantId());

// 把 ai4j Skills 发布为 AgentCard 上的 A2A skills
A2ASkillMapper.addSkillsToServer(server, allowedSkills);
```

:::warning 映射不等于授权
`A2ASkillMapper` 只搬运已授权 skill 的元数据（name/description）。它不读取 skill body，也不绕过 `AgentSkillResolver` 的租户授权决策——传入的 `SkillDescriptor` 列表必须已经是当前租户/用户被允许看到的 skill。A2A 调用方看到这些 skill 不代表能直接执行；执行仍受宿主的工具、审批、sandbox 策略约束（见 [Agent Skills — Prompt and tool boundary](/docs/agent/skills#prompt-and-tool-boundary)）。
:::

## Auth

Both client and server support optional shared-secret authentication. AgentCard discovery stays
open so peers can select the advertised scheme.

- **API key**: `new A2AClient("secret-key")` reads a standard `apiKeySecurityScheme` from the
  AgentCard and uses its declared header. `A2AServer.withAuthentication("api-key", "X-My-Key", ...)
  configures the matching server metadata and check.
- **Bearer**: `A2AClient.bearerToken("token")` sends `Authorization: Bearer token`.
  `A2AServer.withBearerAuthentication(...)` advertises and validates the matching HTTP Bearer
  scheme.

:::note
The server intentionally does not implement JWT/OIDC token verification; use a terminating
identity-aware gateway for those flows.
:::

## Current limitations

What this implementation deliberately does **not** do:

- **Task state is in-memory, not restart-durable.** Restarting the server process loses
  running tasks and their push configurations. For durable sessions use the agent-level
  session store (see [Session Runtime](/docs/agent/session-runtime)) and re-register
  callbacks after restart.
- **No JWT/OIDC verification.** Auth is API-key or Bearer shared-secret only. Terminate
  identity-aware flows at a gateway in front of the server.
- **DNS rebinding race is open at the JDK layer** (see Server internals). Compensate at the
  network egress layer for high-risk deployments.
- **Legacy aliases kept for compatibility.** `POST /tasks/send` and `POST /message:send`
  still speak the older lower-case task-state contract; new integrations should use the
  JSON-RPC surface.

## External Interoperability Regression

`A2AOfficialJsonRpcTest` has deterministic JVM contract coverage plus four opt-in checks against
the official Python SDK: both client directions for `SendMessage` and `SendStreamingMessage`.
Supply the peer URL, Python executable, and its working directory as Maven system properties;
none are committed or required by default CI.

## Where this fits

- **Interception hooks** (control what the agent does): [拦截钩子](/docs/agent/governance/tool-interceptor).
- **Sandbox SPI** (isolate tool execution): [Sandbox SPI](/docs/agent/governance/sandbox-spi).
- **Session + compaction** (manage long sessions): [Memory & Compact](/docs/agent/memory/memory-compact-context).
