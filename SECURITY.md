# Security Policy

## Supported Versions

ai4j follows a limited support window. Only the two most recent minor releases receive security fixes.

| Version | Supported          |
| ------- | ------------------ |
| 2.4.x   | :white_check_mark: |
| 2.3.x   | :white_check_mark: |
| < 2.3   | :x: End of life   |

If you are on an unsupported version, upgrade to the latest 2.4.x release before reporting a vulnerability.

## Reporting a Vulnerability

**Do NOT open a public GitHub issue for security vulnerabilities.**

Please report suspected vulnerabilities privately:

- Email: **lnyocly@gmail.com**
- Subject prefix: `[SECURITY] ai4j - <short summary>`

Include the following in your report so we can reproduce and triage quickly:

1. ai4j version and affected module(s) (`ai4j`, `ai4j-spring-boot-starter`, `ai4j-agent`, `ai4j-coding`, `ai4j-cli`, etc.)
2. JDK version and runtime environment
3. Provider / platform involved (OpenAI, Anthropic, DashScope, Ollama, MCP server, etc.)
4. Minimal reproduction steps or proof-of-concept
5. Impact assessment and any known mitigations

## Response Timeline

| Stage              | Target       |
| ------------------ | ------------ |
| Acknowledgement    | within 24 hours of report |
| Initial assessment | within 72 hours |
| Fix or mitigation  | within 30 days for high severity, 90 days for medium/low |
| Public disclosure  | after a fix is released, or after 90 days from report (Coordinated Disclosure) |

We will keep you informed at each stage and credit you in the release notes unless you prefer to remain anonymous.

## Scope

In scope:

- Vulnerabilities in ai4j source code that allow remote code execution, credential leakage, request forgery, or denial of service when the library is used as documented.
- Flaws in MCP client/server handling, Tool Call execution boundaries, or RAG ingestion pipelines that bypass intended security constraints.
- Supply-chain concerns in published artifacts under `io.github.lnyo-cly` on Maven Central.

Out of scope:

- Vulnerabilities in upstream LLM provider APIs or third-party MCP servers.
- Issues that require the application to already have been compromised.
- Rate limiting, billing, or quota enforcement on the provider side.

## Secure Usage Reminders

- Never hard-code API keys in source files, tests, or configuration committed to version control. Read keys from environment variables or a secrets manager.
- When wiring user-supplied input into Tool Call or MCP payloads, validate and sanitize before execution.
- The Coding Agent CLI executes tools against a local workspace; review extension plugins before enabling them with `--enable`.
