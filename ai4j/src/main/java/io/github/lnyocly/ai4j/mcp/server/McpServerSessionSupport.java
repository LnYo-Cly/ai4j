package io.github.lnyocly.ai4j.mcp.server;

import java.util.Map;
import java.util.Random;
import java.util.function.Function;

/**
 * MCP 服务端会话辅助方法
 */
public final class McpServerSessionSupport {

    private McpServerSessionSupport() {
    }

    public static String generateSessionId(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + Integer.toHexString(new Random().nextInt());
    }

    public static <T extends McpServerSessionState> T getOrCreateSession(
            Map<String, T> sessions,
            String sessionId,
            String sessionPrefix,
            Function<String, T> sessionFactory) {
        String resolvedSessionId = sessionId;
        if (resolvedSessionId == null) {
            resolvedSessionId = generateSessionId(sessionPrefix);
        }

        T existing = sessions.get(resolvedSessionId);
        if (existing != null) {
            return existing;
        }

        T newSession = sessionFactory.apply(resolvedSessionId);
        sessions.put(resolvedSessionId, newSession);
        return newSession;
    }
}
