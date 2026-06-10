# Plugin Author Cookbook

这一页面向第三方插件作者：你想把一组工具、命令、Skill、Prompt 或 Guardrail 打成一个普通 Java jar，让使用者通过 Maven / Gradle 引入，再由宿主显式启用和暴露。

先明确边界：AI4J 插件不是远程 marketplace，不会替使用者自动改 `pom.xml`，也不会在运行时热加载未知 jar。作者负责发布普通 Java 包；使用者负责把包放进 classpath；宿主应用负责 `discover -> enable -> exposeTool`。

## 1. 生成最小插件项目

```bash
ai4j-cli extension init weather-ai4j-plugin \
  --id weather-pack \
  --package com.example.ai4j.weather \
  --name "Weather Pack" \
  --vendor "Example"
```

生成目录包含：

```text
weather-ai4j-plugin/
  README.md
  pom.xml
  src/main/java/com/example/ai4j/weather/WeatherPackExtension.java
  src/main/resources/META-INF/services/io.github.lnyocly.ai4j.extension.Ai4jExtension
  src/main/resources/skills/weather-pack/SKILL.md
  src/main/resources/prompts/weather-pack-summary.md
  src/test/java/com/example/ai4j/weather/WeatherPackExtensionTest.java
```

这个 scaffold 的目标不是生成最终业务逻辑，而是给你一套已经连通的插件合同：

- manifest 有稳定 id、version、vendor、capability 和 config prefix。
- `ServiceLoader` 文件已经指向 extension 实现类。
- 示例 tool / command / skill / prompt / guardrail 都能被 `ExtensionValidator` 检查。
- README 已经列出发布、验证、接入和安全说明需要保留的栏目。

## 2. 先跑生成的测试

```bash
cd weather-ai4j-plugin
mvn test
```

生成的测试会直接调用：

```java
ExtensionRegistry registry = ExtensionRegistry.of(new WeatherPackExtension());
ExtensionValidationReport report = ExtensionValidator.validate(registry, "weather-pack");
```

这一步证明插件能被 AI4J 读取，不依赖真实模型、API key 或外部服务。后续你每改一次 manifest、resource path、tool schema 或 `apply(...)` 注册逻辑，都应该重新跑这个测试。

`ExtensionValidator.validate(...)` 和 `ai4j-cli extension inspect --runtime` 都会临时调用插件 `apply(...)` 来收集运行时贡献。把 `apply(...)` 保持成轻量注册函数：只注册 tool spec、executor、command handler、Skill / Prompt classpath resource 和 Guardrail。不要在 `apply(...)` 里发起网络请求、写文件、读取密钥或做长耗时初始化；这些副作用应该留在 tool executor、command handler 或宿主显式初始化阶段。

## 3. 替换示例逻辑

`WeatherPackExtension` 里默认有一个 echo tool、一个 echo command、一个 allow-all guardrail 和两个资源声明。替换时按下面顺序做，最不容易跑偏。

### 3.1 manifest 先稳定

```java
public ExtensionManifest manifest() {
    return ExtensionManifest.builder()
            .id("weather-pack")
            .name("Weather Pack")
            .version("1.0.0")
            .vendor("Example")
            .capability(ExtensionCapability.TOOL)
            .capability(ExtensionCapability.COMMAND)
            .capability(ExtensionCapability.SKILL)
            .capability(ExtensionCapability.PROMPT)
            .capability(ExtensionCapability.GUARDRAIL)
            .permission("network:weather-api")
            .configPrefix("ai4j.extensions.weather")
            .build();
}
```

插件 id 一旦被使用者依赖，就不要随意改。tool name、command name、skill name、prompt name 也一样，它们会进入宿主配置、CLI 命令和模型工具上下文。

公共 ID / name 有硬性格式规则：必须以英文字母或数字开头，只能包含英文字母、数字、点、下划线和连字符。这个规则适用于 extension id、tool name、command name、Skill name、Prompt name 和 Guardrail name。不要把 `/` 放进 command name；`/weather-check <city>` 只写在 usage 里，CLI 人工输入时可以带前导 `/`。

### 3.2 tool schema 不要只写自然语言

```java
context.tools().register(ExtensionToolSpec.builder()
                .name("weather.search")
                .description("Search current weather by city name")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"City name\"}},\"required\":[\"city\"]}")
                .build(),
        call -> searchWeather(call.getArguments()));
```

`inputSchema` 要写成 JSON object，并包含 `type`。不要只靠 description 让模型猜参数结构。

当前 validator 会检查 AI4J tool mapper 需要的最小结构：

- 根必须是合法 JSON object，且 `type` 必须是 `object`。
- `properties` 如果存在，必须是 object；每个 property value 也必须是 object。
- `required` 和 `enum` 如果存在，必须是只包含非空字符串的 array。
- `items` 如果存在，必须是 object。

它不是完整 JSON Schema 引擎，但会提前拦住“看起来有 `type`、实际不是合法 JSON 或结构不可映射”的 schema。

### 3.3 command 是人工入口

```java
context.commands().register(ExtensionCommandSpec.builder()
                .name("weather-check")
                .description("Check weather from CLI")
                .usage("/weather-check <city>")
                .build(),
        request -> searchWeather(request.getArguments()));
```

command 只通过 `ai4j-cli extension run --enable ...` 这种人工命令执行。它不会因为插件被发现就自动执行，也不会自动变成模型可见 tool。

### 3.4 Skill / Prompt 是 classpath 文本资源

