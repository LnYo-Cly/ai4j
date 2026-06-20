---
sidebar_position: 9
---

# Agent Blueprint YAML

`AgentBlueprint` 是 `ai4j-agent` 的声明式单 Agent 配置模型。它解决的问题是：

> 当 Java API 能动态组装 Agent 之后，如何让一个 Agent 的模型、指令、插件、工具、memory、compact、sandbox 开关和 workflow 参数可以被保存、分享、模板化和校验？

P1-A 提供基础层：**Java DTO + YAML loader + validator + fixture tests**。P1-B 在此基础上增加 `AgentFactory`，可以在宿主显式提供 `AgentModelClient` 等依赖后把 Blueprint 转成 `AgentBuilder` / `Agent`。P1-C 进一步提供 `ai4j-cli run <agent.yaml>`，让单 Agent Blueprint 可以从终端直接运行一次。

注意：`AgentFactory` 仍然不会读取 provider key、本地 profile、插件目录或真实 sandbox。它只做确定性映射，所有敏感配置和外部系统连接都由宿主应用提供。

## 1. 适合什么场景

| 场景 | 是否适合 |
| --- | --- |
| 用 Java 代码临时创建一个非常简单的 Agent | 可以继续直接用 Java API |
| 想把 Agent 配置保存成文件，并让别人复用 | 适合 |
| 想在 UI / 模板 / 脚手架里生成 Agent 配置 | 适合 |
| 想先检查配置有没有缺字段、非法 workflow、非法 compact 阈值 | 适合 |
| 想从 YAML 创建 Agent，但模型客户端由宿主提供 | 适合，P1-B 提供 `AgentFactory` |
| 想在终端直接运行一份单 Agent YAML | 适合，P1-C 提供 `ai4j-cli run <agent.yaml> --input <task>` |
| 想接真实 VM / 容器 / 远端 sandbox | P1-A/P1-B 只声明、校验和 guard 字段，真实执行属于后续 Sandbox SPI |

## 2. 最小 YAML

```yaml
$schema: ./agent-blueprint.schema.json
version: ai4j.agent/v1
id: minimal-agent

model:
  provider: openai-compatible
  model: gpt-4.1-mini

workflow:
  mode: react
  maxTurns: 3
```

这份配置表达的是：

- 使用 `ai4j.agent/v1` Blueprint 合同。
- 当前 Agent 的稳定 ID 是 `minimal-agent`。
- 模型来自通用 `openai-compatible` provider。
- workflow 使用 `react`，最多 3 轮。

注意：这里没有 provider token。Blueprint 不负责保存密钥，密钥仍应来自环境变量、宿主配置或外部 secret store。

`$schema` 是给 IDE / YAML 插件看的 authoring hint。AI4J runtime 会忽略这个字段；真正阻断运行的仍然是 `AgentBlueprintValidator` 和 `AgentFactory` 的校验。

## 3. 完整一点的示例

```yaml
version: ai4j.agent/v1
id: coding-assistant
name: Coding Assistant

model:
  provider: openai-compatible
  profile: default
  model: gpt-4.1
  options:
    temperature: 0.2

instructions:
  system: |
    You are a careful coding agent.
  developer: Prefer small, verifiable changes.
  variables:
    language: zh-CN

plugins:
  - id: ask-user
  - id: todo
    enabled: true
    config:
      limit: 20

tools:
  - ref: coding.file
  - ref: coding.shell
    approval: safe
    config:
      timeoutSeconds: 60

session:
  memory:
    enabled: true
    scope: project
  compact:
    enabled: true
    trigger:
      contextRatio: 0.75
    strategy: structured-summary
    preserve:
      - instructions
      - open_decisions
      - changed_files
      - failed_commands
      - test_results

sandbox:
  enabled: false

workflow:
  mode: react
  maxTurns: 20
```

这份示例仍然是单 Agent Blueprint，不是 Team Blueprint，也不是 workflow graph。Team、handoff、nodes/edges、FlowGram 导出都应该放到后续阶段。

## 4. Java 加载、校验与创建 Agent

### 4.1 加载和校验

P1-A/P1-B 的核心包是：

```text
io.github.lnyocly.ai4j.agent.blueprint
```

典型使用方式：

