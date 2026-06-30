package io.github.lnyocly.ai4j.cli.hook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User-declared external hooks. Loaded from the workspace config (JSON), so end users configure
 * hook commands without writing Java. v1 supports {@code preToolUse} (Claude Code's PreToolUse).
 *
 * <p>Example workspace config:</p>
 * <pre>
 * "hooks": {
 *   "preToolUse": [
 *     { "command": "python ~/.ai4j/hooks/guard.py", "match": "bash" }
 *   ]
 * }
 * </pre>
 */
public class CliHooksConfig {

    private List<CliHookEntry> preToolUse;

    public CliHooksConfig() {
    }

    public List<CliHookEntry> getPreToolUse() {
        return preToolUse == null ? Collections.<CliHookEntry>emptyList() : new ArrayList<CliHookEntry>(preToolUse);
    }

    public void setPreToolUse(List<CliHookEntry> preToolUse) {
        this.preToolUse = preToolUse == null ? null : new ArrayList<CliHookEntry>(preToolUse);
    }

    public boolean isEmpty() {
        return preToolUse == null || preToolUse.isEmpty();
    }
}
