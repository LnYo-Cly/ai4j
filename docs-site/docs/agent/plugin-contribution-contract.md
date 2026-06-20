---
title: Plugin Contribution Contract
sidebar_label: Plugin Contribution Contract
---

# Plugin Contribution Contract

`ai4j-extension-api` 现在把“插件能贡献什么”分成两层：

1. `ExtensionCapability`：插件包声明自己具备哪类能力。
2. `ExtensionContribution`：插件包声明具体贡献项，例如某个 Tool、CLI command、Sandbox provider 或 Remote runner provider。

这样第三方插件作者可以先发布稳定的 manifest 元数据；宿主应用、`ai4j-agent`、`ai4j-cli` 再决定是否安装、启用、暴露或绑定到真实运行时。

## 1. 为什么需要 Contribution Contract

早期插件只需要注册 Tool、Command、Skill、Prompt、Guardrail、Lifecycle Hook。后续 Agent SDK 需要支持更多可组合能力：

- Memory store
- Compact policy
- Context projector
- Sandbox provider
- Remote Agent runner provider
- CLI command / UI contribution

这些能力不一定都有统一的 runtime registry。比如 Sandbox provider 可能由业务方、容器平台、VM 平台或第三方 jar 实现；AI4J 不应该为了一个 provider 强行把所有平台细节塞进核心模块。

Contribution Contract 的作用是：

- 让插件作者明确声明“我贡献了什么”。
- 让使用者在安装前能看懂插件影响面。
- 让宿主可以做 enable / expose / permission / binding 决策。
- 让 docs、CLI、dashboard 或校验器能展示稳定清单。

## 2. Capability 与 Contribution 的区别

| 概念 | 位置 | 作用 |
| --- | --- | --- |
| `ExtensionCapability` | `ExtensionManifest` | 包级能力声明；也是部分 registry 的运行时 gate。 |
| `ExtensionContributionType` | `ExtensionContribution` | 具体贡献类型，例如 `tool`、`sandbox-provider`、`runner-provider`。 |
| `ExtensionContribution` | `ExtensionManifest` | 具体贡献项：名称、描述、权限、是否需要显式宿主绑定。 |

示例：

```java
ExtensionManifest.builder()
        .id("sandbox-pack")
        .name("Sandbox Pack")
        .version("1.0.0")
        .vendor("example")
        .capability(ExtensionCapability.SANDBOX_PROVIDER)
        .contribution(ExtensionContribution.builder()
                .type(ExtensionContributionType.SANDBOX_PROVIDER)
                .name("cube-sandbox")
                .description("Create external sandbox sessions through a host-bound provider.")
                .permission("sandbox.create")
                .build())
        .configPrefix("ai4j.extensions.sandbox")
        .build();
```

注意：这段只声明贡献元数据，不会自动创建 sandbox，也不会读取密钥。

## 3. 当前支持的 Contribution Type

| Type | 典型宿主 | 说明 |
| --- | --- | --- |
| `TOOL` | `ai4j-agent` | Agent tool，仍需 `enable(...)` + `exposeTool(...)`。 |
| `COMMAND` | `ai4j-extension-api` / host | 普通扩展命令。 |
| `CLI_COMMAND` | `ai4j-cli` | CLI slash command 或命令入口。 |
| `SKILL` | host / agent tools | Skill 资源。 |
| `PROMPT` | host / agent tools | Prompt 资源。 |
| `GUARDRAIL` | `ai4j-agent` | 策略判断或工具执行保护。 |
| `LIFECYCLE` | `ai4j-agent` | 观察 Agent run / model / tool / compact 事件。 |
| `MEMORY_STORE` | `ai4j-agent` / host | 可替换 memory store。 |
| `COMPACT_POLICY` | `ai4j-agent` / host | 可替换 compact policy。 |
| `CONTEXT_PROJECTOR` | `ai4j-agent` / host | 可替换 context projector。 |
| `SANDBOX_PROVIDER` | `ai4j-agent` / `ai4j-coding` / host | 创建或恢复 sandbox session 的 provider。 |
| `RUNNER_PROVIDER` | `ai4j-agent` / host | 远端 Agent runner provider。 |
| `UI` | host / CLI / web app | UI 面板、渲染扩展或交互入口的元数据。 |

