package io.github.lnyocly.ai4j.extension;

import java.util.Locale;

/**
 * Stable manifest-level contribution categories for extension authors.
 *
 * <p>{@link ExtensionCapability} remains the runtime registration gate for
 * first-class registries such as tools and lifecycle hooks. Contribution types
 * are broader: they let a package describe provider-style or host-integrated
 * contributions before every category has a dedicated runtime registry.</p>
 */
public enum ExtensionContributionType {
    TOOL("tool", ExtensionCapability.TOOL),
    COMMAND("command", ExtensionCapability.COMMAND),
    SKILL("skill", ExtensionCapability.SKILL),
    PROMPT("prompt", ExtensionCapability.PROMPT),
    GUARDRAIL("guardrail", ExtensionCapability.GUARDRAIL),
    LIFECYCLE("lifecycle", ExtensionCapability.LIFECYCLE),
    MEMORY_STORE("memory-store", ExtensionCapability.MEMORY_STORE),
    COMPACT_POLICY("compact-policy", ExtensionCapability.COMPACT_POLICY),
    CONTEXT_PROJECTOR("context-projector", ExtensionCapability.CONTEXT_PROJECTOR),
    SANDBOX_PROVIDER("sandbox-provider", ExtensionCapability.SANDBOX_PROVIDER),
    RUNNER_PROVIDER("runner-provider", ExtensionCapability.RUNNER_PROVIDER),
    CLI_COMMAND("cli-command", ExtensionCapability.COMMAND),
    UI("ui", ExtensionCapability.UI);

    private final String id;
    private final ExtensionCapability runtimeCapability;

    ExtensionContributionType(String id, ExtensionCapability runtimeCapability) {
        this.id = id;
        this.runtimeCapability = runtimeCapability;
    }

    public String getId() {
        return id;
    }

    public ExtensionCapability getRuntimeCapability() {
        return runtimeCapability;
    }

    public boolean hasRuntimeCapability() {
        return runtimeCapability != null;
    }

    public static ExtensionContributionType fromId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("extension contribution type must not be blank");
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (ExtensionContributionType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unsupported extension contribution type: " + id);
    }
}