```java
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprint;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintLoader;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintValidationIssue;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintValidationReport;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintValidator;

import java.nio.file.Paths;

AgentBlueprintLoader loader = new AgentBlueprintLoader();
AgentBlueprint blueprint = loader.load(Paths.get("agent.yaml"));

AgentBlueprintValidator validator = new AgentBlueprintValidator();
AgentBlueprintValidationReport report = validator.validate(blueprint);

if (!report.isValid()) {
    for (AgentBlueprintValidationIssue issue : report.getErrors()) {
        System.out.println(issue.getPath() + " " + issue.getCode() + " " + issue.getMessage());
    }
    throw new IllegalArgumentException("Invalid Agent Blueprint");
}
```

`load(...)` 负责把 YAML 转成 Java 对象；`validate(...)` 负责输出稳定的错误和 warning。二者分开，是为了让 UI、CLI、测试或 Factory 可以根据同一份 report 决定怎么展示或阻断。



### 4.2 使用 AgentFactory 创建 Agent

P1-B 新增 `AgentFactory` 和 `AgentFactoryContext`：

```java
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprint;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintLoader;
import io.github.lnyocly.ai4j.agent.blueprint.AgentFactory;
import io.github.lnyocly.ai4j.agent.blueprint.AgentFactoryContext;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;

AgentBlueprint blueprint = new AgentBlueprintLoader().load(Paths.get("agent.yaml"));

AgentModelClient modelClient = createModelClientFromYourHostConfig();

Agent agent = new AgentFactory().create(
    blueprint,
    AgentFactoryContext.builder()
        .modelClient(modelClient)
        .build()
);
```

这里的关键点是：`AgentFactory` 不创建 provider client。宿主应用必须自己从环境变量、配置中心、Spring Bean、CLI profile 或 secret store 中拿到配置，再显式传入 `AgentModelClient`。

P1-B 已映射的字段：

| Blueprint 字段 | Agent 映射 |
| --- | --- |
| `model.model` | `AgentBuilder.model(...)` |
| `model.options.temperature` | `AgentBuilder.temperature(...)` |
| `model.options.topP` / `top_p` | `AgentBuilder.topP(...)` |
| `model.options.maxOutputTokens` / `max_output_tokens` | `AgentBuilder.maxOutputTokens(...)` |
| `instructions.system` | `AgentBuilder.systemPrompt(...)` |
| `instructions.developer` | `AgentBuilder.instructions(...)` |
| `workflow.mode=react` | ReAct runtime |
| `workflow.mode=codeact` | CodeAct runtime |
| `workflow.maxTurns` | `AgentOptions.maxSteps` |

P1-B 不映射或不执行的字段：

| 字段 | P1-B 行为 |
| --- | --- |
| `model.profile` | 只作为宿主元数据；Factory 不读取本地 profile。 |
| `plugins[]` | 不安装、不扫描、不自动启用插件。 |
| `tools[]` | 具体工具注册表仍由宿主通过 `AgentFactoryContext` 提供。 |
| `session.memory` / `session.compact` | 不自动创建外部 memory store 或 compact strategy。 |
| `sandbox.enabled=true` | 默认报 `blueprint.sandbox.unsupported`；除非宿主显式 `allowSandboxDeclaration(true)`，也只是允许声明通过，不创建真实 sandbox。 |

### 4.3 用 CLI 运行一份 Agent YAML

P1-C 增加 `ai4j-cli run`，用于把一份单 Agent Blueprint 直接跑起来：

```bash
ai4j-cli run agent.yaml --input "结合知识库回答"
```

也可以显式覆盖运行时 provider / protocol / model：

```bash
ai4j-cli run agent.yaml \
  --input "总结这个任务" \
  --provider openai \
  --protocol responses \
  --model gpt-4.1-mini
```

这个命令的边界和 `AgentFactory` 一致：YAML 不保存 token；CLI host 从 `--api-key`、环境变量、provider profile 或工作区配置中解析运行时模型客户端，然后传给 `AgentFactoryContext`。`model.profile` 可以作为 CLI 宿主的 profile 名称使用，但不是由 `AgentFactory` 自己读取 secret。

常用参数：

| 参数 | 说明 |
| --- | --- |
| `<agent.yaml>` | Blueprint YAML 文件路径。 |
| `--input` / `--prompt` | 本次运行的用户输入，必填。 |
| `--provider` | 覆盖 YAML 中的 `model.provider`。`openai-compatible` 会按 CLI host 的 OpenAI-compatible 运行时处理。 |
| `--protocol` | `chat` 或 `responses`。 |
| `--model` | 覆盖 YAML 中的 `model.model`。 |
| `--profile` | 使用 CLI host provider profile；不存在或与显式 `--provider` 不兼容时会失败，不会静默回退 default profile。 |
| `--base-url` | OpenAI-compatible 或其他 provider 的自定义 base URL。 |
| `--allow-sandbox-declaration` | 只允许 `sandbox.enabled=true` 声明通过，不创建真实 sandbox。 |

