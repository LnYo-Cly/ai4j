package io.github.lnyocly.ai4j.agent;

import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;

import java.util.function.Supplier;

public class Agent {

    private final AgentRuntime runtime;
    private final AgentContext baseContext;
    private final Supplier<AgentMemory> memorySupplier;

    public Agent(AgentRuntime runtime, AgentContext baseContext, Supplier<AgentMemory> memorySupplier) {
        this.runtime = runtime;
        this.baseContext = baseContext;
        this.memorySupplier = memorySupplier;
    }

    public AgentResult run(AgentRequest request) throws Exception {
        return runtime.run(baseContext, request);
    }

    public void runStream(AgentRequest request, AgentListener listener) throws Exception {
        runtime.runStream(baseContext, request, listener);
    }

    public AgentResult runStreamResult(AgentRequest request, AgentListener listener) throws Exception {
        return runtime.runStreamResult(baseContext, request, listener);
    }

    public AgentSession newSession() {
        AgentMemory memory = memorySupplier == null ? baseContext.getMemory() : memorySupplier.get();
        AgentContext sessionContext = baseContext.toBuilder().memory(memory).build();
        return new AgentSession(runtime, sessionContext);
    }
}
