package io.github.lnyocly.ai4j.flowgram.springboot.security;

public interface FlowGramTaskOwnershipStrategy {

    FlowGramTaskOwnership createOwnership(String taskId, FlowGramCaller caller);
}
