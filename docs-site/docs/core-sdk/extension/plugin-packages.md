# Plugin Packages

AI4J 的 plugin package 解决的是：**第三方开发者把工具、命令、Skill、Prompt、Guardrail 等运行时资源打包成一个普通 Java 依赖，使用者通过 classpath 引入后再显式启用和暴露**。

它不是应用商店，也不是远程下载安装器。当前稳定路径是 Maven / Gradle 依赖 + `ServiceLoader` 发现 + `ExtensionRegistry` 安全门禁。

## 1. 它和 provider extension 不是一回事

AI4J 现在有两类容易混淆的扩展：

| 类型 | 解决什么 | 接入方式 | 当前状态 |
| --- | --- | --- | --- |
| Provider / model / service extension | 把新平台、新模型字段或新顶层服务接进核心 SDK | 修改核心工厂、配置和 starter 主链 | 显式代码接线 |
| Plugin package | 把工具、命令、Skill、Prompt、Guardrail 等资源交给 runtime 使用 | 第三方 jar + `ServiceLoader` + 显式 enable/expose | 独立扩展 API |

如果你要新增一个模型平台，仍然看 [Provider Extension](/docs/core-sdk/extension/provider-extension)。
如果你要给 agent 或 coding agent 增加一组可复用工具、提示词或规则，才看这一页。

## 2. 使用者路径

普通 Java 使用者的完整路径分三步。

### 2.1 引入插件依赖

插件包就是普通 jar。使用 Maven 时，把插件加入应用依赖即可：

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>weather-ai4j-plugin</artifactId>
  <version>1.0.0</version>
</dependency>
```

AI4J 不会自动远程拉取插件，也不会把插件写进你的项目依赖。依赖由你的构建系统管理。

### 2.2 发现并启用插件

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("weather-pack");
```

`discover()` 只负责从 classpath 发现实现。发现不等于启用。

### 2.3 显式暴露工具

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("weather-pack")
        .exposeTool("weather.search");
```

启用也不等于把工具交给模型。只有 `exposeTool(...)` 后，工具才会进入 agent tool registry。

### 2.4 Spring Boot 配置路径

Spring Boot 项目可以用配置完成同一件事：

```yaml
ai:
  extensions:
    enabled:
      - weather-pack
    tools:
      expose:
        - weather.search
```

starter 会自动创建两个 bean：

| Bean | 作用 |
| --- | --- |
| `ExtensionRegistry` | 保存 classpath 发现、显式启用和工具 allowlist 状态 |
| `ExtensionRuntimeSnapshot` | 保存已启用资源和已暴露工具的只读快照 |

如果配置了不存在的插件包，或者只配置 `tools.expose` 却没有启用贡献该工具的插件包，应用启动会失败。这是刻意设计的安全边界：Spring Boot 配置也不能绕过 discover / enable / expose 三段式门禁。

starter 不会自动创建 Agent 或 Coding Agent。需要 Agent 时，仍然把 `ExtensionRegistry` 传给 Agent builder：

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

### 2.5 CLI 校验路径

CLI 可以先查看 classpath 上的插件：

```bash
ai4j-cli extension list
ai4j-cli extension inspect weather-pack --runtime
ai4j-cli extension validate weather-pack
```

`validate` 会像 `inspect --runtime` 一样临时调用插件 `apply(...)` 做 runtime inspection，并把 manifest、capability 声明、工具 schema、Skill / Prompt classpath 资源和 `apply(...)` 失败情况整理成校验报告。它只报告问题，不会把工具暴露给模型，也不会执行插件 command。

如果要检查当前 classpath 上所有插件：

```bash
ai4j-cli extension validate --all
```

返回值语义：

| 结果 | 含义 |
| --- | --- |
| `status=pass` | 没有发现 error 或 warning |
| `status=warn` | 没有阻断性错误，但存在建议修正项，例如缺少 manifest `vendor` 或 command `usage` |
| `status=fail` | 存在会影响接入的错误，例如 tool schema 不可用、资源不存在或 `apply(...)` 失败 |

有 error 时 CLI 返回非零退出码。插件作者可以把它放进插件项目的本地测试或 CI；使用者也可以在引入第三方 jar 后先校验，再决定是否在宿主应用里启用。

### 2.6 CLI 命令执行路径

如果插件声明了 command，可以显式启用插件后执行：

```bash
ai4j-cli extension run --enable weather-pack weather.status beijing
```

`--enable` 是必填项。classpath 发现插件不会自动执行命令，也不会把工具暴露给模型。`extension run` 是人手动调用插件 command 的 CLI 入口；Agent / Coding Agent 的模型可见工具仍然只走 `.exposeTool(...)` 或 Spring Boot `ai.extensions.tools.expose`。

### 2.7 CLI 资源读取路径

插件声明的 Skill / Prompt 是 classpath 资源。开发者可以先用 `inspect --runtime` 查看资源名和路径，再显式启用插件读取内容：

```bash
ai4j-cli extension resource --enable weather-pack skill weather-skill
ai4j-cli extension resource --enable weather-pack prompt weather-summary
```

这个命令只打印 UTF-8 文本资源，不会执行插件工具，也不会把工具暴露给模型。它的主要用途是让插件作者和使用者确认 jar 内资源是否可被 AI4J 正确读取。

## 3. 接入 Agent

插件工具可以直接进入通用 Agent loop：

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("glm-4.5-flash")
        .extensions(registry)
        .build();
```

