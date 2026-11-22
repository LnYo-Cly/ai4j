package io.github.lnyocly.ai4j.coding.tool;

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

public class EditToolExecutorTest {

    private Path tempDir;
    private EditToolExecutor executor;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("edit-test");
        Files.write(tempDir.resolve("App.java"),
                ("public class App {\n"
                        + "    public void run() {\n"
                        + "        System.out.println(\"hello\");\n"
                        + "    }\n"
                        + "}\n").getBytes());

        WorkspaceContext ctx = WorkspaceContext.builder()
                .rootPath(tempDir.toString())
                .build();
        executor = new EditToolExecutor(ctx);
    }

    @After
    public void tearDown() throws IOException {
        deleteRecursively(tempDir);
    }

    @Test
    public void shouldReplaceUniqueMatch() throws Exception {
        ObjectNode a = CodingJson.newObject();
        a.put("path", "App.java");
        a.put("old_string", "hello");
        a.put("new_string", "world");
        ObjectNode result = CodingJson.parseObject(execute(CodingJson.toJson(a)));
        assertEquals(1, CodingJson.getIntValue(result, "replacements"));
        assertTrue(CodingJson.getBooleanValue(result, "success"));
        String content = new String(Files.readAllBytes(tempDir.resolve("App.java")));
        assertTrue(content.contains("world"));
        assertFalse(content.contains("hello"));
    }

    @Test
    public void shouldReplaceMultipleLines() throws Exception {
        ObjectNode a = CodingJson.newObject();
        a.put("path", "App.java");
        a.put("old_string", "        System.out.println(\"hello\");");
        a.put("new_string", "        // removed");
        ObjectNode result = CodingJson.parseObject(execute(CodingJson.toJson(a)));
        assertEquals(1, CodingJson.getIntValue(result, "replacements"));
        String content = new String(Files.readAllBytes(tempDir.resolve("App.java")));
        assertTrue(content.contains("// removed"));
    }

    @Test
    public void shouldErrorWhenOldStringNotFound() {
        ObjectNode a = CodingJson.newObject();
        a.put("path", "App.java");
        a.put("old_string", "nonexistent");
        a.put("new_string", "x");
        try {
            execute(CodingJson.toJson(a));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("not found"));
        } catch (Exception e) {
            fail("Expected IllegalArgumentException but got: " + e.getClass());
        }
    }

    @Test
    public void shouldErrorWhenNotUniqueAndReplaceAllFalse() throws IOException {
        Files.write(tempDir.resolve("Dup.java"),
                "int x = 1;\nint x = 1;\n".getBytes());
        ObjectNode a = CodingJson.newObject();
        a.put("path", "Dup.java");
        a.put("old_string", "int x = 1;");
        a.put("new_string", "int y = 2;");
        try {
            execute(CodingJson.toJson(a));
            fail("Expected IllegalArgumentException for non-unique match");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("not unique"));
        } catch (Exception e) {
            fail("Expected IllegalArgumentException but got: " + e.getClass());
        }
    }

    @Test
    public void shouldReplaceAllWhenFlagSet() throws Exception {
        Files.write(tempDir.resolve("Dup.java"),
                "int x = 1;\nint x = 1;\n".getBytes());
        ObjectNode a = CodingJson.newObject();
        a.put("path", "Dup.java");
        a.put("old_string", "int x = 1;");
        a.put("new_string", "int y = 2;");
        a.put("replaceAll", true);
        ObjectNode result = CodingJson.parseObject(execute(CodingJson.toJson(a)));
        assertEquals(2, CodingJson.getIntValue(result, "replacements"));
        String content = new String(Files.readAllBytes(tempDir.resolve("Dup.java")));
        assertEquals("int y = 2;\nint y = 2;\n", content);
    }

    @Test
    public void shouldErrorWhenOldAndNewAreIdentical() {
        ObjectNode a = CodingJson.newObject();
        a.put("path", "App.java");
        a.put("old_string", "hello");
        a.put("new_string", "hello");
        try {
            execute(CodingJson.toJson(a));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("identical"));
        } catch (Exception e) {
            fail("Expected IllegalArgumentException but got: " + e.getClass());
        }
    }

    @Test
    public void shouldErrorWhenFileDoesNotExist() {
        ObjectNode a = CodingJson.newObject();
        a.put("path", "nope.java");
        a.put("old_string", "a");
        a.put("new_string", "b");
        try {
            execute(CodingJson.toJson(a));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("does not exist"));
        } catch (Exception e) {
            fail("Expected IllegalArgumentException but got: " + e.getClass());
        }
    }

    @Test
    public void shouldRequirePath() {
        ObjectNode a = CodingJson.newObject();
        a.put("old_string", "a");
        a.put("new_string", "b");
        try {
            execute(CodingJson.toJson(a));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("path"));
        } catch (Exception e) {
            fail("Expected IllegalArgumentException but got: " + e.getClass());
        }
    }

    private String execute(String args) throws Exception {
        AgentToolCall call = AgentToolCall.builder()
                .name("edit")
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
