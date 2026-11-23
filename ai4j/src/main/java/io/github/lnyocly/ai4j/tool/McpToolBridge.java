package io.github.lnyocly.ai4j.tool;

import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.List;

/**
 * Service provider bridge decoupling core {@link ToolUtil} from optional {@code ai4j-mcp} runtime.
 */
public interface McpToolBridge {

    List<Tool> getLocalMcpTools();

    List<Tool> getAllMcpTools(List<String> mcpServerIds);

    List<Tool> getGlobalMcpTools(List<String> serviceIds);

    List<Tool> getUserMcpTools(List<String> serviceIds, String userId);

    boolean hasLocalMcpTool(String functionName);

    String invokeLocalMcpTool(String functionName, String argument);

    boolean isGatewayInitialized();

    String invokeGlobalMcpTool(String functionName, String argument);

    String invokeUserMcpTool(String userId, String toolName, String argument);
}