如果 YAML 中声明了 `sandbox.enabled=true`，CLI 默认会失败并提示 `blueprint.sandbox.unsupported`。这是有意设计，避免用户误以为已经创建了 VM / 容器。

## 5. Loader 支持的入口

`AgentBlueprintLoader` 支持：

| 方法 | 说明 |
| --- | --- |
| `load(String yaml)` | 从 YAML 文本加载 |
| `load(InputStream inputStream)` | 从输入流加载 |
| `load(Path path)` | 从路径加载 |
| `load(File file)` | 从文件加载 |

Invalid YAML 会抛出 `AgentBlueprintLoadException`，错误码是 `blueprint.yaml.invalid`。异常消息只表达解析失败，不应包含 provider token 或本机敏感路径。

## 6. JSON Schema 与 IDE 提示

为了让 YAML Agent 不只停留在“复制一段示例”，AI4J 内置了 Agent Blueprint JSON Schema：

```text
ai4j/agent-blueprint.schema.json
```

你可以通过 CLI 打印：

```bash
ai4j-cli blueprint schema
```

也可以导出到项目里，供 VS Code、JetBrains YAML 插件、CI 校验或模板仓库引用：

```bash
ai4j-cli blueprint schema --out agent-blueprint.schema.json
```

然后在 `agent.yaml` 顶部写：

```yaml
$schema: ./agent-blueprint.schema.json
version: ai4j.agent/v1
id: my-agent
```

Schema 文件内部的 `$id` 使用稳定标识 `https://schemas.ai4j.dev/agent-blueprint.v1.schema.json`。在公开托管 schema 前，更推荐把 `ai4j-cli blueprint schema --out ...` 导出的本地文件作为 `$schema` 引用，避免 IDE 在离线或未部署 URL 时无法解析。

### 6.1 Schema 能帮你拦什么

| 能力 | Schema 层表现 |
| --- | --- |
| `version` | 固定为 `ai4j.agent/v1` |
| `id` | 提示 slug 规则：字母、数字、`.`、`_`、`-` |
| `model.provider` | 必填 |
| `model.model` / `model.profile` | 至少一个存在 |
| `tools[].ref` | 必填 |
| `tools[].approval` | 限制为 `always/safe/never/manual/auto/deny` |
| `session.compact.trigger.contextRatio` | 必须在 `(0, 1]` |
| `workflow.mode` | 只能是 `react` 或 `codeact` |
| `sandbox.enabled=true` | 必须声明 `provider` 或 `profile` |

### 6.2 Schema 不能替代什么

JSON Schema 是**编辑期提示**，不是运行期权限模型。下面这些仍由宿主或 runtime 决定：

- provider profile 是否存在；
- token / base URL / secret 是否可用；
- 插件是否已安装、启用、暴露；
- tool ref 是否真的存在；
- sandbox provider 是否真实创建 VM / 容器 / 浏览器环境；
- `AgentFactory` 是否允许 `sandbox.enabled=true`。

推荐验证顺序：

```text
IDE Schema 提示
  -> AgentBlueprintLoader
  -> AgentBlueprintValidator
  -> AgentFactory / CLI host policy
  -> runtime execution
```

### 6.3 Java 中读取内置 Schema

如果你要在自己的平台里给用户下载 schema：

```java
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintSchemas;

String schema = AgentBlueprintSchemas.v1JsonSchema();
AgentBlueprintSchemas.writeV1JsonSchema(Paths.get("agent-blueprint.schema.json"));
```

## 7. 字段说明

### 7.1 `version`

```yaml
version: ai4j.agent/v1
```

必填。P1-A 只支持 `ai4j.agent/v1`。未来如果扩展版本，应通过 loader / validator 做兼容策略，而不是让老字段静默变义。

### 7.2 `id` / `name`

```yaml
id: coding-assistant
name: Coding Assistant
```

`id` 必填，是配置、日志、UI、模板引用里的稳定标识。P1-A 要求它只包含字母、数字、点、下划线和中划线。

`name` 可选，是展示名。

### 7.3 `model`

