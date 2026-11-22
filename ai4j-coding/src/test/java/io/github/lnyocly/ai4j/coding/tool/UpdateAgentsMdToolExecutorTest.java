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
        ObjectNode result = CodingJson.parseObject(execute(action("read")));
        assertFalse(CodingJson.getBooleanValue(result, "exists"));
        assertEquals("", CodingJson.getString(result, "content"));
    }

    @Test
    public void shouldWriteContent() throws Exception {
        ObjectNode a = CodingJson.newObject();
        a.put("action", "write");
        a.put("content", "# My Project\nAlways test before commit.\n");
        ObjectNode result = CodingJson.parseObject(execute(CodingJson.toJson(a)));
        assertTrue(CodingJson.getBooleanValue(result, "success"));

        String file = new String(Files.readAllBytes(tempDir.resolve("AGENTS.md")));
        assertTrue(file.contains("# My Project"));
        assertTrue(file.contains("Always test before commit"));
    }

    @Test
    public void shouldReadBackWrittenContent() throws Exception {
        ObjectNode write = CodingJson.newObject();
        write.put("action", "write");
        write.put("content", "# Notes\nUse spaces.\n");
        execute(CodingJson.toJson(write));

        ObjectNode result = CodingJson.parseObject(execute(action("read")));
        assertTrue(CodingJson.getBooleanValue(result, "exists"));
        assertTrue(CodingJson.getString(result, "content").contains("Use spaces"));
    }

    @Test
    public void shouldAppendText() throws Exception {
        ObjectNode write = CodingJson.newObject();
        write.put("action", "write");
        write.put("content", "# Header\n");
        execute(CodingJson.toJson(write));

        ObjectNode append = CodingJson.newObject();
        append.put("action", "append");
        append.put("text", "## Decision\nUse Java 8.\n");
        ObjectNode result = CodingJson.parseObject(execute(CodingJson.toJson(append)));
        assertTrue(CodingJson.getBooleanValue(result, "success"));

        String file = new String(Files.readAllBytes(tempDir.resolve("AGENTS.md")));
        assertTrue(file.contains("# Header"));
        assertTrue(file.contains("## Decision"));
    }

    @Test
    public void shouldDefaultToReadAction() throws Exception {
        ObjectNode result = CodingJson.parseObject(execute("{}"));
        assertFalse(CodingJson.getBooleanValue(result, "exists"));
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
        ObjectNode obj = CodingJson.newObject();
        obj.put("action", action);
        return CodingJson.toJson(obj);
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
