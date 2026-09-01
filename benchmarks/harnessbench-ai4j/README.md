# HarnessBench adapter for ai4j

Run [HarnessBench](https://github.com/codex-harness/harnessbench) (a real-filesystem
agent/harness benchmark) against the ai4j SDK in two modes:

| Mode | What runs | What is durable |
|------|-----------|-----------------|
| `harness` (default) | `CodingAgentHarness` keyed by the bench session id | tasks, executions, checkpoints, waits/wakeups, gates, reviews, idempotency — file-backed store under the sandbox |
| `bare` | plain `Agent` per round | JSONL transcript replay only (the honest control: no durable tasks, gates, or audit state) |

The adapter is **generic_cli-only**: HarnessBench's stock `generic_cli` adapter
invokes `bin/ai4j-bridge.sh` once per round with `HARNESSBENCH_*` environment
variables. No Python adapter class is added to the benchmark or to the SDK, and
no benchmark dependency enters production Maven modules.

## Prerequisites

- JDK 8+ (Java 8 bytecode baseline for the bridge), Maven 3.6+
- Python 3 (stdlib only) for `audit/check_audit.py`
- Bash (on Windows: Git Bash) — the runner invokes `bash bin/ai4j-bridge.sh`
- A HarnessBench checkout placed outside this repository (e.g. `.tmp/harness-bench`, gitignored)

## One-time setup

```bash
# 1. install the SDK modules the bridge links against (skips the local
#    SpotBugs doc gate; CI still enforces it)
mvn -pl ai4j,ai4j-agent,ai4j-harness,ai4j-coding -am -DskipTests -Dspotbugs.skip=true install

# 2. build the bridge classpath + classes
benchmarks/harnessbench-ai4j/bin/ai4j-bridge.sh build
```

## Configuration

Copy `config/harnessbench-ai4j.example.yaml` into the HarnessBench checkout as
`config/harness.yaml` (or merge the `models:` entries into an existing file),
then export the provider configuration in the shell that runs HarnessBench:

| Env var | Meaning |
|---------|---------|
| `AI4J_BENCH_MODE` | `harness` (default) or `bare` |
| `AI4J_BENCH_API_KEY` | provider API key (**required for live runs; keep out of git**) |
| `AI4J_BENCH_BASE_URL` | optional OpenAI-compatible base URL |
| `AI4J_BENCH_MODEL` | model id passed to the agent (default `gpt-4o-mini`) |
| `AI4J_BENCH_MAX_STEPS` | per-round step budget (default 24) |
| `AI4J_BENCH_AUTO_RESUME` | continue continuation slices inside one round (default `true`) |
| `AI4J_BENCH_STATE_DIR` | durable state dir (default `<sandbox>/ai4j-state`) |
| `AI4J_BENCH_AUDIT_DIR` | audit artifact dir (default `<sandbox>/ai4j-audit`) |
| `AI4J_BENCH_SCRIPT` | **protocol/smoke only**: scripted-model scenario JSON, no network |

The bridge reads `HARNESSBENCH_TASK_ID/WORKSPACE/SANDBOX/SESSION_ID/PROMPT_FILE/MODEL_ID`
from the generic_cli environment. The benchmark `task_id` is **never** turned
into a pre-registered business Task; harness-mode agents create their own tasks
at runtime through the harness management tool surface.

## Running

```bash
cd .tmp/harness-bench
python -m harnessbench.cli run-task --task 057-interruption-resume --harness ai4j-harness
python -m harnessbench.cli run-task --task 057-interruption-resume --harness ai4j-bare   # control
python -m harnessbench.cli run-suite --harness ai4j-harness --from-num 57 --to-num 59
```

Protocol smoke without credentials or Docker:

```bash
benchmarks/harnessbench-ai4j/tests/run_protocol_tests.sh
```

## Audit artifacts

Each round writes two machine-readable files under the sandbox:

- `ai4j-audit/harness_audit.json` — sanitized projection read back from the
  durable store on disk (tasks, executions, checkpoints, waits, wakeups,
  gates, submissions, reviews, tool invocations, idempotency key count).
  Prompts, transcripts, and secrets are never exported.
- `ai4j-audit/execution_trace.json` — per-round bridge timeline (status,
  execution/task/wait ids, bounded output preview, error).

`audit/check_audit.py <sandbox>` validates invariants over these exports:
runtime (dynamic) task creation, execution→task references, round continuity
across fresh JVMs, checkpoint↔execution consistency, wait/wakeup pairing,
gate-before-complete, tool-invocation uniqueness, UNKNOWN-preserved-for-reconciliation.
Capability-dependent checks report `not exercised` instead of passing vacuously.

Exit codes: `0` round finished (COMPLETED / CONTINUATION_REQUIRED / WAITING /
BLOCKED / IN_REVIEW), `1` failed, `2` UNKNOWN/CANCELLED (operators reconcile;
the bridge never blindly retries).

## What official runs prove vs. what they cannot

See [docs/COVERAGE.md](docs/COVERAGE.md) for the category matrix: which of the
eight HarnessBench classes are covered by the official oracle, which only yield
LLM-rubric quality signals, and which Harness-internal guarantees (durable
state, cross-process recovery, idempotent redelivery, approval gates,
UNKNOWN/cancel handling) require the audit exports and the ai4j-harness module
test suite.

## 实测对照（2026-09-01，gpt-5.6-terra / reasoning medium / 同网关同 key）

优先任务 × 五臂（各臂裸配置：codex `--yolo` 无 skill、opencode `--pure`、pi `--no-skills --no-extensions`、hermes oneshot）：

| 臂 | 001 | 057 | 058 | 059 | 105 | 106 | 多轮均值 | 轮次稳定率 |
|---|---|---|---|---|---|---|---|---|
| **ai4j-harness** | 1.00 | 0.81 | **1.00** | **1.00** | **0.72** | **0.64** | **0.834** | 100% |
| codex CLI | 1.00 | 0.88 | 0.30* | 0.24* | 0.22* | 0.47 | 0.421 | 50% |
| hermes | 1.00 | 0.73 | **1.00** | **1.00** | 0.22 | 0.51 | 0.692 | 100% |
| opencode | 1.00 | 0.81 | **1.00** | **1.00** | 0.56 | 0.47 | 0.767 | 100% |
| pi | 1.00 | 1.00 | 0.00* | **1.00** | 0.70 | 0.61 | 0.662 | 83% |

\* 该轮 agent 进程非零退出（模型自述未完成），分数仍计入。n=1/格，注意方差。

观察：ai4j-harness 多轮均值第一；在治理语义重的 105/106 与跨轮状态 058 上居首；
057 由 pi 领先（1.00）。reasoning 等级经 SDK `ChatModelClient` 的
`reasoning→reasoning_effort` 映射显式下发（medium），与各臂对齐。
