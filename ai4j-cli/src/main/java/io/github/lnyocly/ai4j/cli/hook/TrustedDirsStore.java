package io.github.lnyocly.ai4j.cli.hook;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages the {@code ~/.ai4j/trusted-dirs.txt} file — a persistent record of workspace
 * directories whose {@code .ai4j/workspace.json} hooks the user has explicitly trusted.
 *
 * <p>The file contains one absolute path per line. Lines starting with {@code #} are comments.
 * The file is created on first trust; if it does not exist, no directory is trusted.</p>
 */
public class TrustedDirsStore {

    private final Path storeFile;

    public TrustedDirsStore() {
        this(defaultStoreFile());
    }

    public TrustedDirsStore(Path storeFile) {
        this.storeFile = storeFile;
    }

    /**
     * Returns the default store path: {@code ~/.ai4j/trusted-dirs.txt}.
     */
    public static Path defaultStoreFile() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".ai4j", "trusted-dirs.txt");
    }

    /**
     * Checks whether the given directory is in the trusted list.
     *
     * @param dir the workspace directory (compared by canonical absolute path)
     * @return {@code true} if the directory is trusted
     */
    public boolean isTrusted(Path dir) {
        if (dir == null) {
            return false;
        }
        String canonical = canonicalize(dir);
        if (canonical == null) {
            return false;
        }
        for (String trusted : readTrustedDirs()) {
            if (canonical.equals(trusted)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds the given directory to the trusted list. If already present, this is a no-op.
     *
     * @param dir the workspace directory to trust
     * @throws IOException if the store file cannot be written
     */
    public void trust(Path dir) throws IOException {
        if (dir == null) {
            throw new IllegalArgumentException("dir must not be null");
        }
        String canonical = canonicalize(dir);
        if (canonical == null) {
            throw new IllegalArgumentException("cannot resolve canonical path for " + dir);
        }
        Set<String> trusted = new LinkedHashSet<String>(readTrustedDirs());
        if (trusted.add(canonical)) {
            writeTrustedDirs(new ArrayList<String>(trusted));
        }
    }

    /**
     * Removes the given directory from the trusted list. If not present, this is a no-op.
     *
     * @param dir the workspace directory to revoke
     * @return {@code true} if the directory was previously trusted and is now removed
     * @throws IOException if the store file cannot be written
     */
    public boolean revoke(Path dir) throws IOException {
        if (dir == null) {
            return false;
        }
        String canonical = canonicalize(dir);
        if (canonical == null) {
            return false;
        }
        List<String> trusted = readTrustedDirs();
        boolean removed = false;
        List<String> updated = new ArrayList<String>();
        for (String entry : trusted) {
            if (entry.equals(canonical)) {
                removed = true;
            } else {
                updated.add(entry);
            }
        }
        if (removed) {
            writeTrustedDirs(updated);
        }
        return removed;
    }

    /**
     * Returns an unmodifiable copy of all trusted directory paths (canonical absolute).
     */
    public List<String> getTrustedDirs() {
        return Collections.unmodifiableList(new ArrayList<String>(readTrustedDirs()));
    }

    // ---- internals ----

    private List<String> readTrustedDirs() {
        if (!Files.exists(storeFile)) {
            return Collections.emptyList();
        }
        try {
            List<String> lines = Files.readAllLines(storeFile, StandardCharsets.UTF_8);
            List<String> result = new ArrayList<String>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                result.add(trimmed);
            }
            return result;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private void writeTrustedDirs(List<String> dirs) throws IOException {
        if (storeFile.getParent() != null) {
            Files.createDirectories(storeFile.getParent());
        }
        List<String> lines = new ArrayList<String>();
        lines.add("# ai4j trusted workspace directories — do not edit while ai4j is running.");
        lines.add("# Each line is a canonical absolute path whose .ai4j/workspace.json hooks are trusted.");
        for (String dir : dirs) {
            lines.add(dir);
        }
        Files.write(storeFile, lines, StandardCharsets.UTF_8);
    }

    private static String canonicalize(Path dir) {
        try {
            return dir.toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            return null;
        }
    }
}
