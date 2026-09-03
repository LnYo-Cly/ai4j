package io.github.lnyocly.ai4j.coding.workspace;

import io.github.lnyocly.ai4j.coding.skill.CodingSkillDescriptor;
import io.github.lnyocly.ai4j.coding.prompt.CodingPromptDescriptor;
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

    @Builder.Default
    private List<CodingPromptDescriptor> availablePrompts = new ArrayList<CodingPromptDescriptor>();

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

    /**
     * Write-policy check used by {@code WorkspacePathGuard}. A plain entry
     * (no {@code *} / {@code ?}) keeps the historical behavior: it matches any
     * single path segment with that name anywhere in the tree (e.g. {@code in}
     * protects every {@code in/} directory). An entry containing {@code *} or
     * {@code ?} is treated as a glob over the root-relative path with
     * {@code /} separators, where {@code **} spans directories
     * (e.g. a pattern like doublestar-slash-test_underscore-star-dot-py
     * protects test files at any depth).
     */
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
        String relativePath = relative.toString().replace('\\', '/');
        for (String pattern : excludedPaths) {
            if (isGlobPattern(pattern) && globMatches(pattern, relativePath)) {
                return true;
            }
        }
        return false;
    }

    static boolean isGlobPattern(String pattern) {
        return pattern != null && (pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0);
    }

    /**
     * Minimal glob matcher for root-relative {@code /}-separated paths:
     * {@code **} spans directory boundaries, {@code *} matches within one
     * segment, {@code ?} matches one non-separator character.
     */
    static boolean globMatches(String pattern, String relativePath) {
        return globMatches(pattern, 0, relativePath, 0);
    }

    private static boolean globMatches(String pattern, int p, String path, int i) {
        while (p < pattern.length()) {
            char pc = pattern.charAt(p);
            if (pc == '*') {
                boolean doubleStar = p + 1 < pattern.length() && pattern.charAt(p + 1) == '*';
                if (doubleStar) {
                    p += 2;
                    // "**/" collapses to zero or more segments: try the rest
                    // directly at i (zero dirs), then after every separator.
                    if (p < pattern.length() && pattern.charAt(p) == '/') {
                        p++;
                        if (globMatches(pattern, p, path, i)) {
                            return true;
                        }
                        for (int skip = i; skip < path.length(); skip++) {
                            if (path.charAt(skip) == '/' && globMatches(pattern, p, path, skip + 1)) {
                                return true;
                            }
                        }
                        return false;
                    }
                    if (globMatches(pattern, p, path, path.length())) {
                        return true;
                    }
                    for (int skip = i; skip < path.length(); skip++) {
                        if (globMatches(pattern, p, path, skip)) {
                            return true;
                        }
                    }
                    return false;
                }
                int nextSlash = path.indexOf('/', i);
                int segmentEnd = nextSlash < 0 ? path.length() : nextSlash;
                for (int end = path.length(); end >= i; end--) {
                    if (end <= segmentEnd && globMatches(pattern, p + 1, path, end)) {
                        return true;
                    }
                }
                return false;
            }
            if (pc == '?') {
                if (i >= path.length() || path.charAt(i) == '/') {
                    return false;
                }
                p++;
                i++;
                continue;
            }
            if (i >= path.length() || path.charAt(i) != pc) {
                return false;
            }
            p++;
            i++;
        }
        return i == path.length();
    }

    /**
     * The default write-policy entries (VCS/build/IDE areas). Exposed so
     * callers extending {@code excludedPaths} (e.g. protected inputs) can
     * append without losing these.
     */
    public static List<String> defaultExcludedPaths() {
        return new ArrayList<>(Arrays.asList(".git", "target", "node_modules", ".idea"));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
