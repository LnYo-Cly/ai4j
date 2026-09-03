package io.github.lnyocly.ai4j.coding.tool;

import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Security guard for write/patch tool executors.
 *
 * <p>Layered checks on top of {@link WorkspaceContext#resolveWorkspacePath(String)}:
 * <ol>
 *   <li>Workspace boundary check (delegates to {@code WorkspaceContext})</li>
 *   <li>Symlink resolution (up to {@value #MAX_SYMLINK_DEPTH} hops) to defeat symlink escapes</li>
 *   <li>Blacklist of sensitive directory trees ({@code .git/hooks}, {@code .ssh}, {@code .aws})</li>
 *   <li>{@code excludedPaths} check (e.g. {@code .git}, {@code target}) so writes inside
 *       excluded directories are rejected even when the path is inside the workspace</li>
 * </ol>
 */
public final class WorkspacePathGuard {

    /** Maximum symlink resolution hops before giving up (prevents symlink loops). */
    static final int MAX_SYMLINK_DEPTH = 8;

    private static final List<String> BLACKLISTED_DIR_NAMES = Collections.unmodifiableList(
            new ArrayList<String>(Arrays.asList(".ssh", ".aws"))
    );

    private static final String GIT_DIR_NAME = ".git";
    private static final String GIT_HOOKS_DIR_NAME = "hooks";

    private WorkspacePathGuard() {
    }

    /**
     * Resolve and validate a write target path.
     *
     * @param workspaceContext the active workspace context
     * @param path             the raw path from the tool call (relative or absolute)
     * @return the resolved, validated absolute path
     * @throws IllegalArgumentException if the path escapes the workspace, hits a blacklisted
     *                                  directory, resolves through too many symlinks, or is inside
     *                                  an excluded path
     * @throws IOException              if symlink resolution fails due to an I/O error
     */
    public static Path resolveForWrite(WorkspaceContext workspaceContext, String path) throws IOException {
        if (workspaceContext == null) {
            throw new IllegalArgumentException("workspaceContext is required");
        }
        Path resolved = workspaceContext.resolveWorkspacePath(path);
        Path canonical = resolveSymlinks(resolved);
        // Re-check boundary after symlink resolution — a symlink may point outside the workspace.
        if (!workspaceContext.isAllowOutsideWorkspace() && !canonical.startsWith(workspaceContext.getRoot())) {
            throw new IllegalArgumentException(
                    "Resolved path escapes workspace root after symlink resolution: " + path
                            + " -> " + canonical);
        }
        rejectBlacklisted(canonical, workspaceContext.getRoot(), path);
        rejectExcluded(canonical, workspaceContext, path);
        return canonical;
    }

    /**
     * Follow symbolic links up to {@link #MAX_SYMLINK_DEPTH} hops, using a visited-set
     * to detect cycles in an OS-independent manner.
     */
    static Path resolveSymlinks(Path path) throws IOException {
        if (path == null) {
            return null;
        }
        return resolveSymlinks(path, new HashSet<Path>());
    }

    private static Path resolveSymlinks(Path path, Set<Path> visited) throws IOException {
        Path current = path;
        for (int i = 0; i < MAX_SYMLINK_DEPTH; i++) {
            Path normalized = current.toAbsolutePath().normalize();
            if (!visited.add(normalized)) {
                throw new IOException("Symlink loop detected: " + path);
            }
            // Check isSymbolicLink BEFORE exists: a symlink whose target is in a loop
            // reports exists()=false, but we still need to follow it to detect the cycle.
            if (Files.isSymbolicLink(current)) {
                Path target = Files.readSymbolicLink(current);
                if (!target.isAbsolute()) {
                    target = current.getParent().resolve(target);
                }
                current = target.toAbsolutePath().normalize();
                continue;
            }
            if (!Files.exists(current)) {
                // The file doesn't exist yet (it's about to be created).
                // Resolve the parent if it exists or is itself a symlink (whose target
                // may be in a loop, causing exists()=false).
                Path parent = current.getParent();
                if (parent != null
                        && !(current.isAbsolute() && parent.equals(current.getRoot()))
                        && (Files.exists(parent) || Files.isSymbolicLink(parent))) {
                    Path realParent = resolveSymlinks(parent, visited);
                    current = realParent.resolve(current.getFileName());
                }
                break;
            }
            // current exists and is not a symlink — but parent might be.
            Path parent = current.getParent();
            if (parent == null) {
                break;
            }
            if (current.isAbsolute() && parent.equals(current.getRoot())) {
                break;
            }
            if (!Files.isSymbolicLink(parent)) {
                break;
            }
            Path realParent = resolveSymlinks(parent, visited);
            current = realParent.resolve(current.getFileName());
        }
        if (Files.isSymbolicLink(current)) {
            throw new IOException(
                    "Symbolic link depth exceeded (" + MAX_SYMLINK_DEPTH
                            + ") — possible symlink loop: " + path);
        }
        return current.toAbsolutePath().normalize();
    }

    /**
     * Reject paths inside sensitive directories: {@code .git/hooks/**}, {@code .ssh/**},
     * {@code .aws/**}.
     */
    static void rejectBlacklisted(Path canonical, Path workspaceRoot, String originalPath) {
        Path relative = relativizeSafe(canonical, workspaceRoot);
        if (relative == null) {
            return;
        }
        List<String> parts = pathParts(relative);
        if (parts.isEmpty()) {
            return;
        }
        // .ssh and .aws anywhere in the path
        for (String part : parts) {
            if (BLACKLISTED_DIR_NAMES.contains(part)) {
                throw new IllegalArgumentException(
                        "Write to sensitive directory is blocked: " + originalPath);
            }
        }
        // .git/hooks specifically (allows .git/ignore, .git/HEAD reads elsewhere, but blocks hooks)
        int gitIndex = indexOfGit(parts);
        if (gitIndex >= 0 && gitIndex + 1 < parts.size()
                && GIT_HOOKS_DIR_NAME.equalsIgnoreCase(parts.get(gitIndex + 1))) {
            throw new IllegalArgumentException(
                    "Write to .git/hooks is blocked (potential hook backdoor): " + originalPath);
        }
    }

    /**
     * Reject writes inside directories listed in {@link WorkspaceContext#getExcludedPaths()}.
     * The message doubles as model guidance: excluded entries are the caller's
     * write policy (defaults like {@code .git}, or declared inputs/tests), so the
     * model is told to redirect instead of retrying.
     */
    static void rejectExcluded(Path canonical, WorkspaceContext workspaceContext, String originalPath) {
        if (workspaceContext.isExcluded(canonical)) {
            throw new IllegalArgumentException(
                    "Write to excluded path is blocked by workspace write policy: " + originalPath
                            + ". This location is protected (task input, tests, or system area) —"
                            + " do not modify it; produce outputs elsewhere per the task instructions.");
        }
    }

    private static Path relativizeSafe(Path canonical, Path workspaceRoot) {
        if (canonical == null || workspaceRoot == null) {
            return null;
        }
        if (!canonical.startsWith(workspaceRoot)) {
            return null;
        }
        try {
            return workspaceRoot.relativize(canonical);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<String> pathParts(Path relative) {
        List<String> parts = new ArrayList<String>();
        for (Path part : relative) {
            parts.add(part.toString());
        }
        return parts;
    }

    private static int indexOfGit(List<String> parts) {
        for (int i = 0; i < parts.size(); i++) {
            if (GIT_DIR_NAME.equalsIgnoreCase(parts.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
