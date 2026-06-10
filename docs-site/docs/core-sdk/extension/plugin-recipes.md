# Plugin Recipes

这一页解决插件使用者的组装问题：**插件 jar 已经进入 classpath 以后，应该怎么检查、启用、授权、暴露，并接进 Java、Spring Boot、Agent、Coding Agent 或 CLI。**

如果你还不知道插件包是什么，先看 [Plugin Packages](/docs/core-sdk/extension/plugin-packages)。如果你要写第三方插件，再看 [Plugin Author Cookbook](/docs/core-sdk/extension/plugin-author-cookbook)。

## 1. 先按资源类型拆开

AI4J 插件包可以贡献多种资源。它们不是同一个开关。

| 资源 | 进入方式 | 谁会使用 | 是否模型可见 |
| --- | --- | --- | --- |
| Tool | `exposeTool(...)` / `ai.extensions.tools.expose` | Agent / Coding Agent tool loop | 是 |
| Command | `allowCommand(...)` / `ai.extensions.commands.allow` | CLI 或宿主显式调用 | 否 |
| Skill | `allowSkill(...)` / `ai.extensions.skills.allow` | Coding Agent 上下文资源 | 否，模型先看到资源名和可读路径 |
| Prompt | `allowPrompt(...)` / `ai.extensions.prompts.allow` | 宿主或 Coding Agent 按需读取 | 否，除非宿主主动注入 |
| Guardrail | `allowGuardrail(...)` / `ai.extensions.guardrails.allow` | tool execution 前置判断 | 否 |

插件接入时建议把这五类资源写成一份 recipe，而不是只写“启用插件”。这样使用者可以判断哪些能力会进入模型上下文，哪些只是给宿主或 CLI 使用。

## 2. 标准接入顺序

### 2.1 加依赖

插件是普通 Maven / Gradle 依赖。以官方 `ask-user` 插件为例：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-plugin-ask-user</artifactId>
</dependency>
```

如果没有使用 `ai4j-bom`，需要显式写版本：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-plugin-ask-user</artifactId>
  <version>${ai4j.version}</version>
</dependency>
```

AI4J 不会自动远程下载插件，也不会替你改 `pom.xml`。依赖进入 classpath 以后，才有后续发现、检查和启用。

### 2.2 先检查 classpath

```bash
ai4j-cli extension list
ai4j-cli extension inspect ask-user --runtime
ai4j-cli extension validate ask-user
```

这三条命令回答不同问题：

| 命令 | 回答什么 |
| --- | --- |
| `list` | 当前 classpath 上发现了哪些插件 |
| `inspect --runtime` | 插件实际贡献了哪些 tool、command、Skill、Prompt、Guardrail |
| `validate` | manifest、资源路径、tool schema、`apply(...)` 注册逻辑是否可被 AI4J 稳定消费 |

`inspect --runtime` 和 `validate` 会临时调用插件 `apply(...)` 收集注册信息，但不会暴露 tool 给模型，也不会执行 command。

### 2.3 写 activation plan

严格模式下，先用 CLI 预览一次：

```bash
ai4j-cli extension plan ask-user --enable \
  --expose-tool ask_user \
  --allow-command ask-user \
  --allow-skill ask-user-collaboration \
  --allow-prompt ask-user-question \
  --strict
```

你应该看到：

```text
enabled=true
explicitResourceActivation=true
tools:
- name=ask_user state=active reason=exposeTool allowlist
commands:
- name=ask-user state=active reason=resource allowlist
skills:
- name=ask-user-collaboration state=active reason=resource allowlist
prompts:
- name=ask-user-question state=active reason=resource allowlist
```

如果资源名写错，plan 会显示 `not registered by extension`。这个阶段修正成本最低。

需要放进 CI 或发布前 smoke 时，使用 `check`：

```bash
ai4j-cli extension check ask-user --enable \
  --expose-tool ask_user \
  --allow-command ask-user \
  --allow-skill ask-user-collaboration \
  --allow-prompt ask-user-question \
  --strict
```

`plan` 只负责预览，打印 inactive 原因后仍返回 0；`check` 会先跑 validation，再检查本次命令显式请求的 tool、command、Skill、Prompt、Guardrail 是否 active。validation 有 error 或被请求资源 inactive 时，`check` 返回非零。未请求的插件资源不会让 `check` 失败。

## 3. Recipe A：普通 Java 接入 `ask-user`

适合自己创建 `Agent` 或 `CodingAgent`，不依赖 Spring Boot 自动装配。

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("ask-user")
        .requireExplicitResourceActivation()
        .allowCommand("ask-user")
        .allowSkill("ask-user-collaboration")
        .allowPrompt("ask-user-question")
        .exposeTool("ask_user");
```

接入 Agent：

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("glm-4.5-flash")
        .extensions(registry)
        .build();
```

