package io.github.lnyocly.ai4j.agent.a2a;

import java.util.Map;

/**
 * A2A Skill definition — describes an agent's capability in the AgentCard.
 *
 * <p>Structure used by the current ai4j A2A contract:</p>
 * <ul>
 *   <li>{@code name}: Skill name (required)</li>
 *   <li>{@code description}: Skill description (required)</li>
 *   <li>{@code inputSchema}: JSON Schema for skill input (optional)</li>
 * </ul>
 */
public class A2ASkill {

    private String name;
    private String description;
    private Map<String, Object> inputSchema; // Optional JSON Schema

    public A2ASkill() {
    }

    public A2ASkill(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getInputSchema() { return inputSchema; }
    public void setInputSchema(Map<String, Object> inputSchema) { this.inputSchema = inputSchema; }
}
