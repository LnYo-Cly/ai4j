package io.github.lnyocly.ai4j.mcp;

import io.github.lnyocly.ai4j.mcp.config.McpServerConfig;
import io.github.lnyocly.ai4j.mcp.entity.McpServerReference;
import io.github.lnyocly.ai4j.mcp.transport.TransportConfig;
import io.github.lnyocly.ai4j.mcp.util.McpTypeSupport;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

public class McpTypeSupportTest {

    @Test
    public void shouldNormalizeKnownAliases() {
        Assert.assertEquals(McpTypeSupport.TYPE_STDIO, McpTypeSupport.normalizeType("process"));
        Assert.assertEquals(McpTypeSupport.TYPE_SSE, McpTypeSupport.normalizeType("event-stream"));
        Assert.assertEquals(McpTypeSupport.TYPE_STREAMABLE_HTTP, McpTypeSupport.normalizeType("http"));
        Assert.assertEquals(McpTypeSupport.TYPE_STREAMABLE_HTTP, McpTypeSupport.normalizeType("streamable-http"));
    }

    @Test
    public void shouldCreateTransportConfigFromServerInfo() {
        McpServerConfig.McpServerInfo serverInfo = new McpServerConfig.McpServerInfo();
        serverInfo.setTransport("server-sent-events");
        serverInfo.setUrl("http://localhost:8080/sse");
        serverInfo.setHeaders(new HashMap<String, String>(Collections.singletonMap("Authorization", "Bearer test")));

        TransportConfig config = TransportConfig.fromServerInfo(serverInfo);

        Assert.assertEquals(McpTypeSupport.TYPE_SSE, config.getType());
        Assert.assertEquals("http://localhost:8080/sse", config.getUrl());
        Assert.assertEquals("Bearer test", config.getHeaders().get("Authorization"));
    }

    @Test
    public void shouldCreateStdioTransportConfigFromServerInfo() {
        McpServerConfig.McpServerInfo serverInfo = new McpServerConfig.McpServerInfo();
        serverInfo.setType("local");
        serverInfo.setCommand("npx");
        serverInfo.setArgs(Arrays.asList("-y", "@modelcontextprotocol/server-filesystem"));

        TransportConfig config = TransportConfig.fromServerInfo(serverInfo);

        Assert.assertEquals(McpTypeSupport.TYPE_STDIO, config.getType());
        Assert.assertEquals("npx", config.getCommand());
        Assert.assertEquals(2, config.getArgs().size());
    }

    @Test
    public void shouldResolveTypeFromServerReference() {
        McpServerReference serverReference = McpServerReference.http("demo", "http://localhost:8080/mcp");

        Assert.assertEquals(McpTypeSupport.TYPE_STREAMABLE_HTTP, McpTypeSupport.resolveType(serverReference));
        Assert.assertEquals(McpTypeSupport.TYPE_STREAMABLE_HTTP, serverReference.getResolvedType());
    }
}
