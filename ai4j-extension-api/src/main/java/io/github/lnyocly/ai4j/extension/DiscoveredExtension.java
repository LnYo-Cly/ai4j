package io.github.lnyocly.ai4j.extension;

public final class DiscoveredExtension {

    private final ExtensionManifest manifest;
    private final Ai4jExtension extension;
    private final String sourceClassName;
    private final boolean enabled;

    public DiscoveredExtension(ExtensionManifest manifest, Ai4jExtension extension, boolean enabled) {
        if (manifest == null) {
            throw new IllegalArgumentException("extension manifest must not be null");
        }
        if (extension == null) {
            throw new IllegalArgumentException("extension must not be null");
        }
        this.manifest = manifest;
        this.extension = extension;
        this.sourceClassName = extension.getClass().getName();
        this.enabled = enabled;
    }

    public ExtensionManifest getManifest() {
        return manifest;
    }

    public Ai4jExtension getExtension() {
        return extension;
    }

    public String getSourceClassName() {
        return sourceClassName;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
