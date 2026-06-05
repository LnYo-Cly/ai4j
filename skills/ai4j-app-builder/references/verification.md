# AI4J App Verification

Use the smallest command that proves the changed surface.

## Local Verification Ladder

1. Build or compile the target module.
2. Run a local unit or Spring context test.
3. Run a live-provider smoke only when required env vars are present and the user expects network access.
4. For HTTP endpoints, run one `curl` after the app starts.

## Common Commands

Maven app:

```bash
mvn test
```

Single module:

```bash
mvn -pl <module> test
```

Spring Boot app:

```bash
mvn test
mvn spring-boot:run
```

Endpoint smoke:

```bash
curl "http://localhost:8080/ai/chat?q=hello"
```

## Live Provider Rules

- Treat missing API keys as an expected setup state, not a code failure.
- Say exactly which env var is required, for example `OPENAI_API_KEY`.
- Keep provider calls opt-in if they cost money or leave the local machine.
- Do not print secret values in logs.

## Recommended Test Shapes

Plain Java:

- a unit test that constructs the request object
- a mock-webserver test when the project already has HTTP mocking
- a live smoke only behind env-var assumptions

Spring Boot:

- context-load test for starter wiring
- service test for request construction
- controller test for endpoint shape
- live endpoint smoke only after local tests pass

RAG:

- ingestion result count is greater than zero
- retrieval returns a source/citation for a known query
- rerank changes ordering only after baseline retrieval is stable

MCP:

- transport connects
- tool list is visible
- one harmless tool call returns the expected shape

Agent:

- one deterministic tool-loop path
- trace/log contains the expected step names
- memory behavior is tested separately from model quality

## Troubleshooting Triage

Dependency failure:

- verify `groupId`, `artifactId`, and version
- use `ai4j-bom` when multiple AI4J modules are combined

Spring injection failure:

- verify starter dependency is present
- verify `ai.*` config is loaded by the active profile
- verify the app scans the service/controller packages

401/403/provider auth failure:

- verify env var exists in the process that runs the app
- do not paste key values into source or chat

Network/proxy failure:

- separate provider auth from connectivity
- configure AI4J/OkHttp proxy only when the user's environment needs it

RAG bad answer:

- first check ingestion and retrieval output
- then check metadata/dataset filters
- add rerank only after retrieval quality is measurable
