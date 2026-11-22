package io.github.lnyocly.ai4j.coding.tool;

import com.fasterxml.jackson.databind.JsonNode;
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

public class GrepToolExecutorTest {

    private Path tempDir;
    private GrepToolExecutor executor;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("grep-test");
        Files.createDirectories(tempDir.resolve("src"));
        Files.write(tempDir.resolve("src/Foo.java"),
                ("public class Foo {\n"
                        + "    // TODO implement this\n"
                        + "    public void bar() {\n"
                        + "        System.out.println(\"todo list\");\n"
                        + "    }\n"
                        + "}\n").getBytes());
        Files.write(tempDir.resolve("src/Bar.java"),
                ("// TODO fix later\n"
                        + "public class Bar {}\n").getBytes());
        Files.createDirectories(tempDir.resolve("target"));
        Files.write(tempDir.resolve("target/Generated.java"),
                ("// TODO generated\n").getBytes());

        WorkspaceContext ctx = WorkspaceContext.builder()
                .rootPath(tempDir.toString())
                .build();
        executor = new GrepToolExecutor(ctx);
    }

    @After
    public void tearDown() throws IOException {
        deleteRecursively(tempDir);
    }

    @Test
    public void shouldFindAllMatchesAcrossFiles() throws Exception {
        ObjectNode result = CodingJson.parseObject(execute(args("pattern", "TODO")));
        assertEquals(2, CodingJson.getIntValue(result, "totalMatches"));
        assertEquals(2, CodingJson.getIntValue(result, "filesMatched"));
        assertFalse(CodingJson.getArray(result, "matches").isEmpty());
    }

    @Test
    public void shouldExcludeTargetDirectory() throws Exception {
        ObjectNode result = CodingJson.parseObject(execute(args("pattern", "TODO")));
        for (JsonNode match : CodingJson.getArray(result, "matches")) {
            assertFalse("target/ should be excluded", CodingJson.getString(match, "file").startsWith("target/"));
        }
    }

    @Test
    public void shouldReturnLineNumbersAndContent() throws Exception {
        ObjectNode a = CodingJson.newObject();
        a.put("pattern", "TODO");
        a.put("path", "src/Foo.java");
        ObjectNode result = CodingJson.parseObject(execute(CodingJson.toJson(a)));
        assertEquals(1, CodingJson.getIntValue(result, "totalMatches"));
        ObjectNode match = CodingJson.getObject(CodingJson.getArray(result, "matches"), 0);
        assertEquals("src/Foo.java", CodingJson.getString(match, "file"));
        assertEquals(2, CodingJson.getIntValue(match, "line"));
        assertTrue(CodingJson.getString(match, "content").contains("TODO"));
    }

    @Test
    public void shouldSupportCaseInsensitive() throws Exception {
        ObjectNode a = CodingJson.newObject();
        a.put("pattern", "todo");
        a.put("caseInsensitive", true);
        ObjectNode result = CodingJson.parseObject(execute(CodingJson.toJson(a)));
        assertEquals(3, CodingJson.getIntValue(result, "totalMatches"));
    }

    @Test
    public void shouldBeCaseSensitiveByDefault() throws Exception {
        ObjectNode result = CodingJson.parseObject(execute(args("pattern", "todo")));
        assertEquals(1, CodingJson.getIntValue(result, "totalMatches"));
    }

    @Test
    public void shouldSupportIncludeGlobFilter() throws Exception {
        ObjectNode a = CodingJson.newObject();
        a.put("pattern", "TODO");
        a.put("include", "*.java");
        ObjectNode result = CodingJson.parseObject(execute(CodingJson.toJson(a)));
        assertTrue(CodingJson.getIntValue(result, "totalMatches") >= 2);
    }

    @Test
    public void shouldSupportRegexPattern() throws Exception {
        ObjectNode result = CodingJson.parseObject(execute(args("pattern", "TODO.*implement")));
        assertEquals(1, CodingJson.getIntValue(result, "totalMatches"));
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

    private String execute(String args) throws Exception {
        AgentToolCall call = AgentToolCall.builder()
                .name("grep")
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
