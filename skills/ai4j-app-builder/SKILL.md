---
name: ai4j-app-builder
description: Use this skill when helping users build applications with AI4J in their own Java or Spring Boot projects, including first chat, streaming, tool/function calls, MCP, RAG, memory, Agent runtime, Coding Agent CLI embedding, FlowGram integration, provider configuration, dependency selection, and troubleshooting. It guides beginner-friendly app scaffolding, secure environment-variable configuration, smallest useful AI4J module selection, runnable examples, and verification steps. For AI4J repository maintenance, follow the repository AGENTS.md and coding-agent-harness files instead of this user-facing app builder skill.
---

# AI4J App Builder

## Purpose

Use this Skill as the user-side AI4J app-building copilot. It is for people who want to use AI4J in their own Java or Spring Boot application, not for maintaining the AI4J SDK repository.

The agent should reduce integration cost by choosing the smallest correct AI4J dependency, writing a minimal runnable vertical slice, keeping secrets out of code, and proving the result with a local verification step.

## Startup Workflow

1. Identify the target project type: plain Java, Maven module, Spring Boot app, existing app, demo/prototype, RAG app, MCP integration, Agent app, Coding Agent host, or FlowGram backend.
2. Inspect existing build/config files when available (`pom.xml`, `build.gradle`, `application.yml`, controller/service layout). If no project exists, ask only for the missing minimum: Java version, build tool, app type, provider/model.
3. Choose the smallest AI4J module path. Read `references/app-paths.md` when selecting dependencies or deciding between core SDK, starter, agent, coding, and FlowGram modules.
4. Add configuration through environment variables or local config placeholders. Never hardcode API keys, access tokens, private endpoints, or one-person machine paths.
5. Implement one thin end-to-end slice before adding features: dependency -> config -> service call -> returned text/result -> verification.
6. Run or provide the smallest useful verification command. Read `references/verification.md` for build/test, live-provider, and troubleshooting patterns.

## Module Selection Rules

- Use `ai4j` for plain Java, library code, CLI tools, first chat, streaming, tool/function calls, MCP client/server, embeddings, rerank, image/audio/realtime, memory, and RAG.
- Use `ai4j-spring-boot-starter` for Spring Boot apps that should inject `AiService` from `ai.*` configuration.
- Add `ai4j-agent` only when the app needs agent runtime concepts such as tool loop, memory, orchestration, trace, subagent, or team behavior.
- Add `ai4j-coding` only when the app is building a coding-agent runtime or workspace-aware automation.
- Use `ai4j-flowgram-spring-boot-starter` only for FlowGram workflow runtime/backend integration.
- Use `ai4j-bom` when multiple AI4J modules are used together.

## Output Contract

For every build/integration answer, produce:

- selected dependency path and reason
- required configuration keys, with env-var placeholders
- minimal code changes or generated files
- exact verification command and what success means
- next optional feature step, only after the first slice is runnable

## Beginner Mode

When the user is new to AI4J or Java AI SDKs:

1. Start with Chat unless the user explicitly asks for RAG, MCP, Agent, FlowGram, or Coding Agent.
2. For Plain Java first chat, use the real `Configuration -> AiService -> IChatService -> ChatCompletion -> ChatCompletionResponse` object chain.
3. Prefer one controller/service/test that the user can run immediately.
4. Distinguish local build success from live model success. If credentials are missing, stop at a compile/mock check and say which env var is needed.
5. Use the user's language. Default to Chinese when the user writes Chinese.

## Reference Loading

- Read `references/app-paths.md` for dependency/module selection and docs entry routing.
- Read `references/recipes.md` for compact implementation patterns such as first chat, Spring REST endpoint, tool call, memory, RAG, MCP, Agent, and FlowGram.
- Read `references/verification.md` before finalizing commands, tests, troubleshooting, or live-provider caveats.

## Guardrails

- Do not start with RAG, Agent, or workflow orchestration when a simple Chat call solves the user's need.
- Do not invent API names when the target project pins an older AI4J version. Inspect the dependency version, source, or docs available in the project.
- Keep Java 8-compatible examples unless the target project already uses a higher baseline.
- Keep examples provider-neutral where possible, but use OpenAI-compatible Chat as the default first slice if the user gives no provider preference.
- Do not store user secrets in generated code, docs, Git-tracked config, screenshots, or logs.
