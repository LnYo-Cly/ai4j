package io.github.lnyocly.ai4j.agent;

import io.github.lnyocly.ai4j.agent.event.AgentListener;

public class AgentSession {

    private final AgentRuntime runtime;
    private final AgentContext context;

    public AgentSession(AgentRuntime runtime, AgentContext context) {
        this.runtime = runtime;
        this.context = context;
    }

    public AgentResult run(String input) throws Exception {
        return runtime.run(context, AgentRequest.builder().input(input).build());
    }

    public AgentResult run(AgentRequest request) throws Exception {
        return runtime.run(context, request);
    }

    public void runStream(AgentRequest request, AgentListener listener) throws Exception {
        runtime.runStream(context, request, listener);
    }

    public AgentResult runStreamResult(AgentRequest request, AgentListener listener) throws Exception {
        return runtime.runStreamResult(context, request, listener);
    }

    public AgentContext getContext() {
        return context;
    }

    public AgentRuntime getRuntime() {
        return runtime;
    }
}
