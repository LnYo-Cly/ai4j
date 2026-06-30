package io.github.lnyocly.ai4j.cli.hook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User-declared external hooks. Loaded from the workspace config (JSON), so end users configure
 * hook commands without writing Java. Supports {@code preToolUse} (Claude Code's PreToolUse) and
 * {@code userPromptSubmit} (Claude Code's UserPromptSubmit).
 *
 * <p>Example workspace config:</p>
 * <pre>
 * "hooks": {
 *   "preToolUse": [
 *     { "command": "python ~/.ai4j/hooks/guard.py", "match": "bash" }
 *   ],
 *   "userPromptSubmit": [
 *     { "command": "python ~/.ai4j/hooks/prompt_guard.py" }
 *   ]
 * }
 * </pre>
 */
public class CliHooksConfig {

    private List<CliHookEntry> preToolUse;
    private List<CliHookEntry> userPromptSubmit;

    public CliHooksConfig() {
    }

    public List<CliHookEntry> getPreToolUse() {
        return preToolUse == null ? Collections.<CliHookEntry>emptyList() : new ArrayList<CliHookEntry>(preToolUse);
    }

    public void setPreToolUse(List<CliHookEntry> preToolUse) {
        this.preToolUse = preToolUse == null ? null : new ArrayList<CliHookEntry>(preToolUse);
    }

    public List<CliHookEntry> getUserPromptSubmit() {
        return userPromptSubmit == null ? Collections.<CliHookEntry>emptyList() : new ArrayList<CliHookEntry>(userPromptSubmit);
    }

    public void setUserPromptSubmit(List<CliHookEntry> userPromptSubmit) {
        this.userPromptSubmit = userPromptSubmit == null ? null : new ArrayList<CliHookEntry>(userPromptSubmit);
    }

    public boolean isEmpty() {
        return (preToolUse == null || preToolUse.isEmpty())
                && (userPromptSubmit == null || userPromptSubmit.isEmpty());
    }

    public boolean hasPromptHooks() {
        return userPromptSubmit != null && !userPromptSubmit.isEmpty();
    }
}
