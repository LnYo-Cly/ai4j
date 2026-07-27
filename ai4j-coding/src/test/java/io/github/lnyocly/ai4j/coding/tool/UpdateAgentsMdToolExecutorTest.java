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

public class UpdateAgentsMdToolExecutorTest {

    private Path tempDir;
    private UpdateAgentsMdToolExecutor executor;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("agentsmd-exec-test");
        WorkspaceContext ctx = WorkspaceContext.builder()
                .rootPath(tempDir.toString())
                .build();
        executor = new UpdateAgentsMdToolExecutor(ctx);
    }

    @After
    public void tearDown() throws IOException {
        deleteRecursively(tempDir);
    }

    @Test
    public void shouldReadEmptyWhenMissing() throws Exception {
        JSONObject result = JSON.parseObject(execute(action("read")));
        assertFalse(result.getBoolean("exists"));
        assertEquals("", result.getString("content"));
    }

    @Test
    public void shouldWriteContent() throws Exception {
        JSONObject a = new JSONObject();
        a.put("action", "write");
        a.put("content", "# My Project\nAlways test before commit.\n");
        JSONObject result = JSON.parseObject(execute(a.toJSONString()));
        assertTrue(result.getBoolean("success"));

        String file = new String(Files.readAllBytes(tempDir.resolve("AGENTS.md")));
        assertTrue(file.contains("# My Project"));
        assertTrue(file.contains("Always test before commit"));
    }

    @Test
    public void shouldReadBackWrittenContent() throws Exception {
        JSONObject write = new JSONObject();
        write.put("action", "write");
        write.put("content", "# Notes\nUse spaces.\n");
        execute(write.toJSONString());

        JSONObject result = JSON.parseObject(execute(action("read")));
        assertTrue(result.getBoolean("exists"));
        assertTrue(result.getString("content").contains("Use spaces"));
    }

    @Test
    public void shouldAppendText() throws Exception {
        JSONObject write = new JSONObject();
        write.put("action", "write");
        write.put("content", "# Header\n");
        execute(write.toJSONString());

        JSONObject append = new JSONObject();
        append.put("action", "append");
        append.put("text", "## Decision\nUse Java 8.\n");
        JSONObject result = JSON.parseObject(execute(append.toJSONString()));
        assertTrue(result.getBoolean("success"));

        String file = new String(Files.readAllBytes(tempDir.resolve("AGENTS.md")));
        assertTrue(file.contains("# Header"));
        assertTrue(file.contains("## Decision"));
    }

    @Test
    public void shouldDefaultToReadAction() throws Exception {
        JSONObject result = JSON.parseObject(execute("{}"));
        assertFalse(result.getBoolean("exists"));
    }

    @Test
    public void shouldErrorOnUnsupportedAction() {
        try {
            execute(action("delete"));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unsupported action"));
        } catch (Exception e) {
            fail("Expected IllegalArgumentException but got: " + e.getClass());
        }
    }

    private String action(String action) {
        JSONObject obj = new JSONObject();
        obj.put("action", action);
        return obj.toJSONString();
    }

    private String execute(String args) throws Exception {
        AgentToolCall call = AgentToolCall.builder()
                .name("update_agents_md")
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
