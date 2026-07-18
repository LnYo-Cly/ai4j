# First Chat Copyable Code Contract

## Overview

This task made the first-chat onboarding examples verifiable. The Plain Java path now has a local core SDK smoke test, the Spring Boot path has a starter auto-configuration smoke test, and docs-site / `ai4j-app-builder` recipes name the exact regression commands that protect those examples.

## Scope

| Surface | Change |
| --- | --- |
| `ai4j` | Added `FirstChatCopyableCodeTest` for the Plain Java first-chat object chain and local OpenAI-compatible response text extraction. |
| `ai4j-spring-boot-starter` | Added `AiServiceFirstChatAutoConfigurationTest` for starter config binding and `AiService` / `IChatService` creation. |
| `docs-site` | Added regression-contract notes to 5-minute first chat and Java/Spring Boot quickstarts. |
| `skills/ai4j-app-builder` | Added recipe maintenance guards for Plain Java and Spring Boot first-chat paths. |
| Governance | Updated Feature SSoT, Regression SSoT, Cadence Ledger, and task review materials. |

## Key Decisions

- Local tests are the default contract. They do not require API keys, real provider calls, or network access beyond local test doubles.
- Plain Java keeps using the current `Configuration` default `OkHttpClient` behavior. Custom `OkHttpClient` remains a documented option for proxy, timeout, interceptor, or network-stack needs.
- Live provider validation is out of scope for this task and remains under LV-001.

## Verification

| Gate | Command | Result |
| --- | --- | --- |
| Core targeted | `mvn -pl ai4j "-Dtest=FirstChatCopyableCodeTest,ConfigurationTest" -DskipTests=false test` | pass, 5 tests |
| RG-001 | `mvn -pl ai4j -am -DskipTests=false test` | pass, 103 tests |
| Starter targeted | `mvn -pl ai4j-spring-boot-starter -Dtest=AiServiceFirstChatAutoConfigurationTest -DskipTests=false test` | pass, 1 test |
| RG-005 | `mvn -pl ai4j-spring-boot-starter -am -DskipTests=false test` | pass, core 103 tests and starter 3 tests |
| RG-008 | `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck` and `npm run build` in `docs-site/` | pass |
| RG-007 | `mvn -DskipTests package` | pass, 9 reactor modules |

## Evidence Depth

- L1 tests: core SDK and Spring Boot starter.
- L2 local smoke: docs-site build/typecheck and monorepo package.
- L3 live: skipped by design; no real provider behavior was in scope.

## Residual

No task-blocking residuals. Real provider success still depends on valid credentials, model availability, quota, network, and provider behavior; keep that under opt-in live validation.

## Links

| Item | Path |
| --- | --- |
| Task plan | pre-HA record; see Git history |
| Task review | pre-HA record; see Git history |
| Feature SSoT | `docs/09-PLANNING/Feature-SSoT.md` |
| Regression SSoT | `docs/05-TEST-QA/Regression-SSoT.md` |
| Cadence Ledger | `docs/05-TEST-QA/Cadence-Ledger.md` |
