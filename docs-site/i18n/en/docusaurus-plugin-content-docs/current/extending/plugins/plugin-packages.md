---
title: "Plugin Packages"
description: "Explains the AI4J plugin package: third-party jar + ServiceLoader discovery + ExtensionRegistry three-stage gate of discover/enable/exposeTool, distinguishes how tool/command/Skill/Prompt/Guardrail resources enter the runtime, and tools are not exposed to the model by default."
tags: [concept]
---

# Plugin Packages

The AI4J plugin package solves this problem: **third-party developers package runtime resources such as tools, commands, Skills, Prompts, and Guardrails into a plain Java dependency, and consumers then inspect, enable, authorize, and expose them after pulling them in via the classpath**.

It is not an app store, nor a remote download installer. The current stable path is Maven / Gradle dependency + `ServiceLoader` discovery + `ExtensionRegistry` security gating.

## 1. It is not the same thing as a provider extension

AI4J currently has two easily confused kinds of extensions:

| Type | What it solves | How it is wired in | Current status |
| --- | --- | --- | --- |
| Provider / model / service extension | Brings a new platform, new model field, or new top-level service into the core SDK | Modifies the core factory, configuration, and starter main chain | Explicit code wiring |
| Plugin package | Hands resources such as tools, commands, Skills, Prompts, and Guardrails to the runtime for use | Third-party jar + `ServiceLoader` + explicit enable/expose | Standalone extension API |

If you want to add a new model platform, you still want [Provider Extension](/docs/extending/code-level/provider-extension).
If you want to give an agent or coding agent a reusable set of tools, prompts, or rules, then this is the page for you.

## 2. Consumer path

The complete path for a plain Java consumer has four steps.

### 2.1 Add the plugin dependency

A plugin package is just a plain jar. When using Maven, add the plugin to your application dependencies:

```xml
<dependency>
  <groupId>io.github.lnyocly</groupId>
  <artifactId>weather-ai4j-plugin</artifactId>
  <version>1.0.0</version>
</dependency>
```

AI4J does not remotely pull plugins, and it does not write plugins into your project dependencies. Dependencies are managed by your build system.

### 2.2 Discover and enable the plugin

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("weather-pack");
```

`discover()` is only responsible for discovering implementations from the classpath. Discovery is not enablement.

`enable(...)` is a whole-package trust decision on the plugin package's runtime resources: it calls the plugin `apply(...)` to register commands, Skills, Prompts, Guardrails, and tool definitions. For backward compatibility with old code, in the default mode commands, Skills, Prompts, and Guardrails enter the runtime when the plugin is enabled; tools still must go through `exposeTool(...)` before they are handed to the model.

If you are wiring in a third-party plugin, it is recommended to enable explicit resource authorization mode:

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("weather-pack")
        .requireExplicitResourceActivation()
        .allowCommand("weather.status")
        .allowSkill("weather-skill")
        .allowPrompt("weather-summary")
        .allowGuardrail("weather-policy");
```

After `requireExplicitResourceActivation()` is turned on, commands, Skills, Prompts, and Guardrails not on the allowlist will not enter `ExtensionRuntimeSnapshot`. When a non-existent resource name is configured, `snapshot()` will fail-fast, preventing the host from believing authorization succeeded.

### 2.3 Explicitly expose tools

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("weather-pack")
        .exposeTool("weather.search");
```

Enablement is not the same as handing the tool to the model. Only after `exposeTool(...)` does the tool enter the agent tool registry.

`exposeTool(...)` and `allowCommand(...)` are not the same thing:

| API | Scope of effect |
| --- | --- |
| `exposeTool("weather.search")` | Makes the specified tool enter the model-visible tool list |
| `allowCommand("weather.status")` | Allows a human / host to explicitly execute a plugin command |
| `allowSkill("weather-skill")` | Allows a Skill to be read or projected into the Coding Agent context |
| `allowPrompt("weather-summary")` | Allows a Prompt to be read or projected into the Coding Agent context |
| `allowGuardrail("weather-policy")` | Allows a Guardrail to be wired into the tool execution precheck |

### 2.4 Spring Boot configuration path

A Spring Boot project can accomplish the same thing through configuration:

```yaml
ai:
  extensions:
    enabled:
      - weather-pack
    tools:
      expose:
        - weather.search
