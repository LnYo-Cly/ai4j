package io.github.lnyocly.ai4j.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.cli.acp.AcpJsonRpcServer;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptionsParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CliJacksonMigrationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void compiledClassesHaveZeroFastjson2References() throws Exception {
        Path classesDir = Paths.get(CliJson.class.getProtectionDomain().getCodeSource().getLocation().toURI());
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

        assertTrue("Expected compiled class files in ai4j-cli", scanned > 0);
        assertTrue("Unexpected fastjson2 constant pool references in: " + offendingClasses, offendingClasses.isEmpty());
    }

    @Test
    public void cliJsonPreservesOrderOmitsNullsAndCoercesTypes() {
        Map<String, Object> ordered = new LinkedHashMap<String, Object>();
        ordered.put("alpha", 1);
        ordered.put("omitted", null);
        ordered.put("beta", "two");
        assertEquals("{\"alpha\":1,\"beta\":\"two\"}", CliJson.toJson(ordered));

        ObjectNode parsed = CliJson.parseObject("{\"num\":\"7\",\"flag\":\"true\",\"blank\":\"\",\"nil\":null}"
                .getBytes(StandardCharsets.UTF_8));
        assertNotNull(parsed);
        assertEquals(Integer.valueOf(7), CliJson.getInteger(parsed, "num"));
        assertEquals(7, CliJson.getIntValue(parsed, "num"));
        assertTrue(CliJson.getBooleanValue(parsed, "flag"));
        assertNull(CliJson.getInteger(parsed, "blank"));
        assertNull(CliJson.getString(parsed, "nil"));
        assertNull(CliJson.getString(parsed, "missing"));

        assertNull(CliJson.parseObject((String) null));
        assertNull(CliJson.parseObject("   "));
        assertNull(CliJson.parseObject("null"));
        assertNull(CliJson.parseObjectOrNull("not-json"));
        assertNull(CliJson.parseArrayOrNull("{\"not\":\"array\"}"));

        ArrayNode array = CliJson.parseArray("[\"first\",\"second\"]");
        assertNotNull(array);
        assertEquals("first", CliJson.getString(array, 0));

        try {
            CliJson.parseObject("[\"not-an-object\"]");
            fail("Expected IllegalArgumentException for array input");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            CliJson.parseObject("{\"a\":1}{\"b\":2}");
            fail("Expected IllegalArgumentException for trailing tokens");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void acpServerPreservesStringIntegerAndNullRequestIdsAndRecoversFromInvalidJson() throws Exception {
        Path workspace = temporaryFolder.newFolder("acp-framing").toPath();
        CodeCommandOptions options = new CodeCommandOptionsParser().parse(
                Arrays.asList("--workspace", workspace.toString(), "--model", "fake-model"),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        String input = "{invalid-json-line\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"session/cancel\",\"params\":{}}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":1}}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":\"req-str-2\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":1}}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":\"unknown-3\",\"method\":\"custom/unknown\",\"params\":{}}\n";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new AcpJsonRpcServer(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                out,
                err,
                options
        ).run();

        assertEquals(0, exitCode);
        String stderrText = new String(err.toByteArray(), StandardCharsets.UTF_8);
        assertTrue("Expected invalid JSON diagnostic on stderr", stderrText.contains("Invalid ACP JSON message"));

        List<ObjectNode> messages = new ArrayList<ObjectNode>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(out.toByteArray()), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    messages.add(CliJson.parseObject(line));
                }
            }
        }

        assertEquals(3, messages.size());

        ObjectNode intResp = findByPlainId(messages, Integer.valueOf(1));
        assertNotNull(intResp);
        JsonNode intIdNode = intResp.get("id");
        assertTrue(intIdNode.isIntegralNumber());
        assertEquals(1, intIdNode.intValue());
        assertEquals(1, CliJson.getIntValue(CliJson.getObject(intResp, "result"), "protocolVersion"));

        ObjectNode strResp = findByPlainId(messages, "req-str-2");
        assertNotNull(strResp);
        JsonNode strIdNode = strResp.get("id");
        assertTrue(strIdNode.isTextual());
        assertEquals("req-str-2", strIdNode.asText());

        ObjectNode errResp = findByPlainId(messages, "unknown-3");
        assertNotNull(errResp);
        assertTrue(errResp.get("id").isTextual());
        ObjectNode errorObj = CliJson.getObject(errResp, "error");
        assertNotNull(errorObj);
        assertEquals(-32601, CliJson.getIntValue(errorObj, "code"));
        assertFalse(CliJson.getString(errorObj, "message").isEmpty());
    }

    private static ObjectNode findByPlainId(List<ObjectNode> messages, Object expectedId) {
        for (ObjectNode message : messages) {
            Object actualId = CliJson.toPlainValue(message.get("id"));
            if (expectedId.equals(actualId)) {
                return message;
            }
        }
        return null;
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
