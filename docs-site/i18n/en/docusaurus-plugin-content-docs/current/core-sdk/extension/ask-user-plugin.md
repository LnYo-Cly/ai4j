---
title: "Ask User Plugin"
description: "Explains the ai4j-plugin-ask-user sample plugin: it expresses the human confirmation an Agent needs as a host-mediated JSON envelope, contributes the ask_user tool and command/Skill/Prompt resources, and itself opens no UI, reads no stdin, and does not block — the host decides presentation and recovery."
tags: [integration]
---

# Ask User Plugin

`ai4j-plugin-ask-user` is the official sample plugin for AI4J. It demonstrates one thing: **a plugin can express the human confirmation or supplementary information an Agent needs as a structured request, leaving the host application responsible for presentation, collecting the answer, and resuming execution**.

It is not a UI component, nor does it block on stdin. It only contributes tool, command, Skill, and Prompt resources.

## 1. What it is good for

An Agent should not keep guessing in these scenarios:

- A business rule is missing a key parameter
- Multiple execution paths are reasonable but have different consequences
- A change to a file, interface, database, or configuration requires the user to choose
- A default exists, but it is better to let the host confirm before continuing

The `ask-user` plugin turns these questions into a unified JSON envelope. The host can render it as a CLI prompt, a web form, an IDE popup, a message-queue event, or an approval task.

## 2. Add the dependency

If you already use `ai4j-bom`:

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-plugin-ask-user</artifactId>
</dependency>
```

Without the BOM:

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-plugin-ask-user</artifactId>
  <version>${ai4j.version}</version>
</dependency>
```

## 3. Enable and expose

Plain Java:

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("ask-user")
        .exposeTool("ask_user");
```

If the host also wants to authorize the command, Skill, and Prompt item by item:

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("ask-user")
        .requireExplicitResourceActivation()
        .allowCommand("ask-user")
        .allowSkill("ask-user-collaboration")
        .allowPrompt("ask-user-question")
        .exposeTool("ask_user");
```

Spring Boot:

```yaml
ai:
  extensions:
    enabled:
      - ask-user
    tools:
      expose:
        - ask_user
```

Spring Boot strict authorization:

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

With only `enable("ask-user")`, in compatibility mode the Skill, Prompt, and command are registered into the runtime snapshot, but `ask_user` does not enter the model-visible tool list. Only after `exposeTool("ask_user")` can an Agent / Coding Agent invoke it. Once `requireExplicitResourceActivation()` or `ai.extensions.explicit-resource-activation=true` is on, the Skill, Prompt, and command additionally require the corresponding `allow*` configuration to enter the runtime state.

## 4. What capabilities it contributes

| Type | Name | Description |
| --- | --- | --- |
| Extension id | `ask-user` | Plugin ID used for classpath discovery and enable |
| Tool | `ask_user` | Structured questioning tool that an Agent can call |
| Command | `ask-user` | Entry point for a questioning request triggered manually by the CLI / host |
| Skill | `ask-user-collaboration` | Workflow description for when to ask the user and how to phrase the question |
| Prompt | `ask-user-question` | Prompt template that generates a user question |

The manifest also declares:

```text
vendor: ai4j
permission: ui.prompt
configPrefix: ai4j.extensions.ask-user
```

:::warning
`permission: ui.prompt` is a host-policy hint. AI4J does not automatically enforce manifest permissions, open UI, read stdin, or grant network access from this field. Runtime exposure still depends on enable / expose / allowlist and host policy.
:::

`ui.prompt` indicates that the plugin will produce questions that the host needs to present to the user, but the plugin itself opens no window, reads no console, and accesses no network.

## 5. Tool input

The input schema of `ask_user`:

```json
{
  "type": "object",
  "properties": {
    "question": {
      "type": "string",
      "description": "The exact question to show to the user"
    },
    "reason": {
      "type": "string",
      "description": "Why the agent needs this answer before continuing"
    },
    "choices": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "Optional short choices the host can render"
    },
    "defaultChoice": {
      "type": "string",
      "description": "Optional recommended default choice"
    },
    "blocking": {
      "type": "boolean",
      "description": "Whether the agent should pause until the host receives an answer"
    }
  },
  "required": [
    "question"
  ]
}
```