```

To enable explicit resource authorization:

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
        - weather.status
    skills:
      allow:
        - weather-skill
    prompts:
      allow:
        - weather-summary
    guardrails:
      allow:
        - weather-policy
```

The starter automatically creates two beans:

| Bean | Purpose |
| --- | --- |
| `ExtensionRegistry` | Holds the classpath discovery, explicit enablement, resource authorization, and tool allowlist state |
| `ExtensionRuntimeSnapshot` | Holds a read-only snapshot of enabled / authorized resources and exposed tools |

If you configure a non-existent plugin package, or only configure `tools.expose` / `commands.allow` / `skills.allow` / `prompts.allow` / `guardrails.allow` without enabling the plugin package that contributes those resources, application startup will fail. This is a deliberately designed security boundary: Spring Boot configuration cannot bypass the discover / enable / allow / expose gating either.

The starter does not automatically create an Agent or Coding Agent. When you need an Agent, you still pass the `ExtensionRegistry` to the Agent builder:

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

### 2.5 CLI inspection path

The CLI can first inspect plugins on the classpath:

```bash
ai4j-cli extension list
ai4j-cli extension inspect weather-pack --runtime
ai4j-cli extension validate weather-pack
ai4j-cli extension plan weather-pack --enable \
  --expose-tool weather.search \
  --allow-command weather.status \
  --allow-skill weather-skill \
  --allow-prompt weather-summary \
  --allow-guardrail weather-policy \
  --strict
ai4j-cli extension check weather-pack --enable \
  --expose-tool weather.search \
  --allow-command weather.status \
  --allow-skill weather-skill \
  --allow-prompt weather-summary \
  --allow-guardrail weather-policy \
  --strict
```

`plan` previews a plugin's activation state under the current authorization parameters. It lists each tool, command, Skill, Prompt, and Guardrail as `active` or `inactive` along with the reason, e.g. `not exposed`, `not allowed`, `not registered by extension`. It is suited for manual inspection right after the dependency has been added to the classpath but before it is wired into an Agent.

`validate` temporarily invokes the plugin's `apply(...)` for runtime inspection, just like `inspect --runtime`, and produces a validation report covering the manifest, capability declarations, tool schemas, Skill / Prompt classpath resources, and any `apply(...)` failures. It only reports problems; it does not expose tools to the model and does not execute plugin commands.

`check` is a scriptable gate. It first runs validation; if validation has an error it returns non-zero immediately and does not proceed to the activation plan. Once validation passes, it enables the plugin and applies this run's `--expose-tool` / `--allow-*` / `--strict` parameters; as long as any explicitly requested resource has not entered the active state it returns non-zero. Plugin resources that were not requested will not cause failure, so consumers can still keep a minimal-authorization recipe.

For this reason plugin authors should keep `apply(...)` as a lightweight registration function: only register spec, executor, classpath resource, and guardrail; do not connect to remote services, make network requests, write files, read user secrets, or run long initializations inside `apply(...)`. Real side effects should go into tool executors, command handlers, or an explicit host initialization flow.

To inspect every plugin currently on the classpath:

```bash
ai4j-cli extension validate --all
```

Result semantics:

| Result | Meaning |
| --- | --- |
| `status=pass` | No errors or warnings found |
| `status=warn` | No blocking errors, but suggested fixes exist, e.g. a missing manifest `vendor` or command `usage` |
| `status=fail` | Errors that would affect wiring exist, e.g. an unusable tool schema, a non-existent resource, or an `apply(...)` failure |

When there is an error the CLI returns a non-zero exit code. Plugin authors can put this into the plugin project's local tests or CI; consumers can also validate right after pulling in a third-party jar, before deciding whether to enable it in the host application.

### 2.6 CLI command execution path

If a plugin declares commands, you can explicitly enable the plugin and then execute:

```bash
ai4j-cli extension run --enable weather-pack --allow-command weather.status weather.status beijing
```

