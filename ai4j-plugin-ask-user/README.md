# ai4j-plugin-ask-user

Official AI4J sample plugin package for host-mediated user clarification.

## What It Provides

- Extension id: `ask-user`
- Tool: `ask_user`
- Command: `ask-user`
- Skill: `ask-user-collaboration`
- Prompt: `ask-user-question`

The plugin returns a structured `ai4j.ask_user.request` JSON envelope. The host application decides how to render the question, collect the user's answer, and resume the agent.

It does not open a UI, read stdin, block a thread, save answers, or install dependencies.

## Enable

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("ask-user")
        .exposeTool("ask_user");
```

## Verify

```bash
mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test
```

See `docs-site/docs/core-sdk/extension/ask-user-plugin.md` for the full usage contract.
