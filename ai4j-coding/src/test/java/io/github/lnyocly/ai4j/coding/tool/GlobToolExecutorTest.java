package io.github.lnyocly.ai4j.coding.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.coding.CodingJson;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class GlobToolExecutorTest {

    private Path tempDir;
    private GlobToolExecutor executor;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("glob-test");
        Files.createDirectories(tempDir.resolve("src/main/java/com/example"));
        Files.createDirectories(tempDir.resolve("src/test/java/com/example"));
        Files.createDirectories(tempDir.resolve("target"));
        Files.createFile(tempDir.resolve("src/main/java/com/example/Foo.java"));
        Files.createFile(tempDir.resolve("src/main/java/com/example/Bar.java"));
        Files.createFile(tempDir.resolve("src/test/java/com/example/FooTest.java"));
        Files.createFile(tempDir.resolve("target/ShouldNotMatch.java"));
        Files.createFile(tempDir.resolve("README.md"));

        WorkspaceContext ctx = WorkspaceContext.builder()
                .rootPath(tempDir.toString())
                .build();
        executor = new GlobToolExecutor(ctx);
    }

    @After
    public void tearDown() throws IOException {
        deleteRecursively(tempDir);
    }

    @Test
    public void shouldMatchJavaFilesWithDoubleStar() throws Exception {
        ObjectNode result = CodingJson.parseObject(execute(args("pattern", "**/*.java")));
        ArrayNode matches = CodingJson.getArray(result, "matches");
        assertTrue(containsText(matches, "src/main/java/com/example/Foo.java"));
        assertTrue(containsText(matches, "src/main/java/com/example/Bar.java"));
        assertTrue(containsText(matches, "src/test/java/com/example/FooTest.java"));
        assertFalse(containsText(matches, "README.md"));
    }

    @Test
    public void shouldExcludeTargetDirectory() throws Exception {
        ObjectNode result = CodingJson.parseObject(execute(args("pattern", "**/*.java")));
        assertFalse("target/ should be excluded",
                containsText(CodingJson.getArray(result, "matches"), "target/ShouldNotMatch.java"));
    }

    @Test
    public void shouldMatchBySpecificSubPath() throws Exception {
        ObjectNode a = CodingJson.newObject();
        a.put("pattern", "**/*.java");
        a.put("path", "src/test");
        ObjectNode result = CodingJson.parseObject(execute(CodingJson.toJson(a)));
        ArrayNode matches = CodingJson.getArray(result, "matches");
        assertTrue(containsText(matches, "src/test/java/com/example/FooTest.java"));
        assertEquals(1, matches.size());
    }

    @Test
    public void shouldMatchMarkdownFiles() throws Exception {
        ObjectNode result = CodingJson.parseObject(execute(args("pattern", "*.md")));
        ArrayNode matches = CodingJson.getArray(result, "matches");
        assertTrue(containsText(matches, "README.md"));
        assertEquals(1, matches.size());
    }

    @Test
    public void shouldRespectMaxResults() throws Exception {
        ObjectNode a = CodingJson.newObject();
        a.put("pattern", "**/*.java");
        a.put("maxResults", 1);
        ObjectNode result = CodingJson.parseObject(execute(CodingJson.toJson(a)));
        assertEquals(1, CodingJson.getArray(result, "matches").size());
        assertTrue(CodingJson.getBooleanValue(result, "truncated"));
    }

    @Test
    public void shouldRequirePattern() {
        try {
            execute("{}");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("pattern"));
        } catch (Exception e) {
            fail("Expected IllegalArgumentException but got: " + e.getClass());
        }
    }

    private String args(String key, String value) {
        ObjectNode obj = CodingJson.newObject();
        obj.put(key, value);
        return CodingJson.toJson(obj);
    }

    private boolean containsText(ArrayNode array, String expected) {
        if (array == null) {
            return false;
        }
        for (JsonNode item : array) {
            if (item != null && !item.isNull() && expected.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    private String execute(String args) throws Exception {
        AgentToolCall call = AgentToolCall.builder()
                .name("glob")
                .arguments(args)
                .build();
        return executor.execute(call);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walk(path)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
    }
}