Example call arguments:

```json
{
  "question": "Should I create a migration file for this schema change?",
  "reason": "The code change is safe only if the database migration is part of the same release.",
  "choices": [
    "Create migration",
    "Code only",
    "Stop"
  ],
  "defaultChoice": "Create migration",
  "blocking": true
}
```

## 6. Tool output

The plugin returns a JSON envelope the host can recognize:

```json
{
  "type": "ai4j.ask_user.request",
  "source": "tool",
  "tool": "ask_user",
  "status": "pending_user_input",
  "hostAction": "render_question_to_user",
  "blocking": "host_decides",
  "argumentsRaw": "{\"question\":\"Should I create a migration file for this schema change?\",\"reason\":\"The code change is safe only if the database migration is part of the same release.\",\"choices\":[\"Create migration\",\"Code only\",\"Stop\"],\"defaultChoice\":\"Create migration\",\"blocking\":true}"
}
```

`argumentsRaw` is the raw argument string the model passed to the tool. The plugin does not parse the JSON here, so that the envelope itself is always valid JSON; if the host needs the structured fields, it can parse `argumentsRaw` itself according to the `ask_user` schema.

The final semantics of `blocking` are decided by the host. The AI4J plugin layer makes no assumption about whether it is currently running in a CLI, on the web, in an IDE, or inside a server-side queue.

## 7. Command path

Before wiring it in, you can review the enablement plan:

```bash
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
```

If the host wants to trigger via a CLI command:

```bash
ai4j-cli extension run --enable ask-user --allow-command ask-user ask-user "Should I continue with this file rewrite?"
```

The `source` of the returned envelope will be `command`:

```json
{
  "type": "ai4j.ask_user.request",
  "source": "command",
  "command": "ask-user",
  "status": "pending_user_input",
  "hostAction": "render_question_to_user",
  "blocking": "host_decides",
  "arguments": {
    "question": "Should I continue with this file rewrite?"
  },
  "argumentsRaw": "Should I continue with this file rewrite?"
}
```

`extension run` explicitly executes a command, either by a human or by the host; it does not expose the tool to the model.

## 8. Reading resources

Built-in Skill of the plugin:

```bash
ai4j-cli extension resource --enable ask-user --allow-skill ask-user-collaboration skill ask-user-collaboration
```

Built-in Prompt of the plugin:

```bash
ai4j-cli extension resource --enable ask-user --allow-prompt ask-user-question prompt ask-user-question
```

These commands still require `--enable ask-user`. `--allow-skill` / `--allow-prompt` put this read into explicit resource authorization mode; if the host uses only compatibility mode, omitting the allow arguments still lets you read the resources registered by the enabled plugin. After a Coding Agent enables the plugin, it can also materialize these resources as read-only context resources for the Agent to read on demand; in strict mode only the allowed resources are materialized.

## 9. Local validation

From the source repository you can run directly:

```bash
mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test
```

This verifies:

- Whether the manifest is declared completely
- Whether the ServiceLoader can discover the plugin
- Whether `ExtensionValidator` passes
- Whether the tool / command returns a stable envelope
- Whether the Skill / Prompt classpath resources are readable

## 10. Current boundaries

`ai4j-plugin-ask-user` currently does not do these things:

- Does not open a UI
- Does not read stdin
- Does not block waiting for user input
- Does not persist the answer
- Does not decide how the Agent resumes execution
- Does not make approval permission decisions on behalf of the host

These are the responsibility of the host application, CLI/TUI, web UI, IDE plugin, or a future higher-level runtime. The plugin is only responsible for turning "need to ask the user" into a discoverable, verifiable, routable AI4J extension package.

## 11. Keep assembling

If you want to wire `ask-user` together with other third-party plugins, continue to [Plugin Recipes](/docs/core-sdk/extension/plugin-recipes). It gives the complete configuration shapes for plain Java, Spring Boot, CLI checks, and multi-plugin combinations.
