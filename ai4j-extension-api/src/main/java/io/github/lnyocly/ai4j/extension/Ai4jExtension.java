package io.github.lnyocly.ai4j.extension;

/**
 * Entry point implemented by third-party AI4J extension packages.
 */
public interface Ai4jExtension {

    ExtensionManifest manifest();

    void apply(ExtensionContext context);
}
