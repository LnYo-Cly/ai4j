package io.github.lnyocly.ai4j.coding;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolInputException;
import io.github.lnyocly.ai4j.coding.tool.BashToolExecutor;
import io.github.lnyocly.ai4j.coding.tool.CodingToolNames;
import io.github.lnyocly.ai4j.coding.tool.ReadFileToolExecutor;
import io.github.lnyocly.ai4j.coding.tool.UpdateAgentsMdToolExecutor;
import io.github.lnyocly.ai4j.coding.workspace.LocalWorkspaceFileService;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CodingJacksonMigrationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void compiledClassesHaveZeroFastjson2References() throws Exception {
        Path classesDir = Paths.get(CodingJson.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue("Expected compiled classes directory at " + classesDir, Files.isDirectory(classesDir));

        byte[] forbiddenPrefix = "com/alibaba/fastjson2/".getBytes(StandardCharsets.US_ASCII);
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
            if (containsBytes(bytes, forbiddenPrefix)) {
                offendingClasses.add(classesDir.relativize(classFile).toString());
            }
        }

        assertTrue("Expected compiled class files in ai4j-coding", scanned > 0);
        assertTrue("Unexpected fastjson2 constant pool references in: " + offendingClasses, offendingClasses.isEmpty());
    }

    @Test
    public void codingJsonHandlesNullsOrderCoercionsAndStrictObjectParsing() {
        Map<String, Object> ordered = new LinkedHashMap<String, Object>();
        ordered.put("first", "alpha");
        ordered.put("omitted", null);
        ordered.put("second", 2);
        String json = CodingJson.toJson(ordered);
        assertEquals("{\"first\":\"alpha\",\"second\":2}", json);

        ObjectNode parsed = CodingJson.parseObject("{\"num\":\"42\",\"flag\":\"true\",\"empty\":null}");
        assertEquals("42", CodingJson.getString(parsed, "num"));
        assertEquals(42, CodingJson.getIntValue(parsed, "num"));
        assertEquals(42L, CodingJson.getLongValue(parsed, "num"));
        assertTrue(CodingJson.getBooleanValue(parsed, "flag"));
        assertNull(CodingJson.getString(parsed, "empty"));
        assertNull(CodingJson.getString(parsed, "missing"));

        assertNull(CodingJson.parseObject(null));
        assertNull(CodingJson.parseObject("   "));
        assertNull(CodingJson.parseObject("null"));
        assertNull(CodingJson.parseObjectOrNull("not-json"));

        try {
            CodingJson.parseObject("[\"not-an-object\"]");
            fail("Expected IllegalArgumentException for array input");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            CodingJson.parseObject("{\"a\":1}{\"b\":2}");
            fail("Expected IllegalArgumentException for trailing tokens");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        CodingSessionCompactResult compact = CodingSessionCompactResult.builder()
                .sessionId("s-1")
                .beforeItemCount(10)
                .afterItemCount(3)
                .summary("compacted")
                .build();
        CodingSessionCompactResult roundTrip = CodingJson.fromJson(CodingJson.toJson(compact), CodingSessionCompactResult.class);
        assertEquals("s-1", roundTrip.getSessionId());
        assertEquals(10, roundTrip.getBeforeItemCount());
        assertEquals("compacted", roundTrip.getSummary());

        Map<String, Object> nested = new LinkedHashMap<String, Object>();
        nested.put("arr", new String[]{"a", "b"});
        Object copied = CodingJson.copyPlainValue(nested);
        assertTrue(copied instanceof Map);
        Object arr = ((Map<?, ?>) copied).get("arr");
        assertTrue(arr instanceof List);
        ArrayNode arrayNode = CodingJson.newArray();
        arrayNode.add("x");
        assertEquals("x", CodingJson.getString(arrayNode, 0));
    }

    @Test
    public void toolExecutorsHandleBlankNullAndMalformedArguments() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("coding-tool-edge").toPath();
        WorkspaceContext context = WorkspaceContext.builder().rootPath(workspaceRoot.toString()).build();

        UpdateAgentsMdToolExecutor agentsMd = new UpdateAgentsMdToolExecutor(context);
        ObjectNode agentsBlank = CodingJson.parseObject(agentsMd.execute(AgentToolCall.builder()
                .name("update_agents_md")
                .arguments("   ")
                .build()));
        assertFalse(CodingJson.getBooleanValue(agentsBlank, "exists"));

        ObjectNode agentsNull = CodingJson.parseObject(agentsMd.execute(AgentToolCall.builder()
                .name("update_agents_md")
                .arguments(null)
                .build()));
        assertFalse(CodingJson.getBooleanValue(agentsNull, "exists"));

        try {
            agentsMd.execute(AgentToolCall.builder()
                    .name("update_agents_md")
                    .arguments("null")
                    .build());
            fail("Expected AgentToolInputException for literal null JSON");
        } catch (AgentToolInputException expected) {
            // expected
        }

        ReadFileToolExecutor readFile = new ReadFileToolExecutor(
                new LocalWorkspaceFileService(context),
                CodingAgentOptions.builder().build()
        );
        try {
            readFile.execute(AgentToolCall.builder()
                    .name(CodingToolNames.READ_FILE)
                    .arguments("{bad json")
                    .build());
            fail("Expected IllegalArgumentException on malformed JSON arguments");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        BashToolExecutor bash = new BashToolExecutor(context, CodingAgentOptions.builder().build(), null);
        try {
            bash.execute(AgentToolCall.builder()
                    .name(CodingToolNames.BASH)
                    .arguments("[]")
                    .build());
            fail("Expected IllegalArgumentException on array JSON arguments");
        } catch (IllegalArgumentException expected) {
            // expected
        }
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
