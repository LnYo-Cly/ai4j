package io.github.lnyocly.ai4j.coding.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

public class LocalWorkspaceFileService implements WorkspaceFileService {

    private final WorkspaceContext workspaceContext;

    public LocalWorkspaceFileService(WorkspaceContext workspaceContext) {
        this.workspaceContext = workspaceContext;
    }

    @Override
    public List<WorkspaceEntry> listFiles(String path, int maxDepth, int maxEntries) throws IOException {
        Path start = workspaceContext.resolveReadablePath(path);
        if (!Files.exists(start)) {
            return Collections.emptyList();
        }
        if (!Files.isDirectory(start)) {
            throw new IllegalArgumentException("Path is not a directory: " + path);
        }

        int effectiveMaxDepth = maxDepth <= 0 ? 4 : maxDepth;
        int effectiveMaxEntries = maxEntries <= 0 ? 200 : maxEntries;
        List<WorkspaceEntry> entries = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(start, effectiveMaxDepth)) {
            Iterator<Path> iterator = stream
                    .filter(candidate -> !candidate.equals(start))
                    .filter(candidate -> !workspaceContext.isExcluded(candidate))
                    .iterator();
            while (iterator.hasNext() && entries.size() < effectiveMaxEntries) {
                Path candidate = iterator.next();
                entries.add(WorkspaceEntry.builder()
                        .path(toRelativePath(candidate))
                        .directory(Files.isDirectory(candidate))
                        .size(safeSize(candidate))
                        .build());
            }
        }

        return entries;
    }

    @Override
    public WorkspaceFileReadResult readFile(String path, Integer startLine, Integer endLine, Integer maxChars) throws IOException {
        Path file = workspaceContext.resolveReadablePath(path);
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("File does not exist: " + path);
        }
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException("Path is a directory: " + path);
        }

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int effectiveStartLine = startLine == null || startLine < 1 ? 1 : startLine;
        int effectiveEndLine = endLine == null || endLine > lines.size() ? lines.size() : endLine;
        if (effectiveEndLine < effectiveStartLine) {
            effectiveEndLine = effectiveStartLine - 1;
        }

        StringBuilder contentBuilder = new StringBuilder();
        for (int i = effectiveStartLine; i <= effectiveEndLine; i++) {
            if (i > lines.size()) {
                break;
            }
            if (contentBuilder.length() > 0) {
                contentBuilder.append('\n');
            }
            contentBuilder.append(lines.get(i - 1));
        }

        int effectiveMaxChars = maxChars == null || maxChars <= 0 ? 12000 : maxChars;
        String content = contentBuilder.toString();
        boolean truncated = false;
        if (content.length() > effectiveMaxChars) {
            content = content.substring(0, effectiveMaxChars);
            truncated = true;
        }

        return WorkspaceFileReadResult.builder()
                .path(toRelativePath(file))
                .content(content)
                .startLine(effectiveStartLine)
                .endLine(effectiveEndLine)
                .truncated(truncated)
                .build();
    }

    @Override
    public WorkspaceWriteResult writeFile(String path, String content, boolean append) throws IOException {
        Path file = workspaceContext.resolveWorkspacePath(path);
        if (Files.exists(file) && Files.isDirectory(file)) {
            throw new IllegalArgumentException("Path is a directory: " + path);
        }
        boolean created = !Files.exists(file);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        byte[] bytes = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
        if (append) {
            Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }

        return WorkspaceWriteResult.builder()
                .path(toRelativePath(file))
                .bytesWritten(bytes.length)
                .created(created)
                .appended(append)
                .build();
    }

    private long safeSize(Path path) {
        if (Files.isDirectory(path)) {
            return 0L;
        }
        try {
            return Files.size(path);
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private String toRelativePath(Path path) {
        Path root = workspaceContext.getRoot();
        if (path == null) {
            return "";
        }
        if (!path.startsWith(root)) {
            return path.toString().replace('\\', '/');
        }
        if (path.equals(root)) {
            return ".";
        }
        return root.relativize(path).toString().replace('\\', '/');
    }
}