```yaml
model:
  provider: openai-compatible
  profile: default
  model: gpt-4.1
  options:
    temperature: 0.2
```

| 字段 | 说明 |
| --- | --- |
| `provider` | 必填。建议使用通用 provider 名，例如 `openai-compatible`。不要把某个中转平台品牌写成 SDK 概念。 |
| `profile` | 可选。表示宿主侧配置 profile。P1-A 只保留字符串，不读取本机配置。 |
| `model` | 可选，但 `model` 和 `profile` 至少需要一个。 |
| `options` | 可选，保留给温度、topP 等模型参数。 |

### 7.4 `instructions`

```yaml
instructions:
  system: |
    You are a careful coding agent.
  developer: Prefer small, verifiable changes.
  variables:
    language: zh-CN
```

`instructions` 只是声明指令文本和变量。P1-A 不做 prompt assembly，不把变量注入模型请求；这些属于后续 Factory / runtime 组装逻辑。

### 7.5 `plugins`

```yaml
plugins:
  - id: ask-user
  - id: todo
    enabled: true
    config:
      limit: 20
```

`plugins[].id` 必填。`enabled` 和 `config` 可选。

P1-A 不负责安装插件、不扫描 classpath、不执行插件生命周期 hook。它只把插件意图表达为 DTO，并校验基本结构。

### 7.6 `tools`

```yaml
tools:
  - ref: coding.file
  - ref: coding.shell
    approval: safe
```

`tools[].ref` 必填。`approval` 用于描述工具执行策略意图，可在后续 P1-B 映射到 `AgentPermissionPolicy`。

P1-A 只对未知 approval 值给 warning，不直接拒绝。原因是 approval 策略还会和 host、CLI、sandbox、业务场景有关。

### 7.7 `session.memory`

```yaml
session:
  memory:
    enabled: true
    scope: project
```

Memory 字段用于声明是否启用 memory，以及 memory 的作用域或存储。P1-A 不创建具体 `AgentMemoryStore`。如果 `enabled: true` 但没有 `scope` 或 `store`，validator 会给 warning。

### 7.8 `session.compact`

```yaml
session:
  compact:
    enabled: true
    trigger:
      contextRatio: 0.75
    strategy: structured-summary
    preserve:
      - instructions
      - open_decisions
      - changed_files
```

`contextRatio` 必须在 `(0, 1]` 范围内。`preserve` 是 compact 时希望保留的信息类别。P1-A 只校验结构，不调用 compact runtime。

### 7.9 `sandbox`

```yaml
sandbox:
  enabled: false
```

或：

```yaml
sandbox:
  enabled: true
  provider: remote-vm
```

P1-A 不创建真实 sandbox。如果 `enabled: true`，必须提供 `provider` 或 `profile`，否则 validator 会报错。

这里的 sandbox 字段是后续 Sandbox SPI 的声明入口，不是普通 tool，也不是“打开后就自动远端执行”。真实路由应由后续 `SandboxProvider`、coding tools 和 CLI host 实现。

### 7.10 `workflow`

```yaml
workflow:
  mode: react
  maxTurns: 20
```

P1-A 支持的 `mode`：

- `react`
- `codeact`

`maxTurns` 如果填写，必须是正整数。

## 8. Validator 会检查什么

错误：

| Code | Path | 含义 |
| --- | --- | --- |
| `blueprint.version.required` | `$.version` | 缺少 version |
| `blueprint.version.unsupported` | `$.version` | version 不是 `ai4j.agent/v1` |
| `blueprint.id.required` | `$.id` | 缺少 id |
| `blueprint.id.invalid` | `$.id` | id 不符合稳定 slug 规则 |
| `blueprint.model.required` | `$.model` | 缺少 model 块 |
| `blueprint.model.provider.required` | `$.model.provider` | 缺少 provider |
| `blueprint.model.selector.required` | `$.model.model` | `model` 和 `profile` 都没填写 |
| `blueprint.plugin.id.required` | `$.plugins[n].id` | 插件缺少 id |
| `blueprint.tool.ref.required` | `$.tools[n].ref` | 工具缺少 ref |
| `blueprint.compact.contextRatio.invalid` | `$.session.compact.trigger.contextRatio` | compact ratio 不在 `(0, 1]` |
| `blueprint.workflow.mode.invalid` | `$.workflow.mode` | workflow mode 非法 |
| `blueprint.workflow.maxTurns.invalid` | `$.workflow.maxTurns` | workflow maxTurns 非正数 |
| `blueprint.sandbox.selector.required` | `$.sandbox` | sandbox 启用但没有 provider/profile |

