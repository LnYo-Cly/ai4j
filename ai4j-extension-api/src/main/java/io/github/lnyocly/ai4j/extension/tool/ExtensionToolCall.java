package io.github.lnyocly.ai4j.extension.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ExtensionToolCall {

    private final String name;
    private final String arguments;
    private final Map<String, Object> attributes;

    public ExtensionToolCall(String name, String arguments) {
        this(name, arguments, null);
    }

    public ExtensionToolCall(String name, String arguments, Map<String, Object> attributes) {
        this.name = name;
        this.arguments = arguments;
        this.attributes = attributes == null ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(attributes));
    }

    public String getName() {
        return name;
    }

    public String getArguments() {
        return arguments;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
