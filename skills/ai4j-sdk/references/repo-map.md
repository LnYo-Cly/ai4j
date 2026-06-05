# AI4J SDK Repo Map

## Identity

`ai4j-sdk` is a Java 8 Maven monorepo with adjacent documentation and demo surfaces. Treat it as a multi-module SDK repository, not as a single `ai4j/` module.

## Modules

| Path | Role |
| --- | --- |
| `ai4j/` | Core SDK: provider access, Chat/Responses, RAG, MCP, vector, image, audio, realtime. |
| `ai4j-agent/` | Agent runtime, workflow, trace, memory, subagent/team orchestration. |
| `ai4j-coding/` | Coding-agent runtime, workspace-aware tools, outer loop, compaction. |
| `ai4j-cli/` | CLI, TUI, ACP host, session/runtime integration. |
| `ai4j-spring-boot-starter/` | Spring Boot auto-configuration for the core SDK. |
| `ai4j-flowgram-spring-boot-starter/` | FlowGram integration, task APIs, trace bridge, starter-side runtime support. |
| `ai4j-flowgram-demo/` | Demo backend for FlowGram starter integration. |
| `ai4j-bom/` | Version alignment BOM. |
| `docs-site/` | Docusaurus documentation site. |
| `ai4j-flowgram-webapp-demo/` | Web demo frontend surface. |

## Ownership Rules

- Core abstractions, provider clients, MCP, RAG, vector, multimodal, and realtime behavior belong in `ai4j/`.
- Agent orchestration, memory, trace, workflow, subagent, and team behavior belongs in `ai4j-agent/`.
- Coding-agent loop, workspace tools, compaction, and coding runtime behavior belongs in `ai4j-coding/`.
- CLI command parsing, TUI, ACP host, and local session wiring belongs in `ai4j-cli/`.
- Spring Boot starters should mostly bind properties and auto-configure production modules. They should not become the source of core behavior.
- Demos should show integration usage. Do not move production logic into demos just because it is easier to test there.
- Docs should explain shipped behavior. If docs need to invent behavior, treat that as a feature request first.

## Source And Test Locations

- Java production code: each Java module uses `src/main/java`.
- Java tests: each Java module uses `src/test/java`.
- Java package root should stay under `io.github.lnyocly.ai4j`.
- Tests are primarily JUnit 4.

## Common Commands

```bash
mvn -DskipTests package
mvn -pl <module> -am -DskipTests package
mvn -pl <module> -DskipTests=false test
mvn -pl <module> -Dtest=<ClassName> -DskipTests=false test
```

Module-specific test shortcuts:

```bash
mvn -pl ai4j -DskipTests=false test
mvn -pl ai4j-agent -DskipTests=false test
mvn -pl ai4j-coding -DskipTests=false test
mvn -pl ai4j-cli -DskipTests=false test
mvn -pl ai4j-spring-boot-starter -DskipTests=false test
mvn -pl ai4j-flowgram-spring-boot-starter -DskipTests=false test
```

Docs-site content or config changes should run:

```bash
cd docs-site
npm run build
```

## Source Of Truth Order

Use this order when facts conflict:

1. The user's latest instruction.
2. The checkout's `AGENTS.md`.
3. Current source code, tests, and POMs.
4. `coding-agent-harness/` context and task records.
5. Numbered `docs/` standards and historical SSoT files.
6. `docs-site/` user-facing docs.

When this Skill conflicts with the local `AGENTS.md`, follow `AGENTS.md` and treat the Skill as stale.
