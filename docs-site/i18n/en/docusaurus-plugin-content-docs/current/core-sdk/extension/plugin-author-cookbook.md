---
title: "Plugin Author Cookbook"
description: "A hands-on guide for third-party plugin authors: use the CLI to generate a minimal plugin project, stabilize the manifest and public ID naming rules, write a structured tool input schema, keep apply() as a lightweight registration function, and complete manifest/resource/schema validation and pre-release declarations."
tags: [how-to]
---

# Plugin Author Cookbook

This page targets third-party plugin authors: you want to package a set of tools, commands, Skills, Prompts, or Guardrails as a plain Java jar that consumers pull in via Maven / Gradle, then explicitly enable and expose through the host.

Start with the boundaries: an AI4J plugin is not a remote marketplace. It will not rewrite a consumer's `pom.xml` on its own, nor will it hot-load unknown jars at runtime. The author is responsible for publishing a plain Java package; the consumer is responsible for putting it on the classpath; the host application is responsible for `discover -> enable -> exposeTool`.

## 1. Generate a minimal plugin project

```bash
ai4j-cli extension init weather-ai4j-plugin \
  --id weather-pack \
  --package com.example.ai4j.weather \
  --name "Weather Pack" \
  --vendor "Example"
```

The generated directory contains:

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

The goal of this scaffold is not to produce the final business logic, but to give you an already-wired plugin contract:

- The manifest has a stable id, version, vendor, capability, and config prefix.
- The `ServiceLoader` file already points at the extension implementation class.
- The sample tool / command / skill / prompt / guardrail can all be inspected by `ExtensionValidator`.
- The README already lists the sections to keep for publishing, validation, integration, and security notes.

## 2. Run the generated test first

```bash
cd weather-ai4j-plugin
mvn test
```

The generated test calls directly into:

```java
ExtensionRegistry registry = ExtensionRegistry.of(new WeatherPackExtension());
ExtensionValidationReport report = ExtensionValidator.validate(registry, "weather-pack");
```

This step proves the plugin can be read by AI4J, without depending on a real model, API key, or external service. Every time you change the manifest, a resource path, a tool schema, or the `apply(...)` registration logic, you should re-run this test.

Both `ExtensionValidator.validate(...)` and `ai4j-cli extension inspect --runtime` temporarily invoke the plugin's `apply(...)` to collect runtime contributions. Keep `apply(...)` a lightweight registration function: it only registers tool specs, executors, command handlers, Skill / Prompt classpath resources, and Guardrails.

:::warning
Do not issue network requests, write files, read secrets, or perform long-running initialization inside `apply(...)`. These side effects belong in the tool executor, the command handler, or an explicit host initialization phase.
:::

## 3. Replace the sample logic

`WeatherPackExtension` ships with an echo tool, an echo command, an allow-all guardrail, and two resource declarations by default. Follow the order below to replace them with the least chance of going off track.

### 3.1 Stabilize the manifest first

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

Once consumers depend on the plugin id, do not change it freely. The same applies to tool names, command names, skill names, and prompt names — they flow into host configuration, CLI commands, and the model's tool context.

Public IDs / names follow strict format rules: they must start with an ASCII letter or digit, and may contain only ASCII letters, digits, dots, underscores, and hyphens. This rule applies to the extension id, tool name, command name, Skill name, Prompt name, and Guardrail name. Do not put `/` in a command name; write `/weather-check <city>` only in the usage string — when typed by a human at the CLI, a leading `/` is allowed.

### 3.2 Don't write the tool schema in natural language only

```java
context.tools().register(ExtensionToolSpec.builder()
                .name("weather.search")
                .description("Search current weather by city name")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"City name\"}},\"required\":[\"city\"]}")
                .build(),
        call -> searchWeather(call.getArguments()));
```

Write `inputSchema` as a JSON object, and include `type`. Do not rely on the description alone and let the model guess the parameter shape.

The validator currently checks the minimal structure that the AI4J tool mapper requires:

