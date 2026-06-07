# Walkthrough - Lightweight ChatClient First Chat Facade

> Date: 2026-06-07
> Feature: F-023 轻量 ChatClient 首聊门面
> Task: `coding-agent-harness/planning/tasks/2026-06-07-chatclient-d5f84742/task_plan.md`

## Summary

This feature added a lightweight Plain Java first-chat facade so a user can start with:

```java
String answer = ChatClient.openAi("sk-...", "gpt-4o-mini").chat("你好，介绍一下 ai4j");
```

The facade keeps the existing lower-level `Configuration`, `AiService`, `IChatService`, and raw `ChatCompletionResponse` path available for advanced usage.

## Changed Surface

| Surface | Change |
| --- | --- |
| `ai4j` | Added `io.github.lnyocly.ai4j.service.ChatClient` and local API tests |
| `docs-site` | Updated first-chat onboarding to prefer the short path and keep object-chain examples as advanced detail |
| Root/docs README | Repointed first-run examples toward `ChatClient` |
| `skills/ai4j-app-builder` | Updated the recipe so agent-assisted users start from the short first-chat path |
| Harness governance | Updated RG-001/RG-007/RG-008 evidence and closed the task after human review confirmation |

## Verification

| Gate | Command | Result |
| --- | --- | --- |
| Targeted API test | `mvn -pl ai4j -Dtest=ChatClientTest -DskipTests=false test` | pass |
| First-chat regression | `mvn -pl ai4j "-Dtest=ChatClientTest,FirstChatCopyableCodeTest,ConfigurationTest" -DskipTests=false test` | pass |
| RG-001 | `mvn -pl ai4j -am -DskipTests=false test` | pass, 108 tests |
| RG-007 | `mvn -DskipTests package` | pass, 9 reactor modules |
| RG-008 typecheck | `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck` in `docs-site/` | pass |
| RG-008 build | `NODE_OPTIONS=--max-old-space-size=8192 npm run build` in `docs-site/` | pass |
| Diff hygiene | `git diff --check` | pass; only expected Windows LF/CRLF warnings were noted in task evidence |

## Review

The agent review reported no material findings. Human review was confirmed through the local Dashboard workbench on 2026-06-07, and the task was advanced to done.

## Residuals

| Residual | Owner | Follow-up |
| --- | --- | --- |
| Real provider key, quota, network, and model quality were not validated in this local gate | release operator | Use LV-001 only when provider behavior or release validation is explicitly in scope |
| `ChatClient` currently covers the OpenAI-compatible first-chat facade | coordinator | Add provider-specific facades only through a separate API design task |

## Lessons Reflection

No reusable governance lesson was accepted for promotion. The task-local decision is recorded as `checked-none: chatclient-first-chat-facade-local-api-no-new-governance-lesson`.
