#!/usr/bin/env bash
# HarnessBench generic_cli entry for the ai4j bridge.
#
# One-time setup (builds the dependency classpath and compiles the bridge):
#   benchmarks/harnessbench-ai4j/bin/ai4j-bridge.sh build
#
# Benchmark rounds are invoked by the HarnessBench generic_cli adapter with
# HARNESSBENCH_* / AI4J_BENCH_* environment variables; no script args needed.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BENCH_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$BENCH_ROOT/../.." && pwd)"
TARGET="$BENCH_ROOT/target"
CP_FILE="$TARGET/classpath.txt"
CLASSES="$TARGET/classes"
SRC="$BENCH_ROOT/java/io/github/lnyocly/ai4j/harnessbench/HarnessBenchBridge.java"
MAIN_CLASS="io.github.lnyocly.ai4j.harnessbench.HarnessBenchBridge"

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) CP_SEP=";" ;;
    *) CP_SEP=":" ;;
esac
# javac.exe needs Windows-style paths for the classpath under Git Bash/MSYS.
REPO_ROOT_WIN="$(cygpath -w "$REPO_ROOT" 2>/dev/null || echo "$REPO_ROOT")"
CLASSES_WIN="$(cygpath -w "$CLASSES" 2>/dev/null || echo "$CLASSES")"

build_classpath() {
    if [ ! -f "$CP_FILE" ]; then
        mkdir -p "$TARGET"
        (cd "$REPO_ROOT" && mvn -q -pl ai4j-coding -am dependency:build-classpath \
            -Dmdep.outputFile="$CP_FILE")
    fi
}

compile_bridge() {
    mkdir -p "$CLASSES"
    local stale=0
    if [ ! -f "$CLASSES/$MAIN_CLASS.class" ]; then
        stale=1
    elif [ -n "$(find "$BENCH_ROOT/java" -name '*.java' -newer "$CLASSES/$MAIN_CLASS.class" 2>/dev/null)" ]; then
        stale=1
    fi
    if [ "$stale" = "1" ]; then
        local full_cp="$REPO_ROOT_WIN/ai4j-coding/target/classes$CP_SEP$(cat "$CP_FILE")"
        # Java 8 baseline: prefer --release 8 (JDK 9+), fall back on JDK 8.
        if ! javac --release 8 -encoding UTF-8 -cp "$full_cp" -d "$CLASSES" "$SRC" 2>/dev/null; then
            javac -source 8 -target 8 -encoding UTF-8 -cp "$full_cp" -d "$CLASSES" "$SRC"
        fi
    fi
}

case "${1:-run}" in
    build)
        build_classpath
        compile_bridge
        echo "bridge ready: $CLASSES"
        ;;
    run)
        build_classpath
        compile_bridge
        # build-classpath lists dependencies only; add the coding module itself.
        local_cp="$CLASSES_WIN$CP_SEP$REPO_ROOT_WIN/ai4j-coding/target/classes$CP_SEP$(cat "$CP_FILE")"
        exec java -cp "$local_cp" "$MAIN_CLASS"
        ;;
    *)
        echo "usage: $0 [build|run]" >&2
        exit 64
        ;;
esac
