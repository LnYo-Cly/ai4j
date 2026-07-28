package io.github.lnyocly.ai4j.extension.lifecycle;

import io.github.lnyocly.ai4j.extension.api.annotation.Experimental;

@Experimental(since = "2.4.3")
public interface LifecycleHookRegistry {

    void register(AgentLifecycleHook hook);
}
