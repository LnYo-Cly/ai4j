package io.github.lnyocly.ai4j.extension.command;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ExtensionCommandRequest {

    private final String command;
    private final String arguments;
    private final Map<String, Object> attributes;

    public ExtensionCommandRequest(String command, String arguments) {
        this(command, arguments, null);
    }

    public ExtensionCommandRequest(String command, String arguments, Map<String, Object> attributes) {
        this.command = command;
        this.arguments = arguments;
        this.attributes = attributes == null ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(attributes));
    }

    public String getCommand() {
        return command;
    }

    public String getArguments() {
        return arguments;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