Warning：

| Code | 含义 |
| --- | --- |
| `blueprint.field.unknown` | 未知 top-level 字段。当前保留为 warning，便于后续版本演进。 |
| `blueprint.memory.scope.warning` | memory 启用但没有 scope/store。 |
| `blueprint.tool.approval.unknown` | approval 值未知，后续 host policy 可自行解释或升级为错误。 |

## 9. 与现有 Agent API 的关系

当前 Java 运行时仍然使用 `AgentBuilder` / `Agents.react()` 等 API 构建 Agent。Blueprint 暂时不替代这些 API。

```text
P1-A:
  YAML -> AgentBlueprint DTO -> ValidationReport

P1-B:
  AgentBlueprint DTO + host AgentFactoryContext -> AgentFactory -> Agent / AgentSession
```

这样分层有两个好处：

1. 配置合同先稳定，后续 Factory 不需要重新定义字段。
2. UI、CLI、docs、测试都可以先基于 validator 给用户明确错误，而不是等运行时才失败。

## 10. 与 Approval / Permission Policy 的关系

`tools[].approval` 可以看作后续策略映射入口。比如：

```yaml
tools:
  - ref: coding.shell
    approval: safe
```

P1-B 仍不直接生成 `AgentPermissionPolicy`。Factory/host 可以在后续版本或业务层把不同 approval 值映射到：

- 允许执行
- 拒绝执行
- 要求用户审批
- 只允许在 sandbox 中执行

底层策略语义见 [Agent Approval / Permission Policy](/docs/agent/approval-permission-policy)。

## 11. 与 Sandbox 的关系

Blueprint 里的 `sandbox` 是声明，不是实现。

```text
sandbox.enabled=true
  != 已创建 VM
  != shell/file/git/browser 已经远端执行
  != 权限策略自动放开
```

真实 sandbox 仍需要后续：

- `SandboxProvider`
- `SandboxSession`
- `SandboxSpec`
- `SandboxCommand`
- `SandboxResult`
- coding tool routing
- CLI/TUI 状态展示

因此 P1-A 的 sandbox 校验只回答：“这份配置是否表达了一个可被后续 host 解析的 sandbox 选择器？”

## 12. 当前边界和下一步

当前已具备：

- `AgentBlueprint` 和字段 DTO
- `AgentBlueprintLoader`
- `AgentBlueprintValidator`
- `AgentBlueprintValidationReport`
- `AgentBlueprintValidationIssue`
- `AgentBlueprintSchemas`
- `AgentFactory` / `AgentFactoryContext` / `AgentFactoryException`
- YAML fixtures 和 deterministic JUnit tests

后续建议：

1. P2：设计 Sandbox SPI，让 `sandbox` 字段有真实 provider 绑定。
2. P3：让 `ai4j-coding` 的 file/shell/git/browser 工具感知 sandbox binding。
3. P4：在 CLI/TUI 里补齐 `ai4j` 主命令、TUI 布局、provider/model 切换、`/sandbox` 状态和更完整的回复渲染。

## 13. 排查问题

### 加载失败：`blueprint.yaml.invalid`

说明 YAML 语法不合法，先用普通 YAML linter 检查缩进、列表和冒号。

### 校验失败：`blueprint.model.selector.required`

`model.model` 和 `model.profile` 至少填写一个。只写 provider 不足以定位具体模型。

### 配置了 `sandbox.enabled=true` 但没有远端执行

这是正常的。P1-B 的 `AgentFactory` 和 P1-C 的 `ai4j-cli run` 默认都会拒绝 `sandbox.enabled=true`，避免用户误以为已经创建 VM。`--allow-sandbox-declaration` 只允许声明通过，不创建真实远端执行环境。真实远端执行需要后续 Sandbox SPI 和 coding tool routing。

### `model.profile` 写错后为什么没有用 default profile？

这是有意设计。`model.profile` 或 `--profile` 表示用户明确指定一个 CLI host profile。
如果这个 profile 不存在，或者它和显式 `--provider` 不兼容，`ai4j-cli run` 会直接失败，避免静默使用 default profile 导致模型、base URL 或密钥用错。

### 想把 token 写进 YAML

不要这样做。Blueprint 文件适合提交、分享和模板化；token 属于环境变量、宿主配置或 secret store。