## 4. 安全默认值

Contribution Contract 不改变现有安全边界：

- 插件被发现，不等于启用。
- 插件被启用，不等于 tool 自动暴露。
- provider-style contribution 被声明，不等于已经绑定真实 provider。
- `requiresExplicitActivation=true` 是默认值，表示宿主必须显式绑定或允许。
- 涉及 tool、guardrail、memory、compact、context projector、sandbox、runner、CLI command、UI 的贡献项应声明 `permission(...)` 元数据。

例如 `ask-user` 官方插件声明了 tool 和 CLI command 都需要 `ui.prompt`：

```java
.contribution(ExtensionContribution.builder()
        .type(ExtensionContributionType.TOOL)
        .name("ask_user")
        .description("Host-mediated user clarification tool; requires explicit tool exposure.")
        .permission("ui.prompt")
        .build())
```

## 5. inspect / activation plan

宿主可以从 `ExtensionRegistry` 获取贡献清单：

```java
ExtensionRegistry registry = ExtensionRegistry.of(new AskUserExtension());
ExtensionInspectionSnapshot snapshot = registry.inspectRuntime("ask-user");

snapshot.getContributions().forEach(contribution -> {
    System.out.println(contribution.getTypeId() + ":" + contribution.getName());
});
```

`activationPlan(...)` 也会展示 contribution 状态：

```java
ExtensionActivationPlan plan = registry
        .enable("ask-user")
        .activationPlan("ask-user");

plan.getContributions().forEach(item -> {
    System.out.println(item.getType() + ":" + item.getName() + " -> " + item.getState());
});
```

provider-style contribution 默认会显示为 inactive，原因是 `requires host binding`。这表示插件包声明了能力，但宿主还没有把它绑定到真实运行时。

## 6. Validator 行为

`ExtensionValidator` 会检查：

- manifest 的 name/version/vendor 是否完整。
- runtime registry 贡献是否与 capability 一致。
- metadata-only capability 是否有 contribution 元数据。
- contribution 是否有描述。
- 需要显式启用的 provider/tool/guardrail/memory/sandbox/runner/CLI/UI 贡献是否声明权限元数据。

常见 warning：

| Code | 意义 |
| --- | --- |
| `capability.contribution.missing` | 声明了 metadata-only capability，但没有写 contribution 元数据。 |
| `contribution.description.missing` | 贡献项缺少说明。 |
| `contribution.permission.missing` | 需要宿主显式启用的高影响贡献缺少权限元数据。 |

## 7. 与 Sandbox / Runner 的关系

`SANDBOX_PROVIDER` 和 `RUNNER_PROVIDER` 只是 manifest contract：

- 不创建真实容器。
- 不读取 provider key。
- 不决定隔离策略。
- 不替代 permission policy。

真实执行仍由后续宿主代码完成：

- `ai4j-agent` 定义 `SandboxProvider` / `AgentRunnerProvider` 等 SPI。
- `ai4j-coding` 把 shell/file/browser/project 工具路由到 sandbox。
- `ai4j-cli` 展示 `/sandbox`、runner status、logs、artifacts。
- 第三方插件或业务系统接入具体平台。

## 8. 写插件时的建议

1. manifest 中同时写 capability 和 contribution。
2. 高影响 contribution 写清 permission。
3. provider-style contribution 默认保留 `requiresExplicitActivation=true`。
4. Tool 仍然通过 `context.tools().register(...)` 注册真实 executor。
5. Sandbox/Runner provider 这类能力先只声明 manifest 元数据，具体绑定由宿主完成。
6. 不要在 manifest、resource 或 docs 中写密钥、token、个人本地路径。
