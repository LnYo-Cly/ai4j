---
name: ai4j-sdk
description: Use this skill when developing, documenting, testing, reviewing, or troubleshooting the ai4j-sdk Java 8 Maven monorepo, including the core SDK, agent runtime, coding agent, CLI, Spring Boot starters, FlowGram integrations, docs-site, and demos. It guides AI agents to choose the right module, preserve boundaries, use harness tasks, protect secrets, and run targeted verification for beginner-friendly AI-assisted development.
---

# AI4J SDK Project Skill

## Purpose

Use this Skill as the project-aware copilot for AI4J SDK work. It is designed for users who may not know Maven, module boundaries, harness tasks, or the difference between SDK logic, starter wiring, demos, and docs.

The agent should reduce human development cost by doing the project reading, choosing the smallest correct module, explaining commands in plain language, implementing narrowly, and recording verification evidence.

## When To Use

Use this Skill for:

- AI4J SDK feature work, bug fixes, tests, examples, docs-site pages, or release documentation.
- Questions about where a change belongs inside `ai4j-sdk`.
- Review or refactor work that touches Java modules, starters, FlowGram integration, CLI, or docs surfaces.
- New-user assistance: "help me add a provider", "write a Spring Boot example", "fix this test", "update docs", "make an agent feature".

Do not use it for unrelated generic Java questions unless the answer must map back to this repository.

## Startup Checklist

1. Confirm the current directory is an `ai4j-sdk` checkout. If it is not clear, ask for the repo path.
2. Read the local `AGENTS.md` first when it exists. Treat it as the live repo contract.
3. Run `git status --short` before edits and keep unrelated dirty changes out of the work boundary.
4. Classify the touched surface with `references/repo-map.md`.
5. Before non-trivial edits, read `references/development-workflow.md` and follow the harness flow used by the repository.
6. Explain the selected module, command, and expected evidence in beginner-friendly language before making risky or broad changes.

## Core Rules

- Keep Java modules compatible with Java 8 unless the task explicitly changes the baseline.
- Preserve module boundaries: SDK/runtime code belongs in production modules; starters wire configuration; demos and docs must not become production sources of truth.
- Never hardcode secrets, provider keys, or one-developer local paths. Use env vars, local config, or documented placeholders.
- Prefer the smallest useful regression command for the touched surface, then escalate only when risk justifies it.
- Do not push to a remote unless the user explicitly asks for that.
- Do not create root-level planning files. Use the repository's harness task locations and commands.

## Reference Loading

- Read `references/repo-map.md` when selecting modules, source locations, docs surfaces, or ownership boundaries.
- Read `references/development-workflow.md` before implementation, verification, review, closeout, or when helping a beginner run the project.
- For agent runtime changes, also read `AGENT.md` if present.
- For docs-site changes, inspect `docs-site/sidebars.ts`, the target docs page, and run the docs-site build when content or config changes.

## Beginner Assistance Pattern

When helping a less experienced user:

1. State the likely module and why it owns the change.
2. Show the exact command you will run and what success means.
3. Implement in small steps and keep code examples copyable.
4. After verification, summarize changed files, passed checks, and remaining human decisions.
5. If live-provider credentials or external systems are needed, stop at a local-safe check and explain the missing env/config explicitly.
