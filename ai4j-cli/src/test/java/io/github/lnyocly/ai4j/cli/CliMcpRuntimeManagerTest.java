package io.github.lnyocly.ai4j.cli.mcp;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.mcp.entity.McpToolDefinition;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CliMcpRuntimeManagerTest {

    @Test
    public void connectedServerRegistersToolsAndRoutesCalls() throws Exception {
        FakeClientSession timeSession = new FakeClientSession(Collections.singletonList(tool("time_now", "Read current time")));
        CliMcpRuntimeManager runtimeManager = new CliMcpRuntimeManager(
                resolvedConfig(
                        Collections.singletonMap("time", activeServer("time", "stdio", "uvx")),
                        Collections.singletonList("time"),
                        Collections.<String>emptyList(),
                        Collections.<String>emptyList()
                ),
                new FakeClientFactory(Collections.singletonMap("time", timeSession))
        );

        runtimeManager.start();
        Assert.assertNotNull(runtimeManager.getToolRegistry());
        Assert.assertNotNull(runtimeManager.getToolExecutor());
        Assert.assertEquals(1, runtimeManager.getToolRegistry().getTools().size());
        Assert.assertEquals(1, runtimeManager.getStatuses().size());
        Assert.assertEquals(CliMcpRuntimeManager.STATE_CONNECTED, runtimeManager.getStatuses().get(0).getState());
        Assert.assertEquals(1, runtimeManager.getStatuses().get(0).getToolCount());

        String result = runtimeManager.getToolExecutor().execute(AgentToolCall.builder()
                .name("time_now")
                .arguments("{\"timezone\":\"Asia/Shanghai\"}")
                .build());

        Assert.assertEquals("ok:time_now", result);
        Assert.assertTrue(timeSession.connected);
        Assert.assertEquals("time_now", timeSession.lastToolName);
        Assert.assertEquals("Asia/Shanghai", String.valueOf(timeSession.lastArguments.get("timezone")));

        runtimeManager.close();
        Assert.assertTrue(timeSession.closed);
    }

    @Test
    public void failedAndConflictingServersDoNotBlockHealthyServer() throws Exception {
        FakeClientSession fetchSession = new FakeClientSession(Collections.singletonList(tool("search_web", "Search the web")));
        FakeClientSession duplicateSession = new FakeClientSession(Collections.singletonList(tool("search_web", "Duplicate tool")));
        FakeClientSession pausedSession = new FakeClientSession(Collections.singletonList(tool("paused_tool", "Paused tool")));

        Map<String, CliResolvedMcpServer> servers = new LinkedHashMap<String, CliResolvedMcpServer>();
        servers.put("fetch", activeServer("fetch", "sse", null));
        servers.put("broken", activeServer("broken", "sse", null));
        servers.put("duplicate", activeServer("duplicate", "streamable_http", null));
        servers.put("paused", pausedServer("paused", "stdio", "uvx"));
        servers.put("disabled", disabledServer("disabled", "stdio", "uvx"));

        Map<String, FakeClientSession> sessions = new LinkedHashMap<String, FakeClientSession>();
        sessions.put("fetch", fetchSession);
        sessions.put("duplicate", duplicateSession);
        sessions.put("paused", pausedSession);

        CliMcpRuntimeManager runtimeManager = new CliMcpRuntimeManager(
                resolvedConfig(
                        servers,
                        Arrays.asList("fetch", "broken", "duplicate", "paused"),
                        Collections.singletonList("paused"),
                        Collections.singletonList("missing")
                ),
                new FakeClientFactory(sessions, Collections.singletonMap("broken", new IllegalStateException("connect failed")))
        );

        runtimeManager.start();

        Map<String, CliMcpStatusSnapshot> statuses = indexStatuses(runtimeManager.getStatuses());
        Assert.assertEquals(CliMcpRuntimeManager.STATE_CONNECTED, statuses.get("fetch").getState());
        Assert.assertEquals(CliMcpRuntimeManager.STATE_ERROR, statuses.get("broken").getState());
        Assert.assertTrue(statuses.get("broken").getErrorSummary().contains("connect failed"));
        Assert.assertEquals(CliMcpRuntimeManager.STATE_ERROR, statuses.get("duplicate").getState());
        Assert.assertTrue(statuses.get("duplicate").getErrorSummary().contains("search_web"));
        Assert.assertEquals(CliMcpRuntimeManager.STATE_PAUSED, statuses.get("paused").getState());
        Assert.assertEquals(CliMcpRuntimeManager.STATE_DISABLED, statuses.get("disabled").getState());
        Assert.assertEquals(CliMcpRuntimeManager.STATE_MISSING, statuses.get("missing").getState());

        Assert.assertNotNull(runtimeManager.getToolRegistry());
        Assert.assertEquals(1, runtimeManager.getToolRegistry().getTools().size());
        Assert.assertEquals("ok:search_web", runtimeManager.getToolExecutor().execute(AgentToolCall.builder()
                .name("search_web")
                .arguments("{}")
                .build()));
        Assert.assertEquals(Arrays.asList(
                "MCP unavailable: broken (connect failed)",
                "MCP unavailable: duplicate (MCP tool name conflict: search_web already provided by fetch)",
                "MCP unavailable: missing (workspace references undefined MCP server)"
        ), runtimeManager.buildStartupWarnings());

        runtimeManager.close();
        Assert.assertTrue(fetchSession.closed);
        Assert.assertTrue(duplicateSession.closed);
        Assert.assertFalse(pausedSession.connected);
    }

    private Map<String, CliMcpStatusSnapshot> indexStatuses(List<CliMcpStatusSnapshot> statuses) {
        Map<String, CliMcpStatusSnapshot> index = new LinkedHashMap<String, CliMcpStatusSnapshot>();
        for (CliMcpStatusSnapshot status : statuses) {
            if (status != null) {
                index.put(status.getServerName(), status);
            }
        }
        return index;
    }

    private CliResolvedMcpConfig resolvedConfig(Map<String, CliResolvedMcpServer> servers,
                                                List<String> enabled,
                                                List<String> paused,
                                                List<String> missing) {
        return new CliResolvedMcpConfig(servers, enabled, paused, missing);
    }

    private CliResolvedMcpServer activeServer(String name, String type, String command) {
        CliMcpServerDefinition definition = CliMcpServerDefinition.builder()
                .type(type)
                .command(command)
                .url(command == null ? "https://example.com/" + name : null)
                .build();
        return new CliResolvedMcpServer(name, type, true, false, true, null, definition);
    }

    private CliResolvedMcpServer pausedServer(String name, String type, String command) {
        CliMcpServerDefinition definition = CliMcpServerDefinition.builder()
                .type(type)
                .command(command)
                .build();
        return new CliResolvedMcpServer(name, type, true, true, false, null, definition);
    }

    private CliResolvedMcpServer disabledServer(String name, String type, String command) {
        CliMcpServerDefinition definition = CliMcpServerDefinition.builder()
                .type(type)
                .command(command)
                .build();
        return new CliResolvedMcpServer(name, type, false, false, false, null, definition);
    }

    private McpToolDefinition tool(String name, String description) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        Map<String, Object> timezone = new LinkedHashMap<String, Object>();
        timezone.put("type", "string");
        timezone.put("description", "Timezone");
        properties.put("timezone", timezone);
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", Collections.singletonList("timezone"));
        return McpToolDefinition.builder()
                .name(name)
                .description(description)
                .inputSchema(schema)
                .build();
    }

    private static final class FakeClientFactory implements CliMcpRuntimeManager.ClientFactory {

        private final Map<String, FakeClientSession> sessions;
        private final Map<String, RuntimeException> failures;

        private FakeClientFactory(Map<String, FakeClientSession> sessions) {
            this(sessions, Collections.<String, RuntimeException>emptyMap());
        }

        private FakeClientFactory(Map<String, FakeClientSession> sessions,
                                  Map<String, RuntimeException> failures) {
            this.sessions = sessions == null ? Collections.<String, FakeClientSession>emptyMap() : sessions;
            this.failures = failures == null ? Collections.<String, RuntimeException>emptyMap() : failures;
        }

        @Override
        public CliMcpRuntimeManager.ClientSession create(CliResolvedMcpServer server) {
            if (server != null && failures.containsKey(server.getName())) {
                throw failures.get(server.getName());
            }
            FakeClientSession session = sessions.get(server == null ? null : server.getName());
            if (session == null) {
                throw new IllegalStateException("missing fake session for " + (server == null ? null : server.getName()));
            }
            return session;
        }
    }

    private static final class FakeClientSession implements CliMcpRuntimeManager.ClientSession {

        private final List<McpToolDefinition> tools;
        private boolean connected;
        private boolean closed;
        private String lastToolName;
        private Map<String, Object> lastArguments;

        private FakeClientSession(List<McpToolDefinition> tools) {
            this.tools = tools == null ? Collections.<McpToolDefinition>emptyList() : new ArrayList<McpToolDefinition>(tools);
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public List<McpToolDefinition> listTools() {
            return new ArrayList<McpToolDefinition>(tools);
        }

        @Override
        @SuppressWarnings("unchecked")
        public String callTool(String toolName, Object arguments) {
            this.lastToolName = toolName;
            this.lastArguments = arguments instanceof Map
                    ? new LinkedHashMap<String, Object>((Map<String, Object>) arguments)
                    : Collections.<String, Object>emptyMap();
            return "ok:" + toolName;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
