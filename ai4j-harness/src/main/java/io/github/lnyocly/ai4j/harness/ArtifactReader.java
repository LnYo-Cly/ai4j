package io.github.lnyocly.ai4j.harness;

/**
 * Resolves the textual content of a declared artifact or source location.
 * Implementations decide how locations map to storage (workspace files,
 * object stores, test fixtures). Returning {@code null} means the location
 * is absent or unreadable; the gate treats it as a contract failure, not an
 * infrastructure error.
 */
public interface ArtifactReader {

    String read(String location);
}
