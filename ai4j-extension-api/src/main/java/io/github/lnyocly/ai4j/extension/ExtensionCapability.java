package io.github.lnyocly.ai4j.extension;

import java.util.Locale;

public enum ExtensionCapability {
    TOOL("tool"),
    COMMAND("command"),
    SKILL("skill"),
    PROMPT("prompt"),
    GUARDRAIL("guardrail"),
    LIFECYCLE("lifecycle"),
    MEMORY_STORE("memory-store"),
    COMPACT_POLICY("compact-policy"),
    CONTEXT_PROJECTOR("context-projector"),
    SANDBOX_PROVIDER("sandbox-provider"),
    RUNNER_PROVIDER("runner-provider"),
    UI("ui");

    private final String id;

    ExtensionCapability(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static ExtensionCapability fromId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("extension capability must not be blank");
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (ExtensionCapability capability : values()) {
            if (capability.id.equals(normalized)) {
                return capability;
            }
        }
        throw new IllegalArgumentException("unsupported extension capability: " + id);
    }
}