运行时会做两件事：

- 把已暴露的 `ExtensionToolSpec` 转成 AI4J 现有的 `Tool`
- 把已暴露的 `ExtensionToolExecutor` 路由到现有 `ToolExecutor`

Agent 主循环不用认识插件实现类。模型看到的是普通 tool schema，调用时走同一套 tool result 回传流程。

### 3.1 Guardrail 执行点

已启用插件注册的 Guardrail 会在 Agent 执行 tool call 之前评估。AI4J 当前给插件 Guardrail 的请求语义是：

| 字段 | 值 |
| --- | --- |
| `action` | `tool.execute` |
| `target` | tool name，例如 `weather.search`、`bash`、`read_file` |
| `attributes.toolName` | 同 `target` |
| `attributes.arguments` | 模型给出的原始 tool arguments 字符串 |
| `attributes.callId` | 当前 tool call id |
| `attributes.type` | 当前 tool call type，存在时传入 |

如果任意 Guardrail 返回 `GuardrailDecision.deny("reason")`，AI4J 不会调用后续 tool executor，而是把拒绝原因作为普通 `TOOL_ERROR` 回写给 Agent loop。这样插件既可以约束自己暴露的 extension tools，也可以约束宿主已经开放给 Agent 的其他工具。

## 4. 接入 Coding Agent

Coding Agent 也使用同一个入口：

```java
CodingAgent agent = CodingAgents.builder()
        .modelClient(modelClient)
        .model("glm-4.5-flash")
        .workspaceContext(workspaceContext)
        .extensions(registry)
        .build();
```

插件工具会和内置 workspace 工具一起进入 coding session：

- `read_file`
- `write_file`
- `apply_patch`
- `bash`
- 已暴露的 extension tools
- 已配置的 delegate / subagent tools

这意味着插件作者可以提供“项目扫描”“代码生成辅助”“业务规则检查”等工具，但执行权限仍然由宿主应用决定。插件不会绕过 Coding Agent 原有的 workspace、tool policy、approval 和执行边界。

已启用插件注册的 Guardrail 也会覆盖 Coding Agent 的 tool execution。它不仅能拦截已暴露的 extension tools，也能拦截内置 workspace tools，例如 `bash`、`read_file`、`write_file`、`apply_patch`，前提是宿主已经把这些工具交给当前 Coding Agent 会话。Guardrail 的判断发生在实际工具执行前；被拒绝的调用不会触发 shell、文件写入或 extension tool executor。

插件 Skill / Prompt 也会进入 Coding Agent 的上下文装配：

- 已启用插件贡献的 Skill 会被物化成只读 `SKILL.md` 文件，进入 `<available_skills>` 清单。
- 已启用插件贡献的 Prompt 会被物化成只读 Markdown 文件，进入 `<available_prompts>` 清单。
- Agent 不会在系统提示里直接塞入完整资源正文，而是先看到资源名、描述和可读路径，再按任务需要用 `read_file` 读取。
- 这些物化文件只加入 `allowedReadRoots`，不会扩大 workspace 写入权限。

这和本地 / 全局 `.ai4j/skills` 的使用方式一致：资源是给 agent 按需读取的工作流和模板，不是安装后自动执行的代码。

## 5. 开发者路径

第三方插件至少包含三个部分。

