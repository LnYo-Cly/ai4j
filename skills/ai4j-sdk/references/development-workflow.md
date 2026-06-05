# AI4J SDK Development Workflow

## Default Flow

1. Diagnose the changed surface and module scope.
2. Check `git status --short` and isolate unrelated dirty files.
3. For non-trivial work, create or update a harness task.
4. Read the smallest relevant source, tests, and standards.
5. Implement in the narrowest correct module boundary.
6. Run targeted verification.
7. Record evidence, review status, residual risks, and next steps.

## Harness Commands

Use `npx --yes coding-agent-harness <command> .` unless the repo has explicitly allowed another installed harness binary.

Typical task lifecycle:

```bash
npx --yes coding-agent-harness new-task --title "<title>" --locale zh-CN --budget standard --preset standard-task .
npx --yes coding-agent-harness task-start <task-id> --message "<what started>" .
npx --yes coding-agent-harness task-log <task-id> --message "<what changed>" --evidence "command:TARGET:<path>:<summary>" .
npx --yes coding-agent-harness task-phase <task-id> EXEC-01 --state done --completion 100 --evidence present .
npx --yes coding-agent-harness task-review <task-id> --message "<ready for review>" .
```

Only run review confirmation or task completion when the user has actually approved the review boundary or asked for closeout.

## Task-Type Reading Matrix

| Task type | Read first |
| --- | --- |
| Core SDK, provider, MCP, RAG, vector, agentflow connector | `docs/11-REFERENCE/engineering-standard.md` |
| Agent runtime, workflow, trace, subagent | `AGENT.md` and `docs/11-REFERENCE/engineering-standard.md` |
| Coding runtime, CLI, TUI, ACP | `docs/11-REFERENCE/engineering-standard.md` and `docs/11-REFERENCE/testing-standard.md` |
| Spring Boot, FlowGram starter, demo integration | `docs/11-REFERENCE/engineering-standard.md` and `docs/11-REFERENCE/testing-standard.md` |
| Regression or smoke verification | `docs/11-REFERENCE/testing-standard.md` and `docs/05-TEST-QA/Regression-SSoT.md` |
| Planning, SSoT, task tracking | `docs/11-REFERENCE/docs-library-standard.md` and `docs/11-REFERENCE/execution-workflow-standard.md` |
| Walkthrough closeout | `docs/11-REFERENCE/walkthrough-standard.md` |
| Worktree or multi-agent coordination | `docs/11-REFERENCE/worktree-standard.md` |

## Verification Matrix

| Surface | First verification choice |
| --- | --- |
| Single Java class | `mvn -pl <module> -Dtest=<ClassName> -DskipTests=false test` |
| One Java module | `mvn -pl <module> -DskipTests=false test` |
| Cross-module API | `mvn -pl <downstream-module> -am -DskipTests package` plus targeted tests |
| Docs-site page/config | `cd docs-site && npm run build` |
| BOM/version alignment | `mvn -DskipTests package` or the smallest affected module set |
| Live provider behavior | local tests first; live checks only with explicit credentials/env agreement |

## Beginner-Friendly Agent Behavior

- Translate repository terms into simple choices: "core SDK", "starter wiring", "demo", "docs", "CLI".
- Do not ask the user to decide Maven flags. Pick the smallest safe command and explain it.
- If a command fails, report the exact failing module, test, or missing environment variable.
- Keep examples minimal and runnable.
- Prefer adding or updating tests close to the changed class.
- If the user asks for a feature but docs do not match code, inspect code before writing docs.

## Safety Boundaries

- Keep Java 8 syntax and dependencies unless the task explicitly upgrades the baseline.
- Do not commit secrets, `.env` values, generated caches, `node_modules`, or local agent install folders.
- Avoid mechanical repo-wide formatting.
- Do not overwrite existing task history, regression records, walkthroughs, or business docs.
- Do not push to remote without explicit user instruction.
