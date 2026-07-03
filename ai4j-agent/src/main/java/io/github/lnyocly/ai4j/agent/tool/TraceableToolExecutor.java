package io.github.lnyocly.ai4j.agent.tool;

/**
 * Optional super-interface for {@link ToolExecutor}s that also produce a structured sub-trace
 * (e.g. a RAG retrieval exposing retrievedHits / rerankedHits). The runtime captures
 * {@link #lastTrace()} into {@link AgentToolResult#getTrace()}, which flows into IoCapture so a
 * TOOL node shows the tool's internal steps, not just its final string output.
 *
 * <p>Concurrency: implementations MUST make {@code lastTrace()} return the trace produced by the
 * most recent {@link #execute(AgentToolCall)} on the <em>same thread</em> (use a {@link
 * ThreadLocal}), because the runtime may execute several tool calls in parallel on the same
 * executor instance.</p>
 */
public interface TraceableToolExecutor extends ToolExecutor {

    /** The sub-trace produced by the last {@link #execute(AgentToolCall)} on this thread, or null. */
    Object lastTrace();
}