如果你还没有项目骨架，可以先用 CLI 生成一个最小 Maven 项目：

```bash
ai4j-cli extension init weather-ai4j-plugin \
  --id weather-pack \
  --package com.example.ai4j.weather \
  --name "Weather Pack"
```

这个命令只写入一个不存在或空的本地目录。它不会把插件依赖安装到宿主应用，不会拉取远程插件，也不会启用插件。生成后目录结构类似：

```text
weather-ai4j-plugin/
  pom.xml
  README.md
  src/main/java/com/example/ai4j/weather/WeatherPackExtension.java
  src/main/resources/META-INF/services/io.github.lnyocly.ai4j.extension.Ai4jExtension
  src/main/resources/skills/weather-pack/SKILL.md
  src/main/resources/prompts/weather-pack-summary.md
  src/test/java/com/example/ai4j/weather/WeatherPackExtensionTest.java
```

本地验证：

```bash
cd weather-ai4j-plugin
mvn test
```

生成的测试会调用 `ExtensionValidator`，先证明 manifest、runtime 贡献、Skill / Prompt classpath 资源和 schema contract 能被 AI4J 稳定读取。插件作者再把示例 Tool / Command / Skill / Prompt / Guardrail 替换成真实业务逻辑。

可选参数：

| 参数 | 作用 | 默认值 |
| --- | --- | --- |
| `--group-id` | Maven `groupId` | `--package` |
| `--artifact-id` | Maven `artifactId` | `--id` |
| `--version` | Maven 和 manifest 版本 | `1.0.0` |
| `--class-name` | `Ai4jExtension` 实现类名 | 从 `--id` 派生 |
| `--vendor` | manifest vendor | `example` |

### 5.1 实现 `Ai4jExtension`

```java
public final class WeatherExtension implements Ai4jExtension {
    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id("weather-pack")
                .name("Weather Pack")
                .capability(ExtensionCapability.TOOL)
                .build();
    }

    public void apply(ExtensionContext context) {
        context.tools().register(
                ExtensionToolSpec.builder()
                        .name("weather.search")
                        .description("Search weather by city")
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}},\"required\":[\"city\"]}")
                        .build(),
                new ExtensionToolExecutor() {
                    public String execute(ExtensionToolCall call) {
                        return "weather result";
                    }
                }
        );
    }
}
```

### 5.2 注册 `ServiceLoader`

在插件 jar 中加入：

```text
META-INF/services/io.github.lnyocly.ai4j.extension.Ai4jExtension
```

文件内容写实现类全名：

```text
com.example.ai4j.weather.WeatherExtension
```

### 5.3 给工具写清楚输入 schema

`ExtensionToolSpec.inputSchema(...)` 使用 JSON Schema 的核心字段：

- `type`
- `properties`
- `required`
- `description`
- `enum`
- `items`

AI4J 会把这些字段映射成现有 OpenAI-compatible tool schema。不要把大段自然语言塞进 `description` 代替结构化参数。

### 5.4 打包 Skill / Prompt 资源

插件可以把 Skill 和 Prompt 放在 `src/main/resources` 下，再在 `apply(...)` 中注册资源路径：

```java
public void apply(ExtensionContext context) {
    context.skills().register(ExtensionSkillResource.builder()
            .name("weather-skill")
            .description("Weather workflow")
            .resourcePath("skills/weather/SKILL.md")
            .build());

    context.prompts().register(ExtensionPromptResource.builder()
            .name("weather-summary")
            .description("Weather summary prompt")
            .resourcePath("prompts/weather-summary.md")
            .build());
}
```

对应 jar 结构：

```text
src/main/resources/
  skills/weather/SKILL.md
  prompts/weather-summary.md
```

资源路径默认按 classpath 查找，也可以写成 `classpath:skills/weather/SKILL.md`。资源路径不能包含 `..`，避免插件把 resource contract 伪装成任意文件读取。

### 5.5 写插件本地校验

插件作者可以直接在测试里调用公共 validator，不必依赖 CLI 文本输出：

```java
ExtensionRegistry registry = ExtensionRegistry.of(new WeatherExtension());
ExtensionValidationReport report = ExtensionValidator.validate(registry, "weather-pack");

if (!report.isValid()) {
    throw new IllegalStateException("extension validation failed: " + report.getIssues());
}
```

