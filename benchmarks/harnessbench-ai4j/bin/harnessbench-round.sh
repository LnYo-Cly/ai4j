#!/usr/bin/env bash
# HarnessBench generic_cli round runner for stock agent CLIs (comparison arms).
#
# generic_cli invokes: harnessbench-round.sh <tool> <prompt_file> <session_id>
# with cwd = benchmark workspace. Round continuity:
#   pi       -> --session-id <bench session>  (same session across rounds)
#   opencode -> -c (continue last session for this project dir)
#   hermes   -> --continue (last session in the sandbox-local HERMES_HOME)
# Exit code becomes the round result (0 = round completed).
set -euo pipefail

TOOL="${1:?tool required}"
PROMPT_FILE="${2:?prompt_file required}"
SESSION_ID="${3:?session_id required}"

MODEL="${AGENT_ROUND_MODEL:-gpt-5.6-terra}"
THINKING="${AGENT_ROUND_THINKING:-medium}"
SANDBOX_DIR="$(cd "$(dirname "$PROMPT_FILE")" && pwd)"
HERMES_BIN="${HERMES_BIN:-C:/Users/1/AppData/Local/hermes/bin/hermes.exe}"

case "$TOOL" in
    opencode)
        PROMPT="$(cat "$PROMPT_FILE")"
        # --pure: no external plugins — raw agent, same as the other arms.
        if [ "${AGENT_ROUND_CONTINUE:-1}" = "1" ]; then
            if ! opencode run --pure -m "trovebox/$MODEL" -c "$PROMPT" 2>/dev/null; then
                # First round in this workspace: nothing to continue yet.
                opencode run --pure -m "trovebox/$MODEL" "$PROMPT"
            fi
        else
            opencode run --pure -m "trovebox/$MODEL" "$PROMPT"
        fi
        ;;
    pi)
        PROMPT="$(cat "$PROMPT_FILE")"
        exec pi -p --thinking "$THINKING" --provider trovebox --model "$MODEL" \
            --session-id "$SESSION_ID" --no-skills --no-extensions "$PROMPT"
        ;;
    hermes)
        export HERMES_HOME="$SANDBOX_DIR/ai4j-state/hermes-home"
        mkdir -p "$HERMES_HOME"
        # hermes reads $HERMES_HOME/config.yaml; a fresh home without one
        # falls back to its default provider flow.
        cp "G:/My_Project/java/ai4j-sdk/.tmp/harness-bench/config/hermes-luna/config.yaml" \
           "$HERMES_HOME/config.yaml"
        if [ "${AGENT_ROUND_CONTINUE:-1}" = "1" ]; then
            if ! "$HERMES_BIN" chat --oneshot --query-file "$PROMPT_FILE" \
                    --reasoning "$THINKING" --continue 2>/dev/null; then
                # First round: no session to continue yet.
                "$HERMES_BIN" chat --oneshot --query-file "$PROMPT_FILE" \
                    --reasoning "$THINKING"
            fi
        else
            "$HERMES_BIN" chat --oneshot --query-file "$PROMPT_FILE" \
                --reasoning "$THINKING"
        fi
        ;;
    *)
        echo "unknown tool: $TOOL (opencode|pi|hermes)" >&2
        exit 64
        ;;
esac
