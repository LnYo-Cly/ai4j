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
        JSONObject result = JSON.parseObject(execute(args("pattern", "TODO")));
        assertEquals(2, result.getIntValue("totalMatches"));
        assertEquals(2, result.getIntValue("filesMatched"));
        assertFalse(result.getJSONArray("matches").isEmpty());
    }

    @Test
    public void shouldExcludeTargetDirectory() throws Exception {
        JSONObject result = JSON.parseObject(execute(args("pattern", "TODO")));
        for (Object match : result.getJSONArray("matches")) {
            JSONObject m = (JSONObject) match;
            assertFalse("target/ should be excluded", m.getString("file").startsWith("target/"));
        }
    }

    @Test
    public void shouldReturnLineNumbersAndContent() throws Exception {
        JSONObject a = new JSONObject();
        a.put("pattern", "TODO");
        a.put("path", "src/Foo.java");
        JSONObject result = JSON.parseObject(execute(a.toJSONString()));
        assertEquals(1, result.getIntValue("totalMatches"));
        JSONObject match = result.getJSONArray("matches").getJSONObject(0);
        assertEquals("src/Foo.java", match.getString("file"));
        assertEquals(2, match.getIntValue("line"));
        assertTrue(match.getString("content").contains("TODO"));
    }

    @Test
    public void shouldSupportCaseInsensitive() throws Exception {
        JSONObject a = new JSONObject();
        a.put("pattern", "todo");
        a.put("caseInsensitive", true);
        JSONObject result = JSON.parseObject(execute(a.toJSONString()));
        assertEquals(3, result.getIntValue("totalMatches"));
    }

    @Test
    public void shouldBeCaseSensitiveByDefault() throws Exception {
        JSONObject result = JSON.parseObject(execute(args("pattern", "todo")));
        assertEquals(1, result.getIntValue("totalMatches"));
    }

    @Test
    public void shouldSupportIncludeGlobFilter() throws Exception {
        JSONObject a = new JSONObject();
        a.put("pattern", "TODO");
        a.put("include", "*.java");
        JSONObject result = JSON.parseObject(execute(a.toJSONString()));
        assertTrue(result.getIntValue("totalMatches") >= 2);
    }

    @Test
    public void shouldSupportRegexPattern() throws Exception {
        JSONObject result = JSON.parseObject(execute(args("pattern", "TODO.*implement")));
        assertEquals(1, result.getIntValue("totalMatches"));
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
