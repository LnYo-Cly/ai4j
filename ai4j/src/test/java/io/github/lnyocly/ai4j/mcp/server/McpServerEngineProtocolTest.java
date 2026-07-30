package io.github.lnyocly.ai4j.mcp.server;

import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
import io.github.lnyocly.ai4j.mcp.entity.McpRequest;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class McpServerEngineProtocolTest {

    @Test
    public void onlyModernProcessingBypassesLegacyInitializationGate() {
        McpServerEngine engine = new McpServerEngine(
                "test", "1.0", Arrays.asList("2025-03-26", "2026-07-28"),
                "2025-03-26", true, false, false);
        McpRequest request = new McpRequest("tools/list", Long.valueOf(1L),
                Collections.<String, Object>emptyMap());

        McpMessage legacyResponse = engine.processMessage(request, null);
        McpMessage modernResponse = engine.processModernMessage(request);

        Assert.assertTrue(legacyResponse.isErrorResponse());
        Assert.assertEquals(Integer.valueOf(-32002), legacyResponse.getError().getCode());
        Assert.assertTrue(modernResponse.isSuccessResponse());
    }
}
