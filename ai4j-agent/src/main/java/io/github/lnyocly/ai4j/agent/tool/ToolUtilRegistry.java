package io.github.lnyocly.ai4j.agent.tool;

import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.tool.ToolUtil;

import java.util.ArrayList;
import java.util.List;

public class ToolUtilRegistry implements AgentToolRegistry {

    private final List<String> functionList;
    private final List<String> mcpServerIds;

    public ToolUtilRegistry(List<String> functionList, List<String> mcpServerIds) {
        this.functionList = functionList;
        this.mcpServerIds = mcpServerIds;
    }

    @Override
    public List<Object> getTools() {
        List<Tool> tools = ToolUtil.getAllTools(functionList, mcpServerIds);
        return new ArrayList<Object>(tools);
    }
}

