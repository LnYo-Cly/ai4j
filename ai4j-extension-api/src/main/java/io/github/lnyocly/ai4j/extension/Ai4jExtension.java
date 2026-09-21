package io.github.lnyocly.ai4j.extension;

/**
 * Entry point implemented by third-party AI4J extension packages.
 */
public interface Ai4jExtension {

    ExtensionManifest manifest();

    void apply(ExtensionContext context);

    /**
     * Called when the extension is disabled or the registry shuts down. Release
     * threads, sockets, file handles, and any callbacks registered during apply.
     * Implementations must be idempotent.
     */
    default void onStop() {
    }
}