接入 Coding Agent：

```java
CodingAgent agent = CodingAgents.builder()
        .modelClient(modelClient)
        .model("glm-4.5-flash")
        .workspaceContext(workspaceContext)
        .extensions(registry)
        .build();
```

这份配置的效果是：

- `ask_user` 进入模型可见 tool 列表。
- `ask-user` command 可以被 CLI 或宿主显式调用。
- `ask-user-collaboration` Skill 和 `ask-user-question` Prompt 可以作为只读资源被读取。
- 未列出的 command、Skill、Prompt 不会因为 `enable("ask-user")` 自动进入严格运行态。

## 4. Recipe B：Spring Boot 接入 `ask-user`

适合希望 starter 创建 `ExtensionRegistry` 和 `ExtensionRuntimeSnapshot` 的项目。

```yaml
ai:
  extensions:
    enabled:
      - ask-user
    explicit-resource-activation: true
    tools:
      expose:
        - ask_user
    commands:
      allow:
        - ask-user
    skills:
      allow:
        - ask-user-collaboration
    prompts:
      allow:
        - ask-user-question
```

然后在自己的 Agent bean 中注入 registry：

```java
@Bean
public Agent agent(ModelClient modelClient, ExtensionRegistry extensionRegistry) {
    return Agents.react()
            .modelClient(modelClient)
            .model("glm-4.5-flash")
            .extensions(extensionRegistry)
            .build();
}
```

starter 只负责装配 registry 和 snapshot，不会自动创建 Agent，也不会替你决定模型、memory、tool policy 或业务恢复流程。

## 5. Recipe C：CLI 做接入前检查

适合把第三方插件交给宿主应用前，先做人工验证。

```bash
ai4j-cli extension validate ask-user
ai4j-cli extension plan ask-user --enable \
  --expose-tool ask_user \
  --allow-command ask-user \
  --allow-skill ask-user-collaboration \
  --allow-prompt ask-user-question \
  --strict
ai4j-cli extension check ask-user --enable \
  --expose-tool ask_user \
  --allow-command ask-user \
  --allow-skill ask-user-collaboration \
  --allow-prompt ask-user-question \
  --strict
ai4j-cli extension resource --enable ask-user \
  --allow-skill ask-user-collaboration \
  skill ask-user-collaboration
ai4j-cli extension resource --enable ask-user \
  --allow-prompt ask-user-question \
  prompt ask-user-question
ai4j-cli extension run --enable ask-user \
  --allow-command ask-user \
  ask-user "Should I continue with this file rewrite?"
```

这些命令仍然不等于 Agent 已经能调用 tool。Agent 是否能调用，只看宿主是否通过 `.exposeTool(...)` 或 `ai.extensions.tools.expose` 把 tool 暴露给模型。

## 6. Recipe D：多个插件一起组装

多个插件不要写成“全启用”。推荐把每个插件分成一行 enable，再按资源类型集中列 allowlist。

假设有三个插件：

| 插件 | 负责什么 | 暴露策略 |
| --- | --- | --- |
| `ask-user` | 让 agent 请求宿主向用户提问 | 暴露 `ask_user` tool，授权 command / Skill / Prompt |
| `weather-pack` | 查询天气 | 暴露 `weather.search` tool，授权 `weather-check` command |
| `repo-policy-pack` | 约束 coding agent 工具调用 | 授权 `repo-policy.safe-write` guardrail，不暴露 tool |

普通 Java：

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("ask-user")
        .enable("weather-pack")
        .enable("repo-policy-pack")
        .requireExplicitResourceActivation()
        .exposeTool("ask_user")
        .exposeTool("weather.search")
        .allowCommand("ask-user")
        .allowCommand("weather-check")
        .allowSkill("ask-user-collaboration")
        .allowPrompt("ask-user-question")
        .allowGuardrail("repo-policy.safe-write");
```

Spring Boot：

```yaml
ai:
  extensions:
    enabled:
      - ask-user
      - weather-pack
      - repo-policy-pack
    explicit-resource-activation: true
    tools:
      expose:
        - ask_user
        - weather.search
    commands:
      allow:
        - ask-user
        - weather-check
    skills:
      allow:
        - ask-user-collaboration
    prompts:
      allow:
        - ask-user-question
    guardrails:
      allow:
        - repo-policy.safe-write
```

接入前分别跑三次 plan：

```bash
ai4j-cli extension plan ask-user --enable \
  --expose-tool ask_user \
  --allow-command ask-user \
  --allow-skill ask-user-collaboration \
  --allow-prompt ask-user-question \
  --strict

ai4j-cli extension plan weather-pack --enable \
  --expose-tool weather.search \
  --allow-command weather-check \
  --strict

ai4j-cli extension plan repo-policy-pack --enable \
  --allow-guardrail repo-policy.safe-write \
  --strict