`--enable` is required. `--allow-command` puts this run into explicit command authorization mode; without `--allow-command`, the CLI keeps its compatibility behavior: after the plugin is enabled, the commands it registered can be executed. Classpath discovery of a plugin does not automatically execute commands, nor does it expose tools to the model. `extension run` is the CLI entry point for a human to manually invoke a plugin command; the model-visible tools of an Agent / Coding Agent still only go through `.exposeTool(...)` or Spring Boot `ai.extensions.tools.expose`.

### 2.7 CLI resource reading path

Skills / Prompts declared by a plugin are classpath resources. Developers can first use `inspect --runtime` to see the resource names and paths, then explicitly enable the plugin to read the content:

```bash
ai4j-cli extension resource --enable weather-pack --allow-skill weather-skill skill weather-skill
ai4j-cli extension resource --enable weather-pack --allow-prompt weather-summary prompt weather-summary
```

`--allow-skill` / `--allow-prompt` puts this resource read into explicit resource authorization mode; without an allow parameter, the CLI keeps its compatibility behavior: after the plugin is enabled, the Skills / Prompts it registered can be read. This command only prints UTF-8 text resources; it does not execute plugin tools and does not expose tools to the model. Its main purpose is to let plugin authors and consumers confirm whether resources inside the jar can be correctly read by AI4J.

## 3. Wiring into an Agent

Plugin tools can go directly into the general Agent loop:

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("glm-4.5-flash")
        .extensions(registry)
        .build();
```

The runtime does two things:

- Converts exposed `ExtensionToolSpec` into AI4J's existing `Tool`
- Routes exposed `ExtensionToolExecutor` to the existing `ToolExecutor`

The Agent main loop does not need to know about plugin implementation classes. The model sees an ordinary tool schema, and calls go through the same tool result return flow.

### 3.1 Guardrail execution point

In the default compatibility mode, Guardrails registered by an enabled plugin are evaluated before the Agent executes a tool call. If the registry has turned on `requireExplicitResourceActivation()`, only Guardrails listed by `allowGuardrail(...)` enter the execution chain. AI4J's current request semantics for plugin Guardrails are:

| Field | Value |
| --- | --- |
| `action` | `tool.execute` |
| `target` | tool name, e.g. `weather.search`, `bash`, `read_file` |
| `attributes.toolName` | same as `target` |
| `attributes.arguments` | the raw tool arguments string supplied by the model |
| `attributes.callId` | current tool call id |
| `attributes.type` | current tool call type, passed in when present |

If any Guardrail returns `GuardrailDecision.deny("reason")`, AI4J does not invoke the subsequent tool executor; instead it writes the denial reason back to the Agent loop as an ordinary `TOOL_ERROR`. This lets a plugin constrain both the extension tools it exposes itself and other tools the host has already opened up to the Agent.

## 4. Wiring into a Coding Agent

The Coding Agent uses the same entry point:

```java
CodingAgent agent = CodingAgents.builder()
        .modelClient(modelClient)
        .model("glm-4.5-flash")
        .workspaceContext(workspaceContext)
        .extensions(registry)
        .build();
```

Plugin tools enter the coding session alongside the built-in workspace tools:

- `read_file`
- `write_file`
- `apply_patch`
- `bash`
- exposed extension tools
- configured delegate / subagent tools

This means plugin authors can provide tools such as "project scan", "code generation assist", or "business rule check", but execution permission is still decided by the host application. Plugins do not bypass the Coding Agent's existing workspace, tool policy, approval, and execution boundaries.

Guardrails registered by an authorized plugin also cover the Coding Agent's tool execution. They can intercept not only exposed extension tools but also built-in workspace tools such as `bash`, `read_file`, `write_file`, and `apply_patch`, provided the host has already handed those tools to the current Coding Agent session. The Guardrail decision happens before actual tool execution; a denied call does not trigger a shell, file write, or extension tool executor.

Plugin Skills / Prompts also enter the Coding Agent's context assembly:

- In the default compatibility mode, Skills / Prompts contributed by enabled plugins are materialized into read-only files; in explicit resource authorization mode, only resources listed by `allowSkill(...)` / `allowPrompt(...)` are materialized.
- Skills enter the `<available_skills>` list; Prompts enter the `<available_prompts>` list.
- The Agent does not stuff the full resource body into the system prompt; it first sees the resource name, description, and readable path, then reads with `read_file` as the task requires.
- These materialized files are only added to `allowedReadRoots` and do not expand workspace write permissions.

This is consistent with how local / global `.ai4j/skills` are used: resources are workflows and templates for the agent to read on demand, not code that auto-runs after installation.

## 5. Developer path

A third-party plugin has at least three parts.

If you do not yet have a project skeleton, you can first use the CLI to generate a minimal Maven project:

```bash
ai4j-cli extension init weather-ai4j-plugin \
  --id weather-pack \
  --package com.example.ai4j.weather \
  --name "Weather Pack"
