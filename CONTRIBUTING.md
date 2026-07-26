# Contributing to ai4j

Thanks for your interest in improving ai4j. This guide covers the practical steps for reporting issues, proposing features, and landing code changes.

## 1. Target branch

**Open pull requests against `main`.**

The legacy `dev` branch is stale and not maintained. All active development flows through `main`; release commits are tagged directly from it. If you still have an old fork tracking `dev`, rebase onto `main` before opening a PR.

## 2. Before you start

- Search [open issues](https://github.com/LnYo-Cly/ai4j/issues) and [Discussions](https://github.com/LnYo-Cly/ai4j/discussions) to avoid duplicates.
- For non-trivial changes, open an issue or Discussion first to align on scope and API shape.
- [Good first issues](https://github.com/LnYo-Cly/ai4j/labels/good%20first%20issue) are scoped for newcomers — claim one by commenting, then a maintainer will assign it.

## 3. Reporting a bug

Use the **Bug report** issue template and fill in every field. The template asks for ai4j version, JDK, provider, whether you run under Spring Boot, and a minimal reproduction. Reports without a reproduction may be closed.

## 4. Requesting a feature

Use the **Feature request** issue template. Describe the use case, the current workaround you rely on, and the API surface you would expect.

## 5. Building and testing

Requirements: JDK 8+ and Maven 3.6+.

Build the whole project:

```bash
mvn clean install -DskipTests
```

Run tests for a single module (the core SDK is most common):

```bash
mvn -pl ai4j test
```

Run tests across the multi-module reactor (extension API, agent, coding, etc.):

```bash
mvn test
```

Some tests call live provider endpoints and are gated behind environment variables (for example `OPENAI_API_KEY`). If a key is absent those tests are skipped, not failed — a clean local run should pass with no extra configuration.

## 6. Code style

- **Indentation:** 4 spaces, no tabs.
- **Types:** `PascalCase` (e.g. `ChatCompletion`, `AiService`).
- **Methods and fields:** `lowerCamelCase` (e.g. `chatCompletion`, `apiKey`).
- **Constants:** `UPPER_SNAKE_CASE` (e.g. `DEFAULT_TIMEOUT`).
- Keep public API binary-compatible with the current minor line; deprecated APIs should be marked `@Deprecated` with a replacement note in the Javadoc.
- New public API that is still in flux should be annotated `@Experimental` so downstream consumers know it may change. See the Javadoc on `io.github.lnyocly.ai4j.extension.api.annotation.Experimental` for the stability contract.

## 7. Commit and PR conventions

- Branch naming: `feature/<topic>`, `fix/<topic>`, or `docs/<topic>`.
- Commit message format — prefix with the change type:
  - `feat(scope): summary`
  - `fix(scope): summary`
  - `docs: summary`
  - `chore: summary`
- Squash to a clean set of commits before requesting review; a PR that tells one logical story is easier to review.
- Fill in the **pull request template**: change type, linked issue, the exact `mvn` command you ran to verify, and any breaking change.

## 8. Relationship to AGENTS.md

This file is the contributor guide for humans. `AGENTS.md` is the authoritative entry point for coding agents (automated tools that work in this repo). The two are intentionally separate: AGENTS.md defines task routing, file-reading matrices, and harness conventions that do not belong in a human-facing contributing guide. When changing agent-facing conventions, edit `AGENTS.md`; when changing human contribution flow, edit this file.

## 9. Security

Do not open public issues for security vulnerabilities. See [SECURITY.md](SECURITY.md) for private disclosure.

## 10. Code of Conduct

Participating in this project means following the [Code of Conduct](CODE_OF_CONDUCT.md). Be kind and constructive.

## License

By contributing, you agree your contributions are licensed under the [Apache License 2.0](LICENSE).
