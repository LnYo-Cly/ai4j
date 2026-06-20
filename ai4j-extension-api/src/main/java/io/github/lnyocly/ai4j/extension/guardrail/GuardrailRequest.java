package io.github.lnyocly.ai4j.extension.guardrail;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GuardrailRequest {

    private final String action;
    private final String target;
    private final Map<String, Object> attributes;

    public GuardrailRequest(String action, String target) {
        this(action, target, null);
    }

    public GuardrailRequest(String action, String target, Map<String, Object> attributes) {
        this.action = action;
        this.target = target;
        this.attributes = attributes == null ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(attributes));
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
