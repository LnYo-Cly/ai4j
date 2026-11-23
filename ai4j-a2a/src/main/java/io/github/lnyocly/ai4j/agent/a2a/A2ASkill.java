package io.github.lnyocly.ai4j.agent.a2a;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A2A Skill definition — describes an agent's capability in the AgentCard.
 *
 * <p>Structure used by the current ai4j A2A contract:</p>
 * <ul>
 *   <li>{@code id}: Stable skill identifier for standard A2A cards</li>
 *   <li>{@code name}: Skill name (required)</li>
 *   <li>{@code description}: Skill description (required)</li>
 *   <li>{@code tags}, {@code examples}, {@code inputModes}, {@code outputModes}: Standard
 *       A2A metadata</li>
 *   <li>{@code inputSchema}: JSON Schema for skill input (optional)</li>
 * </ul>
 */
public class A2ASkill {

    private String id;
    private String name;
    private String description;
    private Map<String, Object> inputSchema; // Optional JSON Schema
    private List<String> tags;
    private List<String> examples;
    private List<String> inputModes;
    private List<String> outputModes;

    public A2ASkill() {
        this.tags = new ArrayList<String>();
        this.examples = new ArrayList<String>();
        this.inputModes = new ArrayList<String>();
        this.outputModes = new ArrayList<String>();
    }

    public A2ASkill(String name, String description) {
        this();
        this.name = name;
        this.description = description;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getInputSchema() { return inputSchema; }
    public void setInputSchema(Map<String, Object> inputSchema) { this.inputSchema = inputSchema; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) {
        this.tags = tags != null ? tags : new ArrayList<String>();
    }

    public List<String> getExamples() { return examples; }
    public void setExamples(List<String> examples) {
        this.examples = examples != null ? examples : new ArrayList<String>();
    }

    public List<String> getInputModes() { return inputModes; }
    public void setInputModes(List<String> inputModes) {
        this.inputModes = inputModes != null ? inputModes : new ArrayList<String>();
    }

    public List<String> getOutputModes() { return outputModes; }
    public void setOutputModes(List<String> outputModes) {
        this.outputModes = outputModes != null ? outputModes : new ArrayList<String>();
    }
}
