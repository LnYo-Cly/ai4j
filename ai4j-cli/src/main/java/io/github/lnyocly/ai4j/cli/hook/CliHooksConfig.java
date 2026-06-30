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
    private List<CliHookEntry> postToolUse;
    private List<CliHookEntry> userPromptSubmit;
    private List<CliHookEntry> stop;
    private List<CliHookEntry> preCompact;
    private List<CliHookEntry> sessionStart;
    private List<CliHookEntry> sessionEnd;

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

    public List<CliHookEntry> getPostToolUse() {
        return postToolUse == null ? Collections.<CliHookEntry>emptyList() : new ArrayList<CliHookEntry>(postToolUse);
    }

    public void setPostToolUse(List<CliHookEntry> postToolUse) {
        this.postToolUse = postToolUse == null ? null : new ArrayList<CliHookEntry>(postToolUse);
    }

    public List<CliHookEntry> getStop() {
        return stop == null ? Collections.<CliHookEntry>emptyList() : new ArrayList<CliHookEntry>(stop);
    }

    public void setStop(List<CliHookEntry> stop) {
        this.stop = stop == null ? null : new ArrayList<CliHookEntry>(stop);
    }

    public List<CliHookEntry> getPreCompact() {
        return preCompact == null ? Collections.<CliHookEntry>emptyList() : new ArrayList<CliHookEntry>(preCompact);
    }

    public void setPreCompact(List<CliHookEntry> preCompact) {
        this.preCompact = preCompact == null ? null : new ArrayList<CliHookEntry>(preCompact);
    }

    public List<CliHookEntry> getSessionStart() {
        return sessionStart == null ? Collections.<CliHookEntry>emptyList() : new ArrayList<CliHookEntry>(sessionStart);
    }

    public void setSessionStart(List<CliHookEntry> sessionStart) {
        this.sessionStart = sessionStart == null ? null : new ArrayList<CliHookEntry>(sessionStart);
    }

    public List<CliHookEntry> getSessionEnd() {
        return sessionEnd == null ? Collections.<CliHookEntry>emptyList() : new ArrayList<CliHookEntry>(sessionEnd);
    }

    public void setSessionEnd(List<CliHookEntry> sessionEnd) {
        this.sessionEnd = sessionEnd == null ? null : new ArrayList<CliHookEntry>(sessionEnd);
    }

    public boolean isEmpty() {
        return (preToolUse == null || preToolUse.isEmpty())
                && (postToolUse == null || postToolUse.isEmpty())
                && (userPromptSubmit == null || userPromptSubmit.isEmpty())
                && (stop == null || stop.isEmpty())
                && (preCompact == null || preCompact.isEmpty())
                && (sessionStart == null || sessionStart.isEmpty())
                && (sessionEnd == null || sessionEnd.isEmpty());
    }

    public boolean hasPromptHooks() {
        return userPromptSubmit != null && !userPromptSubmit.isEmpty();
    }

    /** Observe/side-effect events routed through the lifecycle hook bridge. */
    public boolean hasObserveHooks() {
        return (stop != null && !stop.isEmpty())
                || (preCompact != null && !preCompact.isEmpty())
                || (sessionStart != null && !sessionStart.isEmpty())
                || (sessionEnd != null && !sessionEnd.isEmpty());
    }
}