```

If you are a consumer, the recommended next read is [Plugin Recipes](/docs/extending/plugins/plugin-recipes), which writes out Java, Spring Boot, CLI, and multi-plugin combinations as copy-ready wiring paths. If you are a third-party plugin author, the recommendation is to walk all the way through the [Plugin Author Cookbook](/docs/extending/plugins/plugin-author-cookbook). That page is organized by scaffold, replacing business logic, validation, release notes, and common mistakes, and is better suited than this page as a hands-on workflow.

This command only writes into a non-existent or empty local directory. It does not install plugin dependencies into the host application, does not pull remote plugins, and does not enable the plugin. The generated directory structure looks like:

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

Local validation:

```bash
cd weather-ai4j-plugin
mvn test
```

The generated test invokes `ExtensionValidator`, first proving that the manifest, runtime contributions, Skill / Prompt classpath resources, and schema contract can be stably read by AI4J. The plugin author then replaces the sample Tool / Command / Skill / Prompt / Guardrail with real business logic.

Optional parameters:

| Parameter | Purpose | Default |
| --- | --- | --- |
| `--group-id` | Maven `groupId` | `--package` |
| `--artifact-id` | Maven `artifactId` | `--id` |
| `--version` | Maven and manifest version | `1.0.0` |
| `--class-name` | `Ai4jExtension` implementation class name | derived from `--id` |
| `--vendor` | manifest vendor | `example` |

### 5.1 Implement `Ai4jExtension`

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

### 5.2 Register with `ServiceLoader`

Add into the plugin jar:

```text
META-INF/services/io.github.lnyocly.ai4j.extension.Ai4jExtension
```

Write the fully qualified name of the implementation class as the file content:

```text
com.example.ai4j.weather.WeatherExtension
```

### 5.3 Write a clear input schema for the tool

`ExtensionToolSpec.inputSchema(...)` uses the core fields of JSON Schema:

- `type`
- `properties`
- `required`
- `description`
- `enum`
- `items`

AI4J maps these fields onto the existing OpenAI-compatible tool schema. Do not stuff long natural-language text into `description` in place of structured parameters.

The validator performs a minimal structural check:

- The schema must be a valid JSON object.
- The root `type` must be a non-empty string; currently it must be `object`.
- If `properties` is present, it must be an object; each property value must also be an object.
- If `required` and `enum` are present, they must be arrays containing only non-empty strings.
- If `items` is present, it must be an object.

This is not a complete JSON Schema engine, but it is enough to catch up front schemas that AI4J's current tool mapper cannot consume reliably.

### 5.4 Package Skill / Prompt resources

A plugin can place Skills and Prompts under `src/main/resources` and then register the resource paths in `apply(...)`:

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

Corresponding jar structure:

```text
src/main/resources/
  skills/weather/SKILL.md
  prompts/weather-summary.md
```

The resource path is looked up on the classpath by default; you can also write it as `classpath:skills/weather/SKILL.md`.

:::danger
The resource path must not contain `..`, to prevent a plugin from disguising a resource contract as arbitrary file reads.
:::

### 5.5 Write plugin-local validation

Plugin authors can call the public validator directly in tests, without depending on CLI text output:

```java
ExtensionRegistry registry = ExtensionRegistry.of(new WeatherExtension());
ExtensionValidationReport report = ExtensionValidator.validate(registry, "weather-pack");

