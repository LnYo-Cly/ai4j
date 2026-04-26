package io.github.lnyocly.ai4j.mcp.gateway;

/**
 * MCP gateway key 规则辅助类
 */
public final class McpGatewayKeySupport {

    private McpGatewayKeySupport() {
    }

    public static String buildUserClientKey(String userId, String serviceId) {
        return "user_" + userId + "_service_" + serviceId;
    }

    public static String buildUserToolKey(String userId, String toolName) {
        return "user_" + userId + "_tool_" + toolName;
    }

    public static String buildUserPrefix(String userId) {
        return "user_" + userId + "_";
    }

    public static boolean isUserClientKey(String clientKey) {
        return clientKey != null && clientKey.startsWith("user_") && clientKey.contains("_service_");
    }

    public static String extractUserIdFromClientKey(String clientKey) {
        if (!isUserClientKey(clientKey)) {
            throw new IllegalArgumentException("不是有效的用户客户端Key: " + clientKey);
        }

        String withoutPrefix = clientKey.substring(5);
        int serviceIndex = withoutPrefix.indexOf("_service_");
        if (serviceIndex > 0) {
            return withoutPrefix.substring(0, serviceIndex);
        }

        throw new IllegalArgumentException("无法从客户端Key中提取用户ID: " + clientKey);
    }
}
