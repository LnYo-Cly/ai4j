package io.github.lnyocly.ai4j.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BuiltInToolContext {

    @Builder.Default
    private String workspaceRoot = Paths.get(".").toAbsolutePath().normalize().toString();

    @Builder.Default
    private boolean allowOutsideWorkspace = false;

    @Builder.Default
    private List<String> allowedReadRoots = new ArrayList<String>();

    @Builder.Default
    private int defaultReadMaxChars = 12000;

    @Builder.Default
    private long defaultCommandTimeoutMs = 30000L;

    @Builder.Default
    private int defaultBashLogChars = 12000;

    @Builder.Default
    private int maxProcessOutputChars = 120000;

    @Builder.Default
    private long processStopGraceMs = 1000L;

    private transient BuiltInProcessRegistry processRegistry;

    public Path getWorkspaceRootPath() {
        if (isBlank(workspaceRoot)) {
            return Paths.get(".").toAbsolutePath().normalize();
        }
        return Paths.get(workspaceRoot).toAbsolutePath().normalize();
    }

    public Path resolveWorkspacePath(String path) {
        Path root = getWorkspaceRootPath();
        if (isBlank(path)) {
            return root;
        }
        Path candidate = Paths.get(path);
        if (!candidate.isAbsolute()) {
            candidate = root.resolve(path);
        }
        candidate = candidate.toAbsolutePath().normalize();
        if (!allowOutsideWorkspace && !candidate.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes workspace root: " + path);
        }
        return candidate;
    }

    public Path resolveReadablePath(String path) {
        Path root = getWorkspaceRootPath();
        if (isBlank(path)) {
            return root;
        }
        Path candidate = Paths.get(path);
        if (!candidate.isAbsolute()) {
            candidate = root.resolve(path);
        }
        candidate = candidate.toAbsolutePath().normalize();
        if (allowOutsideWorkspace || candidate.startsWith(root)) {
            return candidate;
        }
        for (Path allowedRoot : getAllowedReadRootPaths()) {
            if (candidate.startsWith(allowedRoot)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Path escapes workspace root: " + path);
    }

    public List<Path> getAllowedReadRootPaths() {
        if (allowedReadRoots == null || allowedReadRoots.isEmpty()) {
            return Collections.emptyList();
        }
        List<Path> paths = new ArrayList<Path>();
        for (String allowedReadRoot : allowedReadRoots) {
            if (isBlank(allowedReadRoot)) {
                continue;
            }
            paths.add(Paths.get(allowedReadRoot).toAbsolutePath().normalize());
        }
        return paths;
    }

    BuiltInProcessRegistry getOrCreateProcessRegistry() {
        if (processRegistry != null) {
            return processRegistry;
        }
        synchronized (this) {
            if (processRegistry == null) {
                processRegistry = new BuiltInProcessRegistry(this);
            }
            return processRegistry;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