if (!report.isValid()) {
    throw new IllegalStateException("extension validation failed: " + report.getIssues());
}
```

This validation cares about "whether the plugin package can be stably consumed by AI4J", not about third-party code security auditing. It checks:

- Whether the manifest has id / capability, and recommends filling in name, version, and vendor
- Whether the declared capability actually contributes the corresponding resources
- Whether the tool has a basically usable input schema
- Whether the command has a description and usage
- Whether the Skill / Prompt classpath resources exist
- Whether `apply(...)` fails during runtime inspection

It invokes the plugin's `apply(...)` to collect runtime contributions, but it does not execute plugin commands, does not expose tools to the model, and does not decide for the host whether a third-party plugin is trustworthy.

## 6. Security gating

:::note
`manifest.permissions` is declarative metadata for host review and policy code. It is not an automatic AI4J permission engine; execution is still bounded by enable / expose / allowlist, Guardrail, and host permission policy.
:::

The default semantics of the plugin ecosystem are a three-stage gate:

| Stage | What happens | What does not happen |
| --- | --- | --- |
| discover | The plugin manifest is found on the classpath | No tools are executed, nothing is exposed to the model |
| enable | The plugin's `apply(...)` is called to register resources | Tools still do not enter the model-visible list |
| allowCommand / allowSkill / allowPrompt / allowGuardrail | In explicit resource authorization mode, allows non-tool resources to enter the runtime state | Does not let tools enter the model-visible list |
| exposeTool | The named tool enters the agent/coding tool registry | Only the named tool is exposed |

This design deliberately does not do "auto-available after installation". The reason is direct:

:::warning
Once a tool is exposed to the model, it can trigger network, filesystem, business-system, or workspace operations. AI4J requires the host application to explicitly decide which tools can enter the model context.
:::

For backward compatibility, `enable(...)` still activates commands, Skills, Prompts, and Guardrails by default. When you need a stricter boundary, call `requireExplicitResourceActivation()`, or set `ai.extensions.explicit-resource-activation=true` in Spring Boot. Once enabled, non-tool resources must enter the runtime state one by one through the corresponding `allow*` API or configuration item.

The CLI's `extension run` and `extension resource` are human-triggered command / resource read paths; they are not part of the Agent tool loop, so they currently do not go through the `tool.execute` Guardrail. They can use the same explicit resource authorization semantics through `--allow-command`, `--allow-skill`, and `--allow-prompt`.

## 7. Naming recommendations

Plugin IDs and tool names should be stable, readable, and easy to triage for conflicts:

```text
weather-pack
weather.search
repo.scan
ticket.create
guardrail.prompt-policy
```

The hard rule for public IDs / names is: they must start with an English letter or digit, and may only contain English letters, digits, dots, underscores, and hyphens. This rule applies to extension id, tool name, command name, Skill name, Prompt name, and Guardrail name.

The command name itself should not contain `/`; a form like `/weather-check <city>` should only appear in usage text. For compatibility with manual input, the CLI accepts `ai4j-cli extension run --enable weather-pack /weather-check beijing`, and internally strips the leading `/` before looking up by command name.

The classpath resource path is not a public name; it still uses path semantics, e.g. `skills/weather/SKILL.md`. Paths may contain `/` but must not contain `..`.

Avoid names that are too broad:

```text
search
run
create
check
```

After a tool name enters the model context, it also enters execution routing. Overly broad names make both call intent and conflict triage harder.

## 8. Official sample plugins

AI4J currently provides one sample plugin shipped with the SDK, and maintains a sample plugin in a separate repository:

| Artifact | Release boundary | Extension id | Capabilities | Docs |
| --- | --- | --- | --- | --- |
| `ai4j-plugin-ask-user` | ai4j-sdk reactor / BOM | `ask-user` | tool + command + Skill + Prompt | [Ask User Plugin](/docs/extending/plugins/ask-user-plugin) |
| `ai4j-plugin-dynamic-workflow` | separate repository, released independently | `dynamic-workflow` | tool + command + Skill + Prompt | [Dynamic Workflow Plugin](/docs/extending/plugins/dynamic-workflow-plugin) |

These samples do not exist to replace third-party plugins; they give plugin authors a compilable, testable, `ServiceLoader`-discoverable reference implementation. The points they highlight are:

- Official plugins are also just plain Maven jars.
- Enabling a plugin does not automatically expose tools to the model.
- tools / commands can return structured envelopes the host can recognize.
- Skills / Prompts can be distributed with the jar, then read on demand by the Agent / Coding Agent.

## 9. Release recommendations

When releasing a plugin package, at minimum provide:

- Maven / Gradle coordinates
- The supported AI4J version range
- The manifest id
- A list of registered tools / commands / skills / prompts / guardrails
- The input schema for each tool
- Whether it triggers network, filesystem, database, or external API access
- Required environment variable names; do not require users to hardcode secrets in code
- A local smoke test command, e.g. `ai4j-cli extension validate <extension-id>` and `ai4j-cli extension check <extension-id> --enable ... --strict`

AI4J does not currently maintain a remote plugin marketplace. The recommended practice is for plugin authors to maintain plugins through their own package management, README, and versioning strategy.

## 10. Current boundaries

Currently available:

- `ai4j-extension-api` defines manifest, discovery, enable, expose, and runtime snapshot
- `ai4j-extension-api` provides `ExtensionActivationPlan`, and supports explicit allowlists for commands, Skills, Prompts, and Guardrails
- Extension-owned Skill / Prompt classpath resources are read through the owning plugin classloader in strict paths, so a missing plugin resource is not hidden by another jar with the same path.
- `ai4j-extension-api` performs format validation when constructing public IDs / names, and checks the basic JSON structure of tool schemas in `ExtensionValidator`
- `ai4j-plugin-ask-user` ships with the SDK, demonstrating the host-mediated user-questioning tool / command / Skill / Prompt
- `ai4j-plugin-dynamic-workflow` is maintained as a separate plugin repository, demonstrating the host-mediated dynamic workflow request envelope and scripted Prompt / Skill
- `ai4j-extension-api` provides `ExtensionValidator`; plugin authors can reuse the same validation report for local testing
- The CLI can `extension list / inspect / plan / validate / check` to view, preview, validate, and gate plugins on the classpath, and can `extension run --enable <id> [--allow-command <name>] <command>` to explicitly execute a plugin command
- The CLI can `extension resource --enable <id> [--allow-skill <name>|--allow-prompt <name>] <skill|prompt> <name>` to explicitly read a plugin Skill / Prompt resource
- An Agent can invoke exposed plugin tools via `.extensions(registry)`, and apply Guardrails registered by enabled plugins before tool execution
- A Coding Agent can invoke exposed plugin tools in a coding session via `.extensions(registry)`, project Skills / Prompts contributed by enabled plugins as read-only readable resources, and apply Guardrails before built-in / extension tool execution
- The Spring Boot starter can wire `ExtensionRegistry` / `ExtensionRuntimeSnapshot` through `ai.extensions.enabled`, `ai.extensions.tools.expose`, and `ai.extensions.{commands,skills,prompts,guardrails}.allow`

Not currently included:

- A remote marketplace
- CLI auto-installation of plugin dependencies
- Runtime hot-loading of jars
- Auto-registration of providers

These capabilities may continue to evolve, but the docs should not imply they already exist.

## 11. Recommended reading order

1. [Extension Overview](/docs/extending/overview)
2. This page: Plugin Packages
3. [Plugin Recipes](/docs/extending/plugins/plugin-recipes)
4. [Ask User Plugin](/docs/extending/plugins/ask-user-plugin)
5. [Dynamic Workflow Plugin](/docs/extending/plugins/dynamic-workflow-plugin)
6. [Plugin Author Cookbook](/docs/extending/plugins/plugin-author-cookbook)
7. [Tools](/docs/capabilities/tools/overview)
8. [Agent Tools and Registry](/docs/agent/tools-and-registry)
9. [Coding Agent Tools and Approvals](/docs/products/coding-agent/tools-and-approvals)