这套校验关注“插件包是否能被 AI4J 稳定消费”，不是第三方代码安全审计。它会检查：

- manifest 是否有 id / capability，并建议补齐 name、version、vendor
- 声明的 capability 是否真的贡献了对应资源
- tool 是否有基本可用的 input schema
- command 是否有描述和 usage
- Skill / Prompt 的 classpath 资源是否存在
- `apply(...)` 在 runtime inspection 中是否失败

它会调用插件 `apply(...)` 收集运行时贡献，但不会执行插件 command，不会把 tool 暴露给模型，也不会替宿主判断第三方插件是否可信。

## 6. 安全门禁

插件生态的默认语义是三段式门禁：

| 阶段 | 会发生什么 | 不会发生什么 |
| --- | --- | --- |
| discover | 从 classpath 找到插件 manifest | 不执行工具，不暴露给模型 |
| enable | 调用插件 `apply(...)` 注册资源 | 工具仍不会进入模型可见列表 |
| exposeTool | 指定工具名进入 agent/coding tool registry | 只暴露被点名的工具 |

这个设计故意不做“安装后自动可用”。原因很直接：tool 一旦暴露给模型，就可能触发网络、文件系统、业务系统或工作区操作。AI4J 要求宿主应用明确决定哪些工具能进入模型上下文。

Guardrail 是 enable 级资源，不需要 `exposeTool(...)`。原因是 Guardrail 不会主动给模型增加工具，也不会自动执行业务动作；它只在已经发生的 tool execution 决策点上判断是否允许继续。CLI 的 `extension run` 和 `extension resource` 是人手动触发的命令 / 资源读取路径，不属于 Agent tool loop，因此当前不走这套 `tool.execute` Guardrail。

## 7. 命名建议

插件 ID 和工具名应该稳定、可读、可冲突排查：

```text
weather-pack
weather.search
repo.scan
ticket.create
guardrail.prompt-policy
```

避免使用过宽的名字：

```text
search
run
create
check
```

工具名进入模型上下文后，也会进入执行路由。名字过宽会让调用意图和冲突排查都变困难。

## 8. 发布建议

插件包发布时至少给出：

- Maven / Gradle 坐标
- 支持的 AI4J 版本范围
- manifest id
- 注册的 tools / commands / skills / prompts / guardrails 清单
- 每个 tool 的输入 schema
- 是否触发网络、文件系统、数据库或外部 API
- 所需环境变量名，不要要求用户把密钥写进代码
- 本地 smoke test 命令，例如 `ai4j-cli extension validate <extension-id>`

AI4J 当前不维护远程插件市场。推荐做法是让插件作者用自己的包管理、README 和版本策略维护插件。

## 9. 当前边界

当前已经可用：

- `ai4j-extension-api` 定义 manifest、discovery、enable、expose 和 runtime snapshot
- `ai4j-extension-api` 提供 `ExtensionValidator`，插件作者可以复用同一套 validation report 做本地测试
- CLI 可以 `extension list / inspect / validate` 查看和校验 classpath 上的插件，也可以 `extension run --enable <id> <command>` 显式执行插件 command
- CLI 可以 `extension resource --enable <id> <skill|prompt> <name>` 显式读取插件 Skill / Prompt 资源
- Agent 可以通过 `.extensions(registry)` 调用暴露的插件工具，并在 tool execution 前应用已启用插件注册的 Guardrail
- Coding Agent 可以通过 `.extensions(registry)` 在 coding session 中调用暴露的插件工具，把已启用插件贡献的 Skill / Prompt 投影成只读可读资源，并在内置 / extension tool execution 前应用 Guardrail
- Spring Boot starter 可以通过 `ai.extensions.enabled` 和 `ai.extensions.tools.expose` 装配 `ExtensionRegistry` / `ExtensionRuntimeSnapshot`

当前不包含：

- 远程 marketplace
- CLI 自动安装插件依赖
- 运行时热加载 jar
- provider 自动注册

这些能力可以继续演进，但不应该在文档里暗示已经存在。

## 10. 推荐阅读顺序

1. [Extension 总览](/docs/core-sdk/extension/overview)
2. 本页：Plugin Packages
3. [Tools](/docs/core-sdk/tools/overview)
4. [Agent Tools and Registry](/docs/agent/tools-and-registry)
5. [Coding Agent Tools and Approvals](/docs/coding-agent/tools-and-approvals)
