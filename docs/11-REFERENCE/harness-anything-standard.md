# Harness Anything Project Standard

> Last updated: 2026-07-18

This repository uses Harness Anything (`ha`) for new task, evidence, decision,
review, and completion management. The current command contract is the local
CLI, not a copied command list:

```text
ha --help
ha capabilities --json
```

Upstream orientation:

- [Harness Anything README](https://github.com/FairladyZ625/harness-anything/blob/main/README.md)
- [Daily commands](https://github.com/FairladyZ625/harness-anything/blob/main/docs-release/start/zh/03-daily-commands.md)
- [Actor attribution](https://github.com/FairladyZ625/harness-anything/blob/main/docs-release/actor-attribution.zh-CN.md)
- [Release posture](https://github.com/FairladyZ625/harness-anything/blob/main/docs-release/release-posture.md)

## Storage Boundary

`harness/` is the authored private nested Git ledger. It contains task,
decision, fact, context, module, and closeout records. `.harness/` is a local,
rebuildable projection/cache. The outer repository must ignore both paths, and
code PRs must not contain private ledger changes.

The tracked regression controls remain authoritative for code verification:

- `docs/05-TEST-QA/Regression-SSoT.md`
- `docs/05-TEST-QA/Cadence-Ledger.md`

## Bootstrap And Health

Run the read-only diagnostic first. On a new checkout, initialize once:

```powershell
ha doctor --json
ha init --name ai4j-sdk
ha daemon repo register --repo-id ai4j-sdk --root .
ha daemon start --service
ha status --json
ha check --profile target-project --strict --json
```

The daemon-backed CLI is the normal write path and provides single-writer
coordination. A local person/credential binding may be required by the daemon;
configure that through HA's identity model rather than adding machine-specific
credentials to tracked project files. Direct mode is only a bootstrap,
recovery, or isolated-test path and must be explicit:

```powershell
$env:HARNESS_DAEMON_MODE = "direct"
$env:HARNESS_DIRECT_WRITE_REASON = "recovery"
```

## Attribution

Every write needs an actor and a Git author. Agents may use a per-process actor:

```powershell
$env:HARNESS_ACTOR = "agent:codex"
$env:HARNESS_GIT_AUTHOR_NAME = (git config --get user.name)
$env:HARNESS_GIT_AUTHOR_EMAIL = (git config --get user.email)
```

Human writes use an explicit one-command flag such as
`ha --actor human:<person-id> ...`. Do not export a human actor: child agents
could inherit it and falsely appear to be a human write.

## Task Lifecycle

The minimum managed flow is:

```powershell
ha task create --title "..." --kind feat --risk-tier medium --urgency medium
ha task claim <task-id> --execution
ha task transition <task-id> active
ha task progress append <task-id> --text "..." --evidence "command:.:verified command"
ha fact record --task <task-id> --statement "..." --source "..." --confidence high
ha task transition <task-id> in_review
ha task review-execution <task-id> --execution-id <execution-id> `
  --verdict approved --findings "..." --rationale "..." `
  --consent-utterance "<exact human approval>"
ha task complete <task-id> --ci passed --reviewer <reviewer-id>
```

Use `ha task progress append` for work-in-progress evidence, `ha fact record`
for durable observations, and `ha decision propose/transition/relate` for
load-bearing architectural choices. `ha task review` is a legacy review.md
compatibility lint; it does not approve an Execution or replace typed human
consent. `ha task complete` is the completion gate.

## Modules, Relations, And Worktrees

Register the monorepo's meaningful module boundaries with `ha module register`
when module filtering or dependency relations add value. Use typed task
relations for real dependencies, `ha relation list`/`ha graph` for navigation,
and the HA worktree surface for task-bound implementation isolation:

```powershell
ha module list --json
ha worktree create --task <task-id> --branch-prefix docs
ha worktree status --task <task-id>
ha relation list --entity task/<task-id>
ha graph --focus task/<task-id>
```

Use `ha capabilities --json` before relying on a less common command. Upstream
README snippets are useful orientation but may lag the installed CLI.

## Completion And Review Boundary

An HA task is not complete because an agent says it is complete. The task must
have the required Execution output, a schema-checked typed Review, applicable
human consent, closeout material, and the resolved preset/profile gates. Facts
are evidence and durable memory; they are not a substitute for review. If a
review gate requires a human decision, stop at `in_review` and request it.
