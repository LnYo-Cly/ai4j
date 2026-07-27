package io.github.lnyocly.ai4j.coding.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Reads and writes the project-level AGENTS.md memory file.
 *
 * <p>Lookup order:
 * <ol>
 *   <li>{@code AGENTS.md} at the workspace root</li>
 *   <li>{@code .agents/AGENTS.md} under the workspace root</li>
 * </ol>
 * When neither exists, reads return an empty string and writes create
 * {@code AGENTS.md} at the workspace root.
 */
public class AgentsMdStore {

    private static final String FILE_NAME = "AGENTS.md";
    private static final String FALLBACK_DIR = ".agents";

    private final WorkspaceContext workspaceContext;

    public AgentsMdStore(WorkspaceContext workspaceContext) {
        this.workspaceContext = workspaceContext;
    }

    /**
     * Resolve the path where AGENTS.md lives (or will be created).
     *
     * @return the resolved path, preferring an existing file
     */
    public Path resolveAgentsMdPath() {
        Path root = workspaceContext.getRoot();
        Path primary = root.resolve(FILE_NAME);
        if (Files.exists(primary)) {
            return primary;
        }
        Path fallback = root.resolve(FALLBACK_DIR).resolve(FILE_NAME);
        if (Files.exists(fallback)) {
            return fallback;
        }
        return primary;
    }

    /**
     * Read the current AGENTS.md content.
     *
     * @return the file content, or empty string if the file does not exist
     */
    public String read() throws IOException {
        Path file = resolveAgentsMdPath();
        if (!Files.exists(file)) {
            return "";
        }
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /**
     * Check whether an AGENTS.md file currently exists.
     */
    public boolean exists() {
        return Files.exists(resolveAgentsMdPath());
    }

    /**
     * Overwrite AGENTS.md with the given content.
     *
     * @param content the full content to write
     * @return the path that was written
     */
    public Path write(String content) throws IOException {
        Path file = resolveAgentsMdPath();
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        Files.write(file, bytes,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return file;
    }

    /**
     * Append text to the end of AGENTS.md, creating the file if needed.
     *
     * @param text the text to append
     * @return the path that was written
     */
    public Path append(String text) throws IOException {
        Path file = resolveAgentsMdPath();
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String existing = "";
        if (Files.exists(file)) {
            existing = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        }
        String separator = existing.isEmpty() || existing.endsWith("\n") ? "" : "\n";
        String toWrite = separator + (text == null ? "" : text);
        Files.write(file, toWrite.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        return file;
    }
}