- The root must be a valid JSON object, and `type` must be `object`.
- If `properties` is present, it must be an object; each property value must also be an object.
- If `required` and `enum` are present, they must be arrays containing only non-empty strings.
- If `items` is present, it must be an object.

It is not a full JSON Schema engine, but it catches schemas that "look like they have a `type`" but are actually not valid JSON or whose structure cannot be mapped.

### 3.3 Commands are the human entry point

```java
context.commands().register(ExtensionCommandSpec.builder()
                .name("weather-check")
                .description("Check weather from CLI")
                .usage("/weather-check <city>")
                .build(),
        request -> searchWeather(request.getArguments()));
```

A command is only executed through a human-driven invocation like `ai4j-cli extension run --enable ...`. It does not run automatically just because the plugin was discovered, and it does not automatically become a model-visible tool.

### 3.4 Skills / Prompts are classpath text resources

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

:::danger
The resource path must exist inside the jar's classpath. Do not include `..` in the path, and do not disguise plugin resources as arbitrary file reads.
:::

### 3.5 Guardrails make decisions only — they don't take business actions

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

A Guardrail's responsibility is to allow or deny before tool execution. It should not, as a side effect, call the network, write files, or mutate business state.

## 4. Local validation checklist

Plugin authors should keep at least three layers of local validation.

| Validation | Command | What it proves |
| --- | --- | --- |
| Unit contract | `mvn test` | The manifest, resource paths, tool schema, and `apply(...)` are basically usable |
| CLI validation | `ai4j-cli extension validate weather-pack` | The plugin jar can be discovered and validated on the AI4J CLI classpath |
| Runtime inspection | `ai4j-cli extension inspect weather-pack --runtime` | The actual contribution list of tools / commands / skills / prompts / guardrails is correct |
| Activation plan | `ai4j-cli extension plan weather-pack --enable --strict ...` | Which resources the consumer plans to enable, expose, and authorize |
| Check gate | `ai4j-cli extension check weather-pack --enable --strict ...` | Can be put into CI or a pre-release check; returns non-zero when validation fails or a requested resource is inactive |

Reading resources and executing a command require explicit `enable`:

```bash
ai4j-cli extension plan weather-pack --enable \
  --expose-tool weather.search \
  --allow-command weather-check \
  --allow-skill weather-skill \
  --allow-prompt weather-summary \
  --allow-guardrail weather.network-policy \
  --strict
ai4j-cli extension check weather-pack --enable \
  --expose-tool weather.search \
  --allow-command weather-check \
  --allow-skill weather-skill \
  --allow-prompt weather-summary \
  --allow-guardrail weather.network-policy \
  --strict
ai4j-cli extension resource --enable weather-pack --allow-skill weather-skill skill weather-skill
ai4j-cli extension resource --enable weather-pack --allow-prompt weather-summary prompt weather-summary
ai4j-cli extension run --enable weather-pack --allow-command weather-check weather-check beijing
```

`validate` and `inspect --runtime` temporarily call `apply(...)` to collect resources. They do not expose tools to the model, and they do not execute commands.

`plan` is a human preview: even if a resource is not active, it prints the reason and returns 0. `check` is a gate: it runs validation first, and fails immediately on any validation error; once validation passes, it checks the activation status for the resources requested via `--expose-tool` / `--allow-*` in this run. Only resources you explicitly request can fail `check`; plugin resources you did not request are not force-enabled.

`enable(...)` still defaults to granting blanket trust to the plugin package's runtime resources, for backward compatibility with older hosts. A stricter wiring option is to have consumers turn on explicit resource authorization: in plain Java use `requireExplicitResourceActivation()`, in Spring Boot use `ai.extensions.explicit-resource-activation=true`. Once enabled, commands, Skills, Prompts, and Guardrails must be brought into the runtime state item by item via `allowCommand(...)`, `allowSkill(...)`, `allowPrompt(...)`, `allowGuardrail(...)`, or the corresponding Spring configuration.

When publishing the README, plugin authors should provide a copy-pasteable set of check commands, for example:

