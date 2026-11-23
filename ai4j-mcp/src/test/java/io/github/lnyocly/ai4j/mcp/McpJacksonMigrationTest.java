package io.github.lnyocly.ai4j.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
import io.github.lnyocly.ai4j.mcp.entity.McpRequest;
import io.github.lnyocly.ai4j.mcp.util.McpMessageCodec;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Verifies {@code ai4j-mcp} compiled bytecode contains zero {@code fastjson2} references
 * and exercises Jackson serialization of MCP frames.
 */
public class McpJacksonMigrationTest {

    @Test
    public void compiledClassesHaveZeroFastjson2References() throws Exception {
        Path classesDir = Paths.get(McpMessageCodec.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Assert.assertTrue("Expected compiled classes directory at " + classesDir, Files.isDirectory(classesDir));

        byte[] forbiddenFastjson2 = "com/alibaba/fastjson2/".getBytes(StandardCharsets.US_ASCII);
        byte[] forbiddenFastjson = "com/alibaba/fastjson/".getBytes(StandardCharsets.US_ASCII);
        List<String> offendingClasses = new ArrayList<String>();
        int scanned = 0;

        List<Path> classFiles = new ArrayList<Path>();
        try (Stream<Path> stream = Files.walk(classesDir)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                    .forEach(classFiles::add);
        }

        for (Path classFile : classFiles) {
            scanned++;
            byte[] bytes = Files.readAllBytes(classFile);
            if (containsBytes(bytes, forbiddenFastjson2) || containsBytes(bytes, forbiddenFastjson)) {
                offendingClasses.add(classesDir.relativize(classFile).toString());
            }
        }

        Assert.assertTrue("Expected compiled class files in ai4j-mcp", scanned > 0);
        Assert.assertTrue("Unexpected fastjson constant-pool references in: " + offendingClasses,
                offendingClasses.isEmpty());
    }

    @Test
    public void mcpMessageCodecRoundTripsRequestsViaJackson() {
        McpRequest request = new McpRequest("tools/call", Long.valueOf(7L),
                Collections.singletonMap("name", "echo"));
        String json = JsonCodec.toJson(request);
        McpMessage parsed = McpMessageCodec.parseMessage(json);
        Assert.assertTrue(parsed.isRequest());
        Assert.assertEquals("tools/call", parsed.getMethod());
        Map<String, Object> params = JsonCodec.fromJson(
                JsonCodec.toJson(parsed.getParams()),
                new TypeReference<Map<String, Object>>() {
                });
        Assert.assertEquals("echo", params.get("name"));
    }

    private static boolean containsBytes(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || haystack.length < needle.length) {
            return false;
        }
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
