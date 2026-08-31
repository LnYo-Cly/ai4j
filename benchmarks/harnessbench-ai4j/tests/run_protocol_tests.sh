#!/usr/bin/env bash
# No-model protocol regression for the ai4j HarnessBench bridge.
#
# Proves, without any provider credential:
#   1. env/parameter contract (HARNESSBENCH_* -> bridge config)
#   2. exit-code contract (0 completed/waiting, 1 failed)
#   3. runtime (dynamic) task creation through the harness management tool
#   4. cross-process continuation: each round is a fresh JVM; state comes
#      back from the durable store, not process memory
#   5. bounded-slice checkpoints persist and resume
#   6. async tools end the round WAITING with a persisted open wait
#   7. bare-agent mode rounds and transcript replay
# plus audit exports that pass audit/check_audit.py invariants.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BENCH_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BRIDGE="$BENCH_ROOT/bin/ai4j-bridge.sh"
CHECKER="$BENCH_ROOT/audit/check_audit.py"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/ai4j-harnessbench-test-XXXXXX")"

PASS=0
FAIL=0

# Windows-style path for JVM/python arguments (Git Bash/MSYS safe).
W() { cygpath -w "$1" 2>/dev/null || echo "$1"; }

setup_env() {
    local sandbox="$1" session="$2" task="$3"
    mkdir -p "$sandbox/workspace/in" "$sandbox/workspace/out"
    export HARNESSBENCH_SANDBOX="$(W "$sandbox")"
    export HARNESSBENCH_WORKSPACE="$(W "$sandbox/workspace")"
    export HARNESSBENCH_SESSION_ID="$session"
    export HARNESSBENCH_TASK_ID="$task"
    export HARNESSBENCH_MODEL_ID="script-test"
}

run_round() {
    local script="$1" round="$2" prompt="$3"
    local pf="$WORK/prompt-round${round}.txt"
    printf '%s' "$prompt" > "$pf"
    export HARNESSBENCH_PROMPT_FILE="$(W "$pf")"
    export AI4J_BENCH_ROUND="$round"
    export AI4J_BENCH_SCRIPT="$(W "$script")"
    bash "$BRIDGE" run
}

expect() {
    local label="$1" expected="$2" actual="$3"
    if [ "$expected" = "$actual" ]; then
        echo "  PASS  $label"
        PASS=$((PASS + 1))
    else
        echo "  FAIL  $label (expected [$expected], got [$actual])"
        FAIL=$((FAIL + 1))
    fi
}

status_of() { grep -o '"status":"[A-Z_]*"' | head -1 | cut -d'"' -f4; }
state_len() { python -c "import json,sys;d=json.load(open(sys.argv[1],encoding='utf-8'));print(len(d['state']['$2']))" "$(W "$1")"; }
check_ok() { python "$CHECKER" "$(W "$1")" ${2:+--expect-status "$2"}; }

echo "== protocol regression (work root: $WORK)"

# --- 1. dynamic task creation, single round -------------------------------
S1="$WORK/s1"
setup_env "$S1" "sess-dynamic" "057-interruption-resume"
OUT=$(run_round "$BENCH_ROOT/tests/scenarios/scenario-dynamic-task.json" 1 "do the discovered work")
CODE=$?
echo "-- scenario: dynamic task (exit=$CODE)"
expect "exit code 0" "0" "$CODE"
expect "status COMPLETED" "COMPLETED" "$(echo "$OUT" | status_of)"
check_ok "$S1" COMPLETED > "$WORK/check1.txt" 2>&1
expect "audit invariants" "0" "$?"
if grep -q "PASS  dynamic-task" "$WORK/check1.txt"; then
    echo "  PASS  audit shows runtime-created task"; PASS=$((PASS + 1))
else
    echo "  FAIL  audit dynamic-task evidence missing"; cat "$WORK/check1.txt"; FAIL=$((FAIL + 1))
fi

# --- 2. cross-process continuation, two rounds, same session ---------------
S2="$WORK/s2"
setup_env "$S2" "sess-resume" "058-multiday-project-state"
OUT=$(run_round "$BENCH_ROOT/tests/scenarios/scenario-dynamic-task.json" 1 "round one: start the project state")
CODE=$?
echo "-- scenario: cross-process resume round 1 (exit=$CODE)"
expect "round 1 exit code 0" "0" "$CODE"
OUT=$(run_round "$BENCH_ROOT/tests/scenarios/scenario-round2.json" 2 "round two: continue the project state")
CODE=$?
echo "-- scenario: cross-process resume round 2 (exit=$CODE)"
expect "round 2 exit code 0" "0" "$CODE"
AUDIT2="$S2/ai4j-audit/harness_audit.json"
expect "store saw 2 executions" "2" "$(state_len "$AUDIT2" executions)"
BOUND=$(python -c "import json,sys;d=json.load(open(sys.argv[1],encoding='utf-8'));es=d['state']['executions'];print(','.join(sorted({e['taskId'] for e in es})) if all(e.get('taskId') for e in es) else 'unbound')" "$(W "$AUDIT2")")
TASK_RECORDS=$(python -c "import json,sys;d=json.load(open(sys.argv[1],encoding='utf-8'));print(len([t for t in d['state']['tasks'] if t.get('taskId')=='runtime-task-a']))" "$(W "$AUDIT2")")
if [ "$BOUND" = "runtime-task-a" ] && [ "$TASK_RECORDS" = "1" ]; then
    echo "  PASS  both rounds continued task runtime-task-a from durable store"; PASS=$((PASS + 1))