```bash
ai4j-cli extension plan weather-pack --enable \
  --expose-tool weather.search \
  --allow-command weather-check \
  --allow-skill weather-skill \
  --allow-prompt weather-summary \
  --allow-guardrail weather.network-policy \
  --strict
ai4j-cli extension check weather-pack --enable \
  --expose-tool weather.search \
  --allow-command weather-check \
  --allow-skill weather-skill \
  --allow-prompt weather-summary \
  --allow-guardrail weather.network-policy \
  --strict
```

These commands do not expose tools to the model and do not execute commands. `plan` lets consumers see whether each resource will be `active` or `inactive`, plus the reason when it is inactive; `check` gives CI / pre-release smoke tests a clear pass-or-fail exit code.

## 5. Integration notes for consumers

The plugin README should at least provide the Maven coordinates:

```xml
<dependency>
  <groupId>io.github.lnyocly</groupId>
  <artifactId>weather-ai4j-plugin</artifactId>
  <version>1.0.0</version>
</dependency>
```

Plain Java host:

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("weather-pack")
        .requireExplicitResourceActivation()
        .allowCommand("weather-check")
        .allowSkill("weather-skill")
        .allowPrompt("weather-summary")
        .allowGuardrail("weather.network-policy")
        .exposeTool("weather.search");
```

Spring Boot host:

```yaml
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
```

These three concerns should be documented separately and clearly:

- Adding the dependency only places the jar on the classpath.
- `enable(...)` is what invokes the plugin's `apply(...)` to register runtime resources.
- `allow*` decides whether non-tool resources enter the explicitly authorized runtime state.
- `exposeTool(...)` is what makes a given tool enter the model-visible tool list.

## 6. Required declarations before publishing

For a plugin ecosystem to be usable, the key is not "how much is installed" but whether consumers can judge risk and compatibility. The published README should state at minimum:

- The supported AI4J version range.
- The manifest id, version, vendor, and config prefix.
- The list of tools / commands / skills / prompts / guardrails.
- Each tool's JSON input schema and a minimal invocation example.
- Whether it accesses the network, filesystem, database, message queue, or external APIs.
- Which environment variables are required — names only, never real secrets.
- Local smoke commands, e.g. `mvn test`, `ai4j-cli extension validate weather-pack`, and `ai4j-cli extension check weather-pack --enable ... --strict`.

## 7. Common mistakes

| Mistake | Consequence | Fix |
| --- | --- | --- |
| Changed the resource path but not the file inside the jar | `validate` reports classpath resource missing | Keep `resourcePath(...)` consistent with `src/main/resources` |
| Tool schema has only a description | Model parameters are unstable; the validator may also fail | Write a structured JSON Schema |
| Schema text contains `"type"` but is not valid JSON | Validator reports `tool.input_schema.invalid` | Use a valid JSON object, and keep `properties` / `required` / `enum` / `items` correctly shaped |
| Command name written as `/weather-check` | Constructing `ExtensionCommandSpec` fails | Use `weather-check` as the name; put `/weather-check <city>` in usage |
| Connecting to a remote service or reading secrets inside `apply(...)` | `validate` / `inspect --runtime` trigger the side effect | `apply(...)` only registers resources; push side effects into the executor / handler |
| Assuming `enable` turns on only a specific Skill or command | The default compatibility mode enables the whole plugin package's non-tool resources | Use `requireExplicitResourceActivation()` and `allow*` to authorize item by item |
| README only says "usable after install" | Consumers wrongly assume it is auto-exposed to the model | Document the discover / enable / exposeTool three-stage flow |
| Long-blocking interactive logic inside a command | CLI and host behavior become unpredictable | Commands return structured results; leave UI / confirmation to the host |
| Hardcoded secrets in the plugin | Leakage risk | Use environment variables or host configuration |

## 8. Further reading

1. [Plugin Packages](/docs/core-sdk/extension/plugin-packages)
2. [Ask User Plugin](/docs/core-sdk/extension/ask-user-plugin)
3. [Agent Tools and Registry](/docs/agent/tools-and-registry)
4. [Coding Agent Tools and Approvals](/docs/coding-agent/tools-and-approvals)
