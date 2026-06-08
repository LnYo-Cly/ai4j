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

使用者的完整路径分三步。

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

## 5. 开发者路径

第三方插件至少包含三个部分。

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

## 6. 安全门禁

插件生态的默认语义是三段式门禁：

| 阶段 | 会发生什么 | 不会发生什么 |
| --- | --- | --- |
| discover | 从 classpath 找到插件 manifest | 不执行工具，不暴露给模型 |
| enable | 调用插件 `apply(...)` 注册资源 | 工具仍不会进入模型可见列表 |
| exposeTool | 指定工具名进入 agent/coding tool registry | 只暴露被点名的工具 |

这个设计故意不做“安装后自动可用”。原因很直接：tool 一旦暴露给模型，就可能触发网络、文件系统、业务系统或工作区操作。AI4J 要求宿主应用明确决定哪些工具能进入模型上下文。

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
- 本地 smoke test 命令

AI4J 当前不维护远程插件市场。推荐做法是让插件作者用自己的包管理、README 和版本策略维护插件。

## 9. 当前边界

当前已经可用：

- `ai4j-extension-api` 定义 manifest、discovery、enable、expose 和 runtime snapshot
- CLI 可以 `extension list / inspect` 查看 classpath 上的插件
- Agent 可以通过 `.extensions(registry)` 调用暴露的插件工具
- Coding Agent 可以通过 `.extensions(registry)` 在 coding session 中调用暴露的插件工具

当前不包含：

- 远程 marketplace
- CLI 自动安装插件依赖
- 运行时热加载 jar
- provider 自动注册
- Spring Boot 配置化插件装配

这些能力可以继续演进，但不应该在文档里暗示已经存在。

## 10. 推荐阅读顺序

1. [Extension 总览](/docs/core-sdk/extension/overview)
2. 本页：Plugin Packages
3. [Tools](/docs/core-sdk/tools/overview)
4. [Agent Tools and Registry](/docs/agent/tools-and-registry)
5. [Coding Agent Tools and Approvals](/docs/coding-agent/tools-and-approvals)