```

`extension plan` 当前按单个插件输出 activation state。多插件组合时，逐个插件检查更容易发现资源名拼错或依赖没进 classpath。接入 recipe 固定后，把同样参数换成 `extension check` 放进 CI 或发布前 smoke；`plan` 适合人工预览，`check` 适合机器判定。

## 7. 第三方插件 README 应该给出的 recipe

第三方插件作者发布插件时，README 至少给出下面这组信息。少了这些，使用者很难安全组装。

```md
## AI4J 接入

### Maven

<dependency>
  <groupId>com.example.ai4j</groupId>
  <artifactId>weather-ai4j-plugin</artifactId>
  <version>1.0.0</version>
</dependency>

### Extension id

weather-pack

### 贡献资源

| 类型 | 名称 | 是否建议启用 | 说明 |
| --- | --- | --- | --- |
| Tool | weather.search | 是 | 按城市查询天气 |
| Command | weather-check | 可选 | CLI 人工查询 |
| Skill | weather-skill | 可选 | 天气查询工作流 |
| Prompt | weather-summary | 可选 | 天气摘要模板 |
| Guardrail | weather.network-policy | 可选 | 限制天气工具网络访问 |

### Activation plan

ai4j-cli extension plan weather-pack --enable \
  --expose-tool weather.search \
  --allow-command weather-check \
  --allow-skill weather-skill \
  --allow-prompt weather-summary \
  --allow-guardrail weather.network-policy \
  --strict

### CI check

ai4j-cli extension check weather-pack --enable \
  --expose-tool weather.search \
  --allow-command weather-check \
  --allow-skill weather-skill \
  --allow-prompt weather-summary \
  --allow-guardrail weather.network-policy \
  --strict

### Java

ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("weather-pack")
        .requireExplicitResourceActivation()
        .exposeTool("weather.search")
        .allowCommand("weather-check")
        .allowSkill("weather-skill")
        .allowPrompt("weather-summary")
        .allowGuardrail("weather.network-policy");

### Spring Boot

ai:
  extensions:
    enabled:
      - weather-pack
    explicit-resource-activation: true
    tools:
      expose:
        - weather.search
    commands:
      allow:
        - weather-check
    skills:
      allow:
        - weather-skill
    prompts:
      allow:
        - weather-summary
    guardrails:
      allow:
        - weather.network-policy

### 安全说明

- 是否访问网络：
- 是否读写文件：
- 是否访问数据库或外部 API：
- 需要的环境变量：
- 本地验证命令：
```

这份 README recipe 的重点不是写得长，而是让使用者能把“装了什么”和“暴露了什么”分开判断。

## 8. 常见组装错误

| 错误 | 现象 | 修正 |
| --- | --- | --- |
| 只加 Maven 依赖就以为插件生效 | `extension list` 能看到，但 Agent 没有新 tool | 还需要 `enable(...)` 和 `exposeTool(...)` |
| 只 `enable(...)` 就以为 tool 可被模型调用 | runtime 有 tool spec，但模型看不到 | 指定 `exposeTool("tool.name")` |
| 严格模式下忘记 `allowSkill(...)` | Coding Agent 看不到插件 Skill 资源 | 加 allowlist，或确认不需要该资源 |
| 把 command 当 tool | CLI 能执行，但模型不能调用 | command 是人工入口；模型调用必须注册并暴露 tool |
| 多插件组合时复制了不存在的资源名 | 启动或 snapshot fail-fast，plan 显示 `not registered by extension` | 先跑 `inspect --runtime` 和 `plan --strict` |
| 插件 README 只写“安装后可用” | 使用者不知道哪些能力会进入模型上下文 | README 必须列出资源清单和 activation recipe |

## 9. 什么时候不要用插件

下面几类需求不适合用 plugin package 解决：

- 新增模型平台：看 [Provider Extension](/docs/core-sdk/extension/provider-extension)。
- 给已有 provider 补请求字段：看 [Model Extension](/docs/core-sdk/extension/model-extension)。
- 新增顶层 SDK 能力面：看 [Service Extension](/docs/core-sdk/extension/service-extension)。
- 只调整 HTTP dispatcher 或 connection pool：看 [SPI HTTP Stack](/docs/core-sdk/extension/spi-http-stack)。

插件适合交付运行时资源，不适合替核心 SDK 做 provider 自动注册。

## 10. 推荐阅读顺序

1. [Plugin Packages](/docs/core-sdk/extension/plugin-packages)
2. 本页：Plugin Recipes
3. [Ask User Plugin](/docs/core-sdk/extension/ask-user-plugin)
4. [Plugin Author Cookbook](/docs/core-sdk/extension/plugin-author-cookbook)
5. [Agent Tools and Registry](/docs/agent/tools-and-registry)
6. [Coding Agent Tools and Approvals](/docs/coding-agent/tools-and-approvals)
