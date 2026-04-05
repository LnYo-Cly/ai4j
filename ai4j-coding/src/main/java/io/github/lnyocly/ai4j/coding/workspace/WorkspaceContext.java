package io.github.lnyocly.ai4j.coding.workspace;

import io.github.lnyocly.ai4j.coding.skill.CodingSkillDescriptor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceContext {

    @Builder.Default
    private String rootPath = Paths.get(".").toAbsolutePath().normalize().toString();

    @Builder.Default
    private List<String> excludedPaths = defaultExcludedPaths();

    @Builder.Default
    private boolean allowOutsideWorkspace = false;

    private String description;

    @Builder.Default
    private List<String> skillDirectories = new ArrayList<String>();

    @Builder.Default
    private List<String> allowedReadRoots = new ArrayList<String>();

    @Builder.Default
    private List<CodingSkillDescriptor> availableSkills = new ArrayList<CodingSkillDescriptor>();

    public Path getRoot() {
        return Paths.get(rootPath).toAbsolutePath().normalize();
    }

    public Path resolveWorkspacePath(String path) {
        Path root = getRoot();
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
        Path root = getRoot();
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

    public boolean isExcluded(Path absolutePath) {
        Path root = getRoot();
        if (absolutePath == null || !absolutePath.startsWith(root)) {
            return false;
        }
        Path relative = root.relativize(absolutePath);
        for (Path part : relative) {
            if (excludedPaths.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> defaultExcludedPaths() {
        return new ArrayList<>(Arrays.asList(".git", "target", "node_modules", ".idea"));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
