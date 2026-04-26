package io.github.lnyocly.ai4j.flowgram.springboot.security;

import io.github.lnyocly.ai4j.flowgram.springboot.support.FlowGramStoredTask;

public class DefaultFlowGramAccessChecker implements FlowGramAccessChecker {

    @Override
    public boolean isAllowed(FlowGramAction action, FlowGramCaller caller, FlowGramStoredTask task) {
        return true;
    }
}
