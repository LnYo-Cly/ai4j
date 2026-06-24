package io.github.lnyocly.ai4j.agent.replay;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

/**
 * Wraps a {@link ToolExecutor} with resume-or-capture semantics over a {@link ResumeCache}.
 *
 * <p>On a tool call already seen in the cache (same name + arguments), returns the captured output
 * <b>without</b> invoking the delegate — i.e. the side effect is NOT re-performed. This is the
 * crux of safe failure recovery: re-running a crashed task must not re-execute tools that already
 * took effect (file writes, API calls, charges). On a miss, delegates and records.</p>
 */
public class ResumableToolExecutor implements ToolExecutor {

    private final ToolExecutor delegate;
    private final ResumeCache cache;

    public ResumableToolExecutor(ToolExecutor delegate, ResumeCache cache) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate tool executor must not be null");
        }
        if (cache == null) {
            throw new IllegalArgumentException("resume cache must not be null");
        }
        this.delegate = delegate;
        this.cache = cache;
    }

    public ResumeCache getCache() {
        return cache;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        String key = ResumeCache.toolKey(call);
        String cached = cache.lookupTool(key);
        if (cached != null) {
            return cached;
        }
        String output = delegate.execute(call);
        cache.recordTool(key, output);
        return output;
    }
}
