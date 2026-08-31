# HarnessBench coverage matrix for the ai4j bridge

HarnessBench 2.0 checkout: 106 tasks across 8 classes. This matrix separates
**what each evidence channel can actually prove** for the ai4j integration.

Evidence channels:

- **Official oracle** — deterministic `oracle_grade.py` over the workspace
  after the run. Proves end-task outcomes, not harness internals.
- **LLM rubric** — `llm_rubric.py` quality signal only. Never evidence of
  harness correctness.
- **Protocol smoke** — `tests/run_protocol_tests.sh`: scripted-model,
  credential-free runs of the real bridge + real file-backed harness store.
  Proves the adapter contract, env/exit-code contract, and durable-state
  readback, cross-process.
- **Custom audit** — `audit/check_audit.py` over `harness_audit.json`
  (state projection read back from disk). Proves harness-internal invariants
  for a given run.
- **Module suite** — `ai4j-harness` unit tests (50 tests) own the deep
  governance semantics the benchmark runner cannot reach (idempotent
  redelivery, UNKNOWN reconciliation, cancel/late-result isolation, lease
  fencing).

## Class coverage (official runner)

| HarnessBench class | Tasks | Multi-round | Proven by |
|---|---|---|---|
| Long-running Autonomy & State Adaptation | 11 | 7 (007, 057, 058, 059, 060, 103, 105) | oracle + rubric + protocol smoke + audit (priority class) |
| Software Engineering & Codebase Maintenance | 22 | – | oracle + rubric |
| Workspace, Tool Use & Multimodal Operations | 15 | – | oracle + rubric; multimodal cases additionally need provider-side capabilities |
| Data, BI & Finance Analytics | 14 | – | oracle + rubric |
| Knowledge, Evidence & Retrieval | 13 | – | oracle + rubric |
| Office & Business Communication | 12 | – | oracle + rubric; browser/office tasks need optional local services |
| Vertical Professional Workflows | 12 | – | oracle + rubric |
| SRE, DevOps & Release Ops | 7 | – | oracle + rubric |

Priority multi-round tasks for the harness 对照组: `057-interruption-resume`,
`058-multiday-project-state`, `059-event-update-replan`,
`105-partial-batch-resume-ledger`, `106-release-approval-gate-plan`.

## Harness-internal capability provenance

| Capability | Protocol smoke | Custom audit | Module suite |
|---|---|---|---|
| Runtime (dynamic) task creation, no pre-registered Task | ✔ scenario 1 | ✔ `dynamic-task` | `AgentHarnessTest` |
| Stable session/workspace across rounds, fresh JVM per round | ✔ scenarios 1–2 | ✔ `round-continuity` | `AgentHarnessTest` |
| Checkpoint persist + resume after bounded slice | ✔ scenario 3 | ✔ `checkpoint-record-exists` | `AgentHarnessTest` |
| Async tool → WAITING, durable open wait | ✔ scenario 4 | ✔ `open-wait-persisted` | `CodingAgentHarnessTest` |
| Provider failure → exit 1, FAILED surfaced | ✔ scenario 5 | – | – |
| Bare control: no durable state, transcript replay only | ✔ scenario 6 | ✔ honest `not exercised` | – |
| Duplicate delivery idempotency, late-result isolation | – | key-count exported; behavior owned by module suite | `HarnessGatewayInvariantTest` (idempotency + atomic waits), `AgentHarnessTest` (late async completion) |
| Approval/submission gates block completion | – | checked when gates present (`gate-before-complete`) | `AgentHarnessTest.approvalWaitIsDurable...` |
| UNKNOWN not blindly retried | – | checked when present (`unknown-preserved`); no direct module-suite owner yet | – |
| Cancel does not reopen after late async result | – | – | `AgentHarnessTest.cancelledTaskQuarantinesLateAsyncCompletion...` |
| Lease fencing / worker handoff | – | – | module suite only |

## Not executed in this integration round

- **Live model runs** (any class): require provider credentials; recorded as
  environment blocker, not a skipped claim. Run with `ai4j-harness` /
  `ai4j-bare` entries once `AI4J_BENCH_API_KEY` is available.
- **Docker-isolated tasks**: the runner's container path was not exercised;
  the bridge runs on the host like other CLI adapters.
- **SIGKILL-mid-write journal recovery**: every bridge start exercises the
  store's journal replay (`readRecovered`), but a hard-kill injection is owned
  by `FileHarnessStore` tests, not by the benchmark surface.

## Residual risks

- generic_cli forwards `os.environ` + proxy env only; benchmark configuration
  (mode, model, credentials) must be exported in the shell running
  HarnessBench — the model_config `env` key is not forwarded by the runner.
- The bare mode's transcript-replay continuity is an honest but weak control:
  it shows outcome deltas of harness mode, not a second implementation.
- `prompt-round{N}.txt` naming is parsed for the round number; if the runner
  changes its naming scheme, `AI4J_BENCH_ROUND` must be set explicitly.
