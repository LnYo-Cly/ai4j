package io.github.lnyocly.ai4j.agent.replay;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecution;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecutionStatus;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutors;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

import java.util.concurrent.CompletionStage;

/**
 * Wraps a {@link ToolExecutor} with resume-or-capture semantics over a {@link ResumeCache}.
 *
 * <p>On a tool call already seen in the cache (same name + arguments), returns the captured output
 * <b>without</b> invoking the delegate — i.e. the side effect is NOT re-performed. This is the
 * crux of safe failure recovery: re-running a crashed task must not re-execute tools that already
 * took effect (file writes, API calls, charges). On a miss, delegates and records.</p>
 */
public class ResumableToolExecutor implements AsyncToolExecutor {

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
        AgentToolExecution execution = start(call);
        AgentToolResult result = execution == null ? null : execution.await();
        return result == null ? null : result.getOutput();
    }

    @Override
    public AgentToolExecution start(AgentToolCall call) throws Exception {
        String key = ResumeCache.toolKey(call);
        String cached = cache.lookupTool(key);
        if (cached != null) {
            return AgentToolExecution.completed(result(call, cached));
        }

        AgentToolExecution started = AsyncToolExecutors.start(delegate, call);
        if (started == null) {
            return AgentToolExecution.completed(result(call, null));
        }
        AgentToolResult initial = started.isPending()
                ? started.getInitialResult() : started.await();
        if (!started.isPending()) {
            recordCompleted(key, initial);
            return AgentToolExecution.completed(initial);
        }
        CompletionStage<AgentToolResult> completion = started.getCompletion();
        if (completion == null) {
            return AgentToolExecution.of(initial, null);
        }
        CompletionStage<AgentToolResult> recorded = completion.thenApply(completed -> {
            recordCompleted(key, completed);
            return completed;
        });
        return AgentToolExecution.of(initial, recorded);
    }

    private void recordCompleted(String key, AgentToolResult result) {
        if (result != null
                && !AgentToolExecutionStatus.WAITING.equals(result.getStatus())
                && !AgentToolExecutionStatus.UNKNOWN.equals(result.getStatus())
                && result.getOutput() != null) {
            cache.recordTool(key, result.getOutput());
        }
    }

    private AgentToolResult result(AgentToolCall call, String output) {
        return AgentToolResult.builder()
                .name(call == null ? null : call.getName())
                .callId(call == null ? null : call.getCallId())
                .output(output)
                .status(AgentToolExecutionStatus.COMPLETED)
                .build();
    }
}
