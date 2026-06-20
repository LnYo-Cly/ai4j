package io.github.lnyocly.ai4j.agent.lifecycle;

import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEvent;

public final class AgentLifecycleHookError {

    private final String hookName;
    private final AgentLifecycleEvent event;
    private final String errorType;
    private final String message;

    public AgentLifecycleHookError(String hookName, AgentLifecycleEvent event, Throwable error) {
        this.hookName = hookName;
        this.event = event;
        this.errorType = error == null ? null : error.getClass().getName();
        this.message = error == null ? null : error.getMessage();
    }

    public String getHookName() {
        return hookName;
    }

    public AgentLifecycleEvent getEvent() {
        return event;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getMessage() {
        return message;
    }
}
