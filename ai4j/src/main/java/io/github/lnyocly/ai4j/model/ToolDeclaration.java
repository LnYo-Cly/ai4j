package io.github.lnyocly.ai4j.model;

import java.util.Map;
import java.util.Objects;

/**
 * A single tool declaration: the model-visible shape plus session binding
 * information. It never encodes who executes the tool (design 6.1 / C12).
 *
 * <p>{@code parameters} is a plain JSON Schema document carried as
 * {@code Map<String,Object>} (design F2); no dedicated schema type exists.</p>
 */
public final class ToolDeclaration {

    private final ToolKind kind;
    private final String name;
    private final String description;
    private final Map<String, Object> parameters;
    private final Boolean strict;
    private final ToolVisibility visibility;
    private final Map<String, Object> nativeSpec;

    private ToolDeclaration(ToolKind kind, String name, String description,
                            Map<String, Object> parameters, Boolean strict,
                            ToolVisibility visibility, Map<String, Object> nativeSpec) {
        this.kind = Objects.requireNonNull(kind, "kind is required");
        this.name = name;
        this.description = description;
        this.parameters = JsonValues.copyObject(parameters);
        this.strict = strict;
        this.visibility = visibility == null ? ToolVisibility.ALWAYS : visibility;
        this.nativeSpec = JsonValues.copyObject(nativeSpec);
        if (kind == ToolKind.FUNCTION && (name == null || name.trim().isEmpty())) {
            throw new IllegalArgumentException("function declarations require a name");
        }
    }

    /** Function declaration builder, matching the design 6.1 usage shape. */
    public static Builder function(String name) {
        return new Builder(ToolKind.FUNCTION, name);
    }

    public static Builder builder(ToolKind kind, String name) {
        return new Builder(kind, name);
    }

    /** Protocol-shape-specific fields ({@code type}/{@code input_schema}/{@code server_url}/...). */
    public static ToolDeclaration nativeTool(String name, Map<String, Object> nativeSpec) {
        return new Builder(ToolKind.NATIVE, name).nativeSpec(nativeSpec).build();
    }

    public ToolKind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /** Function input JSON Schema document; empty when not declared. */
    public Map<String, Object> getParameters() {
        return parameters;
    }

    /** Strict-schema flag for protocols that support it, or {@code null}. */
    public Boolean getStrict() {
        return strict;
    }

    public ToolVisibility getVisibility() {
        return visibility;
    }

    public Map<String, Object> getNativeSpec() {
        return nativeSpec;
    }

    /** Fluent builder. */
    public static final class Builder {

        private final ToolKind kind;
        private final String name;
        private String description;
        private Map<String, Object> parameters;
        private Boolean strict;
        private ToolVisibility visibility;
        private Map<String, Object> nativeSpec;

        private Builder(ToolKind kind, String name) {
            this.kind = kind;
            this.name = name;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder strict(Boolean strict) {
            this.strict = strict;
            return this;
        }

        public Builder visibility(ToolVisibility visibility) {
            this.visibility = visibility;
            return this;
        }

        public Builder nativeSpec(Map<String, Object> nativeSpec) {
            this.nativeSpec = nativeSpec;
            return this;
        }

        public ToolDeclaration build() {
            return new ToolDeclaration(kind, name, description, parameters, strict, visibility, nativeSpec);
        }
    }
}
