package io.github.lnyocly.ai4j.agent.sandbox;

/**
 * Portable metadata for one file/artifact produced by a sandbox session.
 */
public final class SandboxArtifact {

    private final String artifactId;
    private final String name;
    private final String path;
    private final String mimeType;
    private final Long sizeBytes;
    private final String sha256;

    private SandboxArtifact(Builder builder) {
        this.artifactId = builder.artifactId;
        this.name = builder.name;
        this.path = builder.path;
        this.mimeType = builder.mimeType;
        this.sizeBytes = builder.sizeBytes;
        this.sha256 = builder.sha256;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public SandboxArtifact copy() {
        return builder()
                .artifactId(artifactId)
                .name(name)
                .path(path)
                .mimeType(mimeType)
                .sizeBytes(sizeBytes)
                .sha256(sha256)
                .build();
    }

    public static final class Builder {
        private String artifactId;
        private String name;
        private String path;
        private String mimeType;
        private Long sizeBytes;
        private String sha256;

        private Builder() {
        }

        public Builder artifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public Builder sizeBytes(Long sizeBytes) {
            this.sizeBytes = sizeBytes;
            return this;
        }

        public Builder sha256(String sha256) {
            this.sha256 = sha256;
            return this;
        }

        public SandboxArtifact build() {
            return new SandboxArtifact(this);
        }
    }
}
