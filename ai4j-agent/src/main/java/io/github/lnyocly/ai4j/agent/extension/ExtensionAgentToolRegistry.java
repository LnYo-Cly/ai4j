package io.github.lnyocly.ai4j.agent.extension;

import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExtensionAgentToolRegistry implements AgentToolRegistry {

    private final List<Tool> tools;

    public ExtensionAgentToolRegistry(ExtensionRuntimeSnapshot snapshot) {
        this(snapshot == null ? null : snapshot.getTools());
    }

    public ExtensionAgentToolRegistry(List<ExtensionToolSpec> specs) {
        if (specs == null || specs.isEmpty()) {
            this.tools = Collections.emptyList();
            return;
        }
        List<Tool> mapped = new ArrayList<Tool>();
        for (ExtensionToolSpec spec : specs) {
            mapped.add(ExtensionAgentToolSchemaMapper.toAgentTool(spec));
        }
        this.tools = Collections.unmodifiableList(mapped);
    }

    @Override
    public List<Object> getTools() {
        return new ArrayList<Object>(tools);
    }
}
