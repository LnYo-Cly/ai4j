# Findings

## Material Findings

No open material findings.

## Closed Findings

| ID | Severity | Finding | Resolution | Evidence |
| --- | --- | --- | --- | --- |
| F-001 | P2 | `ai4j-app-builder` 的 Plain Java 首聊示例只设置 provider config，但 core SDK 的 `OpenAiChatService` 需要 `Configuration` 提供 `OkHttpClient`，小白用户复制示例可能遇到空指针。 | `Configuration` 默认创建 `OkHttpClient`；recipe 补充 `Configuration`、`OpenAiConfig` imports，并说明只有自定义超时、代理、拦截器或连接设置时才覆盖 client。 | `ConfigurationTest`、`mvn -pl ai4j -am -DskipTests=false test`、`mvn -DskipTests package`、`quick_validate.py skills\ai4j-app-builder` |

## Decisions

### Remove Maintainer Skill

`$ai4j-sdk` duplicated repository-maintenance guidance already owned by `AGENTS.md` and `coding-agent-harness/`. Removing it reduces public confusion and future drift.

### Keep Historical Evidence

Historical task artifacts that mention `$ai4j-sdk` are retained as records of prior work. Active public entry points are cleaned instead.

### Fix Runtime Prerequisite At SDK Layer

Plain Java onboarding should not require users to remember infrastructure wiring that the SDK can safely default. `Configuration` now owns a default `OkHttpClient`, while Spring Boot starter and advanced users can still override it.

## Residual Risk

- Users who already installed `$ai4j-sdk` from a prior commit may still have a local copy. Future release notes can mention the consolidation if needed.
- Remote repository state must be pushed before `npx skills add LnYo-Cly/ai4j --skill ai4j-app-builder` reflects this local deletion.
