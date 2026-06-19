package io.github.lnyocly.ai4j.agent.blueprint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Declarative single-agent blueprint loaded from YAML.
 *
 * <p>P1-A only models and validates this document. It does not create or run an
 * Agent instance.</p>
 */
public class AgentBlueprint {

    public static final String VERSION_V1 = "ai4j.agent/v1";

    private String version;
    private String id;
    private String name;
    private AgentBlueprintModel model;
    private AgentBlueprintInstructions instructions;
    private List<AgentBlueprintPlugin> plugins;
    private List<AgentBlueprintTool> tools;
    private AgentBlueprintSession session;
    private AgentBlueprintSandbox sandbox;
    private AgentBlueprintWorkflow workflow;
    private Map<String, Object> extensions;
    private List<String> unknownFields;

    public AgentBlueprint() {
        this.plugins = new ArrayList<AgentBlueprintPlugin>();
        this.tools = new ArrayList<AgentBlueprintTool>();
        this.extensions = new LinkedHashMap<String, Object>();
        this.unknownFields = new ArrayList<String>();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AgentBlueprintModel getModel() {
        return model;
    }

    public void setModel(AgentBlueprintModel model) {
        this.model = model;
    }

    public AgentBlueprintInstructions getInstructions() {
        return instructions;
    }

    public void setInstructions(AgentBlueprintInstructions instructions) {
        this.instructions = instructions;
    }

    public List<AgentBlueprintPlugin> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<AgentBlueprintPlugin> plugins) {
        this.plugins = plugins == null ? new ArrayList<AgentBlueprintPlugin>() : plugins;
    }

    public List<AgentBlueprintTool> getTools() {
        return tools;
    }

    public void setTools(List<AgentBlueprintTool> tools) {
        this.tools = tools == null ? new ArrayList<AgentBlueprintTool>() : tools;
    }

    public AgentBlueprintSession getSession() {
        return session;
    }

    public void setSession(AgentBlueprintSession session) {
        this.session = session;
    }

    public AgentBlueprintSandbox getSandbox() {
        return sandbox;
    }

    public void setSandbox(AgentBlueprintSandbox sandbox) {
        this.sandbox = sandbox;
    }

    public AgentBlueprintWorkflow getWorkflow() {
        return workflow;
    }

    public void setWorkflow(AgentBlueprintWorkflow workflow) {
        this.workflow = workflow;
    }

    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions == null ? new LinkedHashMap<String, Object>() : extensions;
    }

    public List<String> getUnknownFields() {
        return unknownFields;
    }

    public void setUnknownFields(List<String> unknownFields) {
        this.unknownFields = unknownFields == null ? new ArrayList<String>() : unknownFields;
    }
}
