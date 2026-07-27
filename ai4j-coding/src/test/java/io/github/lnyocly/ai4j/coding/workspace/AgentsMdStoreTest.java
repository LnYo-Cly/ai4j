package io.github.lnyocly.ai4j.coding.workspace;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class AgentsMdStoreTest {

    private Path tempDir;
    private AgentsMdStore store;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("agentsmd-test");
        WorkspaceContext ctx = WorkspaceContext.builder()
                .rootPath(tempDir.toString())
                .build();
        store = new AgentsMdStore(ctx);
    }

    @After
    public void tearDown() throws IOException {
        deleteRecursively(tempDir);
    }

    @Test
    public void shouldReturnEmptyWhenFileMissing() throws Exception {
        assertFalse(store.exists());
        assertEquals("", store.read());
    }

    @Test
    public void shouldWriteAndReadBack() throws Exception {
        store.write("# Project Notes\nConvention: use tabs.\n");
        assertTrue(store.exists());
        String content = store.read();
        assertTrue(content.contains("# Project Notes"));
        assertTrue(content.contains("use tabs"));
    }

    @Test
    public void shouldAppendToExistingFile() throws Exception {
        store.write("# Header\n");
        store.append("## Decision\nUse Java 8.\n");
        String content = store.read();
        assertTrue(content.contains("# Header"));
        assertTrue(content.contains("## Decision"));
        assertTrue(content.contains("Use Java 8."));
    }

    @Test
    public void shouldAppendCreatingNewFile() throws Exception {
        store.append("# New\n");
        assertTrue(store.exists());
        String content = store.read();
        assertTrue(content.contains("# New"));
    }

    @Test
    public void shouldPreferRootAgentsMdOverFallback() throws Exception {
        store.write("# Root version\n");
        Path fallbackDir = tempDir.resolve(".agents");
        Files.createDirectories(fallbackDir);
        Files.write(fallbackDir.resolve("AGENTS.md"), "# Fallback version\n".getBytes());

        String content = store.read();
        assertTrue("should read root AGENTS.md, not .agents/AGENTS.md", content.contains("Root version"));
        assertFalse(content.contains("Fallback version"));
    }

    @Test
    public void shouldReadFallbackWhenRootMissing() throws Exception {
        Path fallbackDir = tempDir.resolve(".agents");
        Files.createDirectories(fallbackDir);
        Files.write(fallbackDir.resolve("AGENTS.md"), "# Fallback version\n".getBytes());

        assertTrue(store.exists());
        String content = store.read();
        assertTrue(content.contains("Fallback version"));
    }

    @Test
    public void shouldOverwriteExistingContent() throws Exception {
        store.write("# Original\n");
        store.write("# Replaced\n");
        String content = store.read();
        assertFalse(content.contains("Original"));
        assertTrue(content.contains("Replaced"));
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
