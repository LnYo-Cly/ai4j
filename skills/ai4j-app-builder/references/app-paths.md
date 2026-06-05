# AI4J App Paths

Use this file when deciding which AI4J module, documentation path, and verification path fits the user's application.

## Dependency Choice

| User goal | Primary dependency | Add-on dependency | First verification |
| --- | --- | --- | --- |
| Plain Java first chat, streaming, tool call, embedding, rerank, image/audio/realtime | `io.github.lnyo-cly:ai4j` | none | `mvn test` or a small `main`/JUnit smoke |
| Spring Boot REST API or existing Spring app | `io.github.lnyo-cly:ai4j-spring-boot-starter` | `ai4j-bom` when using more modules | `mvn test`, then `mvn spring-boot:run` or one endpoint curl |
| Multi-module app using core + agent/coding/starter | `io.github.lnyo-cly:ai4j-bom` in dependencyManagement | selected modules without explicit versions | `mvn -pl <app> test` |
| RAG knowledge-base app | `ai4j` or starter | vector DB client/config already provided by AI4J path | ingestion smoke + retrieval smoke |
| MCP client/server integration | `ai4j` or starter | none unless app also needs agent orchestration | local MCP handshake/tool-list smoke |
| Agent runtime app | `ai4j-agent` | `ai4j` is transitive in AI4J repo modules; user apps can still declare BOM | local tool-loop or runtime unit test |
| Coding-agent host or workspace automation | `ai4j-coding` | `ai4j-agent`, `ai4j-cli` only when needed | workspace-safe dry-run or CLI help smoke |
| FlowGram backend integration | `ai4j-flowgram-spring-boot-starter` | Spring Boot web/runtime dependencies as needed | Spring context load + one task API smoke |

## Maven Patterns

Single-module plain Java:

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j</artifactId>
  <version>${ai4j.version}</version>
</dependency>
```

Spring Boot:

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-spring-boot-starter</artifactId>
  <version>${ai4j.version}</version>
</dependency>
```

Multiple AI4J modules:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.lnyo-cly</groupId>
      <artifactId>ai4j-bom</artifactId>
      <version>${ai4j.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Then declare only the modules needed by the app:

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-agent</artifactId>
</dependency>
```

## Configuration Path

For Spring Boot, prefer `application.yml` with environment-variable placeholders:

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
```

For plain Java, build `Configuration` in code but read secrets from `System.getenv(...)`.

## Documentation Routing

When the AI4J docs are available, route users to the smallest matching entry:

| Need | Docs path |
| --- | --- |
| New user path selection | `docs-site/docs/start-here/choose-your-path.md` |
| First Java chat | `docs-site/docs/start-here/quickstart-java.md` and `docs-site/docs/start-here/first-chat.md` |
| First Spring Boot chat | `docs-site/docs/start-here/quickstart-spring-boot.md` |
| Tool/function call | `docs-site/docs/start-here/first-tool-call.md` |
| Spring Boot details | `docs-site/docs/spring-boot/` |
| RAG solution | `docs-site/docs/solutions/rag-ingestion-vector-store.md` |
| MCP | `docs-site/docs/mcp/` |
| Agent runtime | `docs-site/docs/agent/` |
| Coding Agent | `docs-site/docs/coding-agent/` |

Do not send beginners to deep API pages before the first runnable slice works.
