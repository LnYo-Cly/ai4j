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
        JSONObject result = JSON.parseObject(execute(args("pattern", "**/*.java")));
        assertTrue(result.getJSONArray("matches").contains("src/main/java/com/example/Foo.java"));
        assertTrue(result.getJSONArray("matches").contains("src/main/java/com/example/Bar.java"));
        assertTrue(result.getJSONArray("matches").contains("src/test/java/com/example/FooTest.java"));
        assertFalse(result.getJSONArray("matches").contains("README.md"));
    }

    @Test
    public void shouldExcludeTargetDirectory() throws Exception {
        JSONObject result = JSON.parseObject(execute(args("pattern", "**/*.java")));
        assertFalse("target/ should be excluded",
                result.getJSONArray("matches").contains("target/ShouldNotMatch.java"));
    }

    @Test
    public void shouldMatchBySpecificSubPath() throws Exception {
        JSONObject a = new JSONObject();
        a.put("pattern", "**/*.java");
        a.put("path", "src/test");
        JSONObject result = JSON.parseObject(execute(a.toJSONString()));
        assertTrue(result.getJSONArray("matches").contains("src/test/java/com/example/FooTest.java"));
        assertEquals(1, result.getJSONArray("matches").size());
    }

    @Test
    public void shouldMatchMarkdownFiles() throws Exception {
        JSONObject result = JSON.parseObject(execute(args("pattern", "*.md")));
        assertTrue(result.getJSONArray("matches").contains("README.md"));
        assertEquals(1, result.getJSONArray("matches").size());
    }

    @Test
    public void shouldRespectMaxResults() throws Exception {
        JSONObject a = new JSONObject();
        a.put("pattern", "**/*.java");
        a.put("maxResults", 1);
        JSONObject result = JSON.parseObject(execute(a.toJSONString()));
        assertEquals(1, result.getJSONArray("matches").size());
        assertTrue(result.getBoolean("truncated"));
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
        JSONObject obj = new JSONObject();
        obj.put(key, value);
        return obj.toJSONString();
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
