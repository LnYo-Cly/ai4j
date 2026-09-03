#!/usr/bin/env bash
# Bare-agent wrapper: same as ai4j-bridge.sh with AI4J_BENCH_MODE=bare.
export AI4J_BENCH_MODE="bare"
exec "$(dirname "${BASH_SOURCE[0]}")/ai4j-bridge.sh" run
