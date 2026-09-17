# Harness Anything Project Standard

> Last updated: 2026-08-30

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

The installed thin CLI is the authority. Start with this read-only preflight:

```powershell
ha capabilities --json
ha daemon status
ha agenda
ha agent list --json
ha squad list --json
ha runtime instance list
```

On a new checkout, initialize and register the workspace once:

```powershell
ha init --repo-id ai4j-sdk --person-id <person-id> --display-name "<display-name>" --name ai4j-sdk
```

`ha init` starts the daemon on demand. If the resident daemon was stopped, use
`ha daemon start --service` and then re-run `ha daemon status`. The explicit
`ha daemon repo register --repo-id ai4j-sdk --root .` form is for an already
initialized workspace that needs registration repaired.

The daemon-backed CLI is the normal write path and provides single-writer
coordination. A local person/credential binding may be required by the daemon;
configure that through HA's identity model rather than adding machine-specific
credentials to tracked project files. Direct mode is only a bootstrap,
recovery, or isolated-test path and must be explicit:

```powershell
$env:HARNESS_DAEMON_MODE = "direct"
$env:HARNESS_DIRECT_WRITE_REASON = "recovery"
```

The current CLI does not expose the historical `ha doctor --json`,
`ha status --json`, or `ha check --profile target-project --strict --json`
commands. Older task records may contain them as historical evidence; do not
use them as the current preflight.

## Fable Agent And Squad Declarations

An Agent declaration package is a directory containing `agent.json`; a Squad
declaration package is a directory containing `squad.json`. The smallest useful
shapes are:

```json
{
  "schema": "agent-declaration/v1",
  "id": "example-worker",
  "name": "Example Worker",
  "role": "worker",
  "runtime_type": "claude",
  "instructions": "..."
}
```

```json
{
  "schema": "squad-declaration/v1",
  "id": "example-squad",
  "name": "Example Squad",
  "leader": "fable-commander",
  "workers": ["example-worker"],
  "leaderTurnBudget": 1,
  "roster": "Before convergence, publish one synthesis report at artifacts/reports/example-squad-synthesis.md."
}
```

Validate before installing, then read back the canonical entity store:

```powershell
$source = (Resolve-Path '<agent-package-directory>').Path
ha agent validate --source $source
ha agent install --source $source
ha agent list --json
ha agent inspect <agent-id>

$source = (Resolve-Path '<squad-package-directory>').Path
ha squad validate --source $source
ha squad install --source $source
ha squad list --json
ha squad inspect <squad-id>
```

For this checkout, the installed identities are `fable-commander`, `sol`,
`terra`, and `luna`; the installed domain squads are `debug-squad`,
`gui-squad`, `ci-triage-squad`, `antientropy-squad`, `ledger-squad`, and
`ontology-squad`. Choose an execution resource from `ha runtime instance list`
when dispatching; do not treat an instance name as an Agent identity.

```powershell
# Prefer the matching domain Squad.
ha squad run debug-squad --instance <ready-instance> --task <task-id> --cwd <worktree>

# Use one declared Agent when a Squad is unnecessary.
ha runtime run <ready-instance> --agent sol --task <task-id> --cwd <worktree> --detach
ha runtime status --task <task-id> --wait
```

With `--task`, ordinary dispatch derives its mission from the task package.
Use `--prompt` or `--mission` only for a bounded override or a named mission;
the current CLI does not provide `--prompt-file`.

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
ha task pin <task-id>
ha task start <task-id> --ttl-ms 7200000
ha task progress append <task-id> --text "..." --evidence "command:.:verified command"
ha fact record --task <task-id> --statement "..." --source "..." --confidence high
ha task submit <task-id> --from-file <submission.json>
ha task review-execution <task-id> --review-id <review-id> --from-file <review.json>
ha task review-consent <task-id> --consent-id <consent-id>
ha task complete <task-id> --ci passed --path <canonical-path>
```

Use `ha task progress append` for work-in-progress evidence, `ha fact record`
for durable observations, and `ha decision propose/transition/relate` for
load-bearing architectural choices. `ha task review` is a legacy review.md
compatibility lint; it does not approve an Execution or replace typed human
consent. `ha task complete` is the completion gate.

## Task Navigation And Relations

Use the currently exposed task, relation, and agenda views for navigation:

```powershell
ha task list
ha task show <task-id>
ha task dispatches <task-id>
ha relation list --entity task/<task-id>
ha agenda
```

Use `ha capabilities --json` before relying on a less common command. The
current thin CLI does not expose the historical `ha module`, `ha worktree`, or
`ha graph` domains; use the repository's `git worktree` flow for isolation and
verify any future HA replacement through live capabilities/help. Upstream README
snippets are useful orientation but may lag the installed CLI.

## Completion And Review Boundary

An HA task is not complete because an agent says it is complete. The task must
have the required Execution output, a schema-checked typed Review, applicable
human consent, closeout material, and the resolved preset/profile gates. Facts
are evidence and durable memory; they are not a substitute for review. If a
review gate requires a human decision, stop at `in_review` and request it.
