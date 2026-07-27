package io.github.lnyocly.ai4j.coding.tool;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
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
        JSONObject a = new JSONObject();
        a.put("path", "App.java");
        a.put("old_string", "hello");
        a.put("new_string", "world");
        JSONObject result = JSON.parseObject(execute(a.toJSONString()));
        assertEquals(1, result.getIntValue("replacements"));
        assertTrue(result.getBoolean("success"));
        String content = new String(Files.readAllBytes(tempDir.resolve("App.java")));
        assertTrue(content.contains("world"));
        assertFalse(content.contains("hello"));
    }

    @Test
    public void shouldReplaceMultipleLines() throws Exception {
        JSONObject a = new JSONObject();
        a.put("path", "App.java");
        a.put("old_string", "        System.out.println(\"hello\");");
        a.put("new_string", "        // removed");
        JSONObject result = JSON.parseObject(execute(a.toJSONString()));
        assertEquals(1, result.getIntValue("replacements"));
        String content = new String(Files.readAllBytes(tempDir.resolve("App.java")));
        assertTrue(content.contains("// removed"));
    }

    @Test
    public void shouldErrorWhenOldStringNotFound() {
        JSONObject a = new JSONObject();
        a.put("path", "App.java");
        a.put("old_string", "nonexistent");
        a.put("new_string", "x");
        try {
            execute(a.toJSONString());
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
        JSONObject a = new JSONObject();
        a.put("path", "Dup.java");
        a.put("old_string", "int x = 1;");
        a.put("new_string", "int y = 2;");
        try {
            execute(a.toJSONString());
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
        JSONObject a = new JSONObject();
        a.put("path", "Dup.java");
        a.put("old_string", "int x = 1;");
        a.put("new_string", "int y = 2;");
        a.put("replaceAll", true);
        JSONObject result = JSON.parseObject(execute(a.toJSONString()));
        assertEquals(2, result.getIntValue("replacements"));
        String content = new String(Files.readAllBytes(tempDir.resolve("Dup.java")));
        assertEquals("int y = 2;\nint y = 2;\n", content);
    }

    @Test
    public void shouldErrorWhenOldAndNewAreIdentical() {
        JSONObject a = new JSONObject();
        a.put("path", "App.java");
        a.put("old_string", "hello");
        a.put("new_string", "hello");
        try {
            execute(a.toJSONString());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("identical"));
        } catch (Exception e) {
            fail("Expected IllegalArgumentException but got: " + e.getClass());
        }
    }

    @Test
    public void shouldErrorWhenFileDoesNotExist() {
        JSONObject a = new JSONObject();
        a.put("path", "nope.java");
        a.put("old_string", "a");
        a.put("new_string", "b");
        try {
            execute(a.toJSONString());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("does not exist"));
        } catch (Exception e) {
            fail("Expected IllegalArgumentException but got: " + e.getClass());
        }
    }

    @Test
    public void shouldRequirePath() {
        JSONObject a = new JSONObject();
        a.put("old_string", "a");
        a.put("new_string", "b");
        try {
            execute(a.toJSONString());
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
