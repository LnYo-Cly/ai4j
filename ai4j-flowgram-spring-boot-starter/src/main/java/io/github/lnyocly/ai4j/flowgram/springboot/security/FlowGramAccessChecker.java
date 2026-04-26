package io.github.lnyocly.ai4j.flowgram.springboot.security;

import io.github.lnyocly.ai4j.flowgram.springboot.support.FlowGramStoredTask;

public interface FlowGramAccessChecker {

    boolean isAllowed(FlowGramAction action, FlowGramCaller caller, FlowGramStoredTask task);
}
