# Engineering Standard

> Last updated: 2026-04-26

## Architecture Principles

1. **Module ownership is explicit**
   - `ai4j` owns provider access, transport/protocol models, RAG, vector, MCP, and shared SDK abstractions.
   - `ai4j-agent` owns agent runtime concerns such as workflows, memory, trace, handoff, and orchestration.
   - `ai4j-coding` owns workspace/code-execution tooling and coding-agent outer-loop behavior.
   - `ai4j-cli` owns terminal, ACP, session, and user-interaction host concerns.
   - Starter modules own Spring wiring, not duplicated core business logic.
   - Demo and docs surfaces consume the modules; they do not define core production truth.
2. **Dependency direction stays narrow**
   - Higher-level modules may depend on lower-level modules.
   - Lower-level modules must not learn about CLI, demos, or docs-site behavior.
3. **Public contract changes need owner-module tests**
   - If a public API, DTO, or protocol mapping changes, the owning module must carry the regression.
4. **Java 8 compatibility is the default**
   - New code in Java modules must stay compatible unless the task explicitly changes the baseline.
5. **Secrets and machine-specific paths stay out of shared code**
   - Credentials belong in env vars or local config, not committed source.

## Package And Module Organization

- Root packaging is Maven `pom`
- Java package root remains `io.github.lnyocly.ai4j`
- Follow existing package families where possible:
  - `platform/`
  - `agent/`
  - `coding/`
  - `cli/`
  - `mcp/`
  - `rag/`
  - `vector/`
  - `agentflow/`
  - `flowgram/`

## Style Rules

- Java: 4 spaces, no tabs for indentation
- Classes/interfaces: PascalCase
- Methods/fields: lowerCamelCase
- Constants: UPPER_SNAKE_CASE
- Match surrounding style; avoid broad mechanical reformatting

## Boundary Rules

- Core SDK behavior belongs in `ai4j`, not in starters or demos
- Auto-configuration, property binding, and container integration belong in starter modules
- CLI/TUI rendering concerns stay out of lower runtime layers
- Docs-site examples may demonstrate usage, but must not become the only place where important behavior is documented

## Error Handling

- Prefer clear, typed exceptions or stable error payloads over silent fallbacks
- Do not swallow provider, transport, or workflow errors without preserving diagnostic context
- When behavior is intentionally lossy or best-effort, state that in code comments or docs

## Logging And Trace

- Avoid logging secrets, tokens, or raw credentials
- Preserve traceability for runtime decisions, especially in agent, coding, CLI, and FlowGram surfaces
- When changing tracing or event semantics, update tests and walkthrough notes together

## Frontend And Docs Surfaces

- `docs-site/` is a Node 20 + Docusaurus surface; validate with `npm run build` and `npm run typecheck`
- `ai4j-flowgram-webapp-demo/` is a React/RSBuild surface; validate with `npm run lint`, `npm run ts-check`, and `npm run build`
- Frontend/demo changes should not introduce hidden coupling back into Java module internals

## Security Constraints

1. Do not commit real provider keys, tokens, or local secrets.
2. Do not widen code-execution or workspace-approval behavior casually; treat it as a security-sensitive surface.
3. If a change alters CLI approval, tool exposure, or remote execution semantics, add or update a regression gate and document the risk.