else
    echo "  FAIL  rounds did not continue one durable task (bound=$BOUND, taskRecords=$TASK_RECORDS)"; FAIL=$((FAIL + 1))
fi
check_ok "$S2" > "$WORK/check2.txt" 2>&1
expect "round-2 audit invariants" "0" "$?"
if grep -q "PASS  round-continuity" "$WORK/check2.txt"; then
    echo "  PASS  audit round-continuity"; PASS=$((PASS + 1))
else
    echo "  FAIL  audit round-continuity missing"; cat "$WORK/check2.txt"; FAIL=$((FAIL + 1))
fi

# --- 3. bounded slice persists a checkpoint and resumes --------------------
S3="$WORK/s3"
setup_env "$S3" "sess-slice" "105-partial-batch-resume-ledger"
export AI4J_BENCH_MAX_STEPS="1"
export AI4J_BENCH_AUTO_RESUME="false"
OUT=$(run_round "$BENCH_ROOT/tests/scenarios/scenario-slice.json" 1 "process the batch with a one-step budget")
CODE=$?
echo "-- scenario: bounded slice (exit=$CODE)"
expect "slice reports CONTINUATION_REQUIRED" "CONTINUATION_REQUIRED" "$(echo "$OUT" | status_of)"
CP_COUNT=$(state_len "$S3/ai4j-audit/harness_audit.json" checkpoints)
if [ "$CP_COUNT" -ge 1 ] 2>/dev/null; then
    echo "  PASS  checkpoint persisted ($CP_COUNT)"; PASS=$((PASS + 1))
else
    echo "  FAIL  no checkpoint after bounded slice"; FAIL=$((FAIL + 1))
fi
OUT=$(run_round "$BENCH_ROOT/tests/scenarios/scenario-round2.json" 2 "resume and finish")
CODE=$?
echo "-- scenario: resume after slice (exit=$CODE)"
expect "resume exit code 0" "0" "$CODE"
unset AI4J_BENCH_MAX_STEPS AI4J_BENCH_AUTO_RESUME
check_ok "$S3" > "$WORK/check3.txt" 2>&1
expect "slice audit invariants" "0" "$?"

# --- 4. async tool => WAITING with durable open wait -----------------------
S4="$WORK/s4"
setup_env "$S4" "sess-async" "106-release-approval-gate-plan"
OUT=$(run_round "$BENCH_ROOT/tests/scenarios/scenario-async-wait.json" 1 "start the remote release build")
CODE=$?
echo "-- scenario: async wait (exit=$CODE)"
expect "async round exit code 0" "0" "$CODE"
expect "status WAITING" "WAITING" "$(echo "$OUT" | status_of)"
check_ok "$S4" WAITING > "$WORK/check4.txt" 2>&1
expect "wait audit invariants" "0" "$?"
if grep -q "PASS  open-wait-persisted" "$WORK/check4.txt"; then
    echo "  PASS  open wait persisted"; PASS=$((PASS + 1))
else
    echo "  FAIL  open wait not persisted"; cat "$WORK/check4.txt"; FAIL=$((FAIL + 1))
fi

# --- 5. provider failure => exit 1 ------------------------------------------
S5="$WORK/s5"
setup_env "$S5" "sess-fail" "059-event-update-replan"
OUT=$(run_round "$BENCH_ROOT/tests/scenarios/scenario-fail.json" 1 "this will fail")
CODE=$?
echo "-- scenario: provider failure (exit=$CODE)"
expect "failure exit code 1" "1" "$CODE"
expect "status FAILED" "FAILED" "$(echo "$OUT" | status_of)"

# --- 6. bare mode rounds + transcript replay --------------------------------
S6="$WORK/s6"
setup_env "$S6" "sess-bare" "004-meeting-summary"
export AI4J_BENCH_MODE="bare"
OUT=$(run_round "$BENCH_ROOT/tests/scenarios/scenario-bare-text.json" 1 "summarize")
CODE=$?
OUT=$(run_round "$BENCH_ROOT/tests/scenarios/scenario-bare-text.json" 2 "continue")
CODE2=$?
unset AI4J_BENCH_MODE
echo "-- scenario: bare mode (exits=$CODE,$CODE2)"
expect "bare round 1 exit 0" "0" "$CODE"
expect "bare round 2 exit 0" "0" "$CODE2"
LINES=$(wc -l < "$S6/ai4j-state/bare/sess-bare.jsonl" | tr -d ' ')
expect "transcript has 2 rounds" "2" "$LINES"
check_ok "$S6" > "$WORK/check6.txt" 2>&1
expect "bare audit reports no durable state" "0" "$?"
if grep -q "not exercised" "$WORK/check6.txt"; then
    echo "  PASS  bare mode honestly reports no harness state"; PASS=$((PASS + 1))
else
    echo "  FAIL  bare audit check unexpected"; cat "$WORK/check6.txt"; FAIL=$((FAIL + 1))
fi

echo
echo "protocol regression: $PASS passed, $FAIL failed"
echo "work root kept for inspection: $WORK"
[ "$FAIL" = "0" ]
