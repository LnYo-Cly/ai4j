package io.github.lnyocly.ai4j.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single-turn model request.
 *
 * <p>Instances are immutable; every collection and JSON document is defensively
 * copied on construction. {@code applicationMetadata} is business correlation
 * data and never reaches the wire (design C13), while {@code providerOptions}
 * is protocol parameter data that a codec maps explicitly through its
 * whitelist.</p>
 */
public final class ModelRequest {

    private final Protocol protocol;
    private final String model;
    private final List<ModelItem> items;
    private final List<ToolDeclaration> tools;
    private final ToolChoice toolChoice;
    private final Boolean parallelToolCalls;
    private final Integer maxOutputTokens;
    private final Double temperature;
    private final Double topP;
    private final Boolean stream;
    private final Map<String, Object> applicationMetadata;
    private final Map<String, Object> providerOptions;
    private final CallExecutionOptions execution;
    private final Boolean replayable;

    private ModelRequest(Protocol protocol, String model, List<ModelItem> items,
                         List<ToolDeclaration> tools, ToolChoice toolChoice, Boolean parallelToolCalls,
                         Integer maxOutputTokens, Double temperature, Double topP, Boolean stream,
                         Map<String, Object> applicationMetadata, Map<String, Object> providerOptions,
                         CallExecutionOptions execution, Boolean replayable) {
        this.protocol = protocol;
        this.model = Objects.requireNonNull(model, "model is required");
        this.items = immutableItems(items);
        this.tools = immutableTools(tools);
        this.toolChoice = toolChoice;
        this.parallelToolCalls = parallelToolCalls;
        this.maxOutputTokens = maxOutputTokens;
        this.temperature = temperature;
        this.topP = topP;
        this.stream = stream;
        this.applicationMetadata = JsonValues.copyObject(applicationMetadata);
        this.providerOptions = JsonValues.copyObject(providerOptions);
        this.execution = execution;
        this.replayable = replayable;
    }

    private static List<ModelItem> immutableItems(List<ModelItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<ModelItem> copy = new ArrayList<ModelItem>(items.size());
        for (ModelItem item : items) {
            copy.add(Objects.requireNonNull(item, "request items must not be null"));
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<ToolDeclaration> immutableTools(List<ToolDeclaration> tools) {
        if (tools == null || tools.isEmpty()) {
            return Collections.emptyList();
        }
        List<ToolDeclaration> copy = new ArrayList<ToolDeclaration>(tools.size());
        for (ToolDeclaration tool : tools) {
            copy.add(Objects.requireNonNull(tool, "tool declarations must not be null"));
        }
        return Collections.unmodifiableList(copy);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Optional per-request protocol override; the endpoint protocol is authoritative otherwise. */
    public Protocol getProtocol() {
        return protocol;
    }

    public String getModel() {
        return model;
    }

    public List<ModelItem> getItems() {
        return items;
    }

    public List<ToolDeclaration> getTools() {
        return tools;
    }

    public ToolChoice getToolChoice() {
        return toolChoice;
    }

    public Boolean getParallelToolCalls() {
        return parallelToolCalls;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public Boolean getStream() {
        return stream;
    }

    /** Business metadata; never serialized to the wire. */
    public Map<String, Object> getApplicationMetadata() {
        return applicationMetadata;
    }

    /** Protocol parameters, mapped to the wire only through an explicit whitelist. */
    public Map<String, Object> getProviderOptions() {
        return providerOptions;
    }

    public CallExecutionOptions getExecution() {
        return execution;
    }

    /**
     * Whether this request may be retried automatically.
     *
     * <p>Derived from the replayability of all media inputs; an explicit value
     * overrides the derivation. Retry additionally requires a retryable failure
     * and budget (design 8.3).</p>
     */
    public boolean isReplayable() {
        if (replayable != null) {
            return replayable;
        }
        for (ModelItem item : items) {
            if (!(item instanceof Message)) {
                continue;
            }
            for (Block block : ((Message) item).getBlocks()) {
                if (!isBlockReplayable(block)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isBlockReplayable(Block block) {
        if (block instanceof ImageBlock) {
            return ((ImageBlock) block).getMedia().isReplayable();
        }
        if (block instanceof AudioBlock) {
            return ((AudioBlock) block).getMedia().isReplayable();
        }
        if (block instanceof VideoBlock) {
            return ((VideoBlock) block).getMedia().isReplayable();
        }
        if (block instanceof DocumentBlock) {
            return ((DocumentBlock) block).getMedia().isReplayable();
        }
        return true;
    }

    @Override
    public String toString() {
        return "ModelRequest{model=" + model + ", items=" + items.size() + ", tools=" + tools.size() + '}';
    }

    /** Fluent builder. */
    public static final class Builder {

        private Protocol protocol;
        private String model;
        private final List<ModelItem> items = new ArrayList<ModelItem>();
        private final List<ToolDeclaration> tools = new ArrayList<ToolDeclaration>();
        private ToolChoice toolChoice;
        private Boolean parallelToolCalls;
        private Integer maxOutputTokens;
        private Double temperature;
        private Double topP;
        private Boolean stream;
        private Map<String, Object> applicationMetadata;
        private final Map<String, Object> providerOptions = new LinkedHashMap<String, Object>();
        private CallExecutionOptions execution;
        private Boolean replayable;

        public Builder protocol(Protocol protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder item(ModelItem item) {
            this.items.add(Objects.requireNonNull(item, "item is required"));
            return this;
        }

        public Builder items(List<ModelItem> items) {
            if (items != null) {
                for (ModelItem item : items) {
                    item(item);
                }
            }
            return this;
        }

        public Builder message(Message message) {
            return item(message);
        }

        public Builder tool(ToolDeclaration tool) {
            this.tools.add(Objects.requireNonNull(tool, "tool is required"));
            return this;
        }

        public Builder tools(List<ToolDeclaration> tools) {
            if (tools != null) {
                for (ToolDeclaration tool : tools) {
                    tool(tool);
                }
            }
            return this;
        }

        public Builder toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        public Builder applicationMetadata(Map<String, Object> applicationMetadata) {
            this.applicationMetadata = applicationMetadata;
            return this;
        }

        public Builder providerOption(String key, Object value) {
            this.providerOptions.put(Objects.requireNonNull(key, "provider option key is required"), value);
            return this;
        }

        public Builder providerOptions(Map<String, Object> providerOptions) {
            if (providerOptions != null) {
                this.providerOptions.putAll(providerOptions);
            }
            return this;
        }

        public Builder execution(CallExecutionOptions execution) {
            this.execution = execution;
            return this;
        }

        public Builder replayable(Boolean replayable) {
            this.replayable = replayable;
            return this;
        }

        public ModelRequest build() {
            return new ModelRequest(protocol, model, items, tools, toolChoice, parallelToolCalls,
                    maxOutputTokens, temperature, topP, stream, applicationMetadata, providerOptions,
                    execution, replayable);
        }
    }
}