```java
context.skills().register(ExtensionSkillResource.builder()
        .name("weather-skill")
        .description("Weather lookup workflow")
        .resourcePath("skills/weather-pack/SKILL.md")
        .build());

context.prompts().register(ExtensionPromptResource.builder()
        .name("weather-summary")
        .description("Weather answer prompt")
        .resourcePath("prompts/weather-pack-summary.md")
        .build());
```

资源路径必须在 jar 的 classpath 里存在。路径不要包含 `..`，不要把插件资源伪装成任意文件读取。

### 3.5 Guardrail 只做决策，不做业务动作

```java
context.guardrails().register(new ExtensionGuardrail() {
    public String name() {
        return "weather.network-policy";
    }

    public GuardrailDecision evaluate(GuardrailRequest request) {
        if ("tool.execute".equals(request.getAction())
                && "weather.search".equals(request.getTarget())) {
            return GuardrailDecision.allow();
        }
        return GuardrailDecision.allow();
    }
});
```

Guardrail 的职责是在 tool execution 前允许或拒绝，不应该顺手调用网络、写文件或修改业务状态。

## 4. 本地验证清单

插件作者至少保留三层本地验证。

| 验证 | 命令 | 证明什么 |
| --- | --- | --- |
| 单元合同 | `mvn test` | manifest、resource path、tool schema 和 `apply(...)` 基本可用 |
| CLI 校验 | `ai4j-cli extension validate weather-pack` | 插件 jar 在 AI4J CLI classpath 上能被发现和校验 |
| Runtime inspection | `ai4j-cli extension inspect weather-pack --runtime` | tool / command / skill / prompt / guardrail 实际贡献清单正确 |

读取资源和执行 command 时要显式 enable：

```bash
ai4j-cli extension resource --enable weather-pack skill weather-skill
ai4j-cli extension resource --enable weather-pack prompt weather-summary
ai4j-cli extension run --enable weather-pack weather-check beijing
```

`validate` 和 `inspect --runtime` 会临时调用 `apply(...)` 收集资源。它们不会把工具暴露给模型，也不会执行 command。

`enable(...)` 是对插件包的运行时资源做整包信任。Tool 还有下一层 `exposeTool(...)` allowlist；command、Skill、Prompt 和 Guardrail 当前没有单项 allowlist，一旦启用插件包就会进入对应的人类命令、资源读取、上下文投影或 tool execution 决策点。不要让用户把不可信插件包加入 classpath 并启用。

## 5. 给使用者的接入说明

插件 README 至少给出 Maven 坐标：

```xml
<dependency>
  <groupId>com.example.ai4j</groupId>
  <artifactId>weather-ai4j-plugin</artifactId>
  <version>1.0.0</version>
</dependency>
```

普通 Java 宿主：

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("weather-pack")
        .exposeTool("weather.search");
```

Spring Boot 宿主：

```yaml
ai:
  extensions:
    enabled:
      - weather-pack
    tools:
      expose:
        - weather.search
```

这三件事要分开写清楚：

- 引入依赖只是把 jar 放进 classpath。
- `enable(...)` 才会调用插件 `apply(...)` 注册运行时资源。
- `exposeTool(...)` 才会让指定 tool 进入模型可见工具列表。

## 6. 发布前必须声明

插件生态要好用，关键不是“装得多”，而是使用者能判断风险和兼容性。发布 README 至少写清楚：

- 支持的 AI4J 版本范围。
- manifest id、version、vendor、config prefix。
- tools / commands / skills / prompts / guardrails 清单。
- 每个 tool 的 JSON input schema 和最小调用示例。
- 是否访问网络、文件系统、数据库、消息队列、外部 API。
- 需要哪些环境变量，只写变量名，不写真实密钥。
- 本地 smoke 命令，例如 `mvn test` 和 `ai4j-cli extension validate weather-pack`。

## 7. 常见错误

| 错误 | 后果 | 修正 |
| --- | --- | --- |
| 改了 resource path 但没改 jar 内文件 | `validate` 报 classpath resource missing | 保持 `resourcePath(...)` 和 `src/main/resources` 一致 |
| tool schema 只有 description | 模型参数不稳定，validator 也可能失败 | 写结构化 JSON Schema |
| schema 文本里有 `"type"` 但不是合法 JSON | validator 报 `tool.input_schema.invalid` | 用合法 JSON object，并保持 `properties` / `required` / `enum` / `items` 形状正确 |
| command name 写成 `/weather-check` | 构造 `ExtensionCommandSpec` 失败 | name 写 `weather-check`，usage 写 `/weather-check <city>` |
| 在 `apply(...)` 里连接远程服务或读取密钥 | `validate` / `inspect --runtime` 会触发副作用 | `apply(...)` 只注册资源，把副作用放到 executor / handler |
| 以为 `enable` 只开启某个 Skill 或 command | 启用整个插件包的非 tool 资源 | 把插件包作为信任边界；tool 再用 `exposeTool` 控制模型可见性 |
| README 只写“安装后可用” | 使用者误以为自动暴露给模型 | 写清 discover / enable / exposeTool 三段式 |
| command 里做长时间阻塞交互 | CLI 和宿主行为不可预测 | command 返回结构化结果，把 UI/确认交给宿主 |
| 插件里硬编码密钥 | 泄漏风险 | 使用环境变量或宿主配置 |

## 8. 下一步阅读

1. [Plugin Packages](/docs/core-sdk/extension/plugin-packages)
2. [Ask User Plugin](/docs/core-sdk/extension/ask-user-plugin)
3. [Agent Tools and Registry](/docs/agent/tools-and-registry)
4. [Coding Agent Tools and Approvals](/docs/coding-agent/tools-and-approvals)
