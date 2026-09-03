#!/usr/bin/env bash
# Harness-mode wrapper: same as ai4j-bridge.sh with AI4J_BENCH_MODE=harness.
export AI4J_BENCH_MODE="${AI4J_BENCH_MODE:-harness}"
exec "$(dirname "${BASH_SOURCE[0]}")/ai4j-bridge.sh" run
