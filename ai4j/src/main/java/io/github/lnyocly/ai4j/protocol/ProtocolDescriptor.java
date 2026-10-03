package io.github.lnyocly.ai4j.protocol;

import io.github.lnyocly.ai4j.model.Protocol;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Static facts about one wire protocol: default route, codec, baseline
 * capabilities and protocol headers.
 *
 * <p>The baseline describes what the protocol specification allows; a
 * {@link ProviderProfile} narrows it and an endpoint override can adjust it.</p>
 */
public final class ProtocolDescriptor {

    private final Protocol protocol;
    private final ProtocolCodec codec;
    private final String defaultPath;
    private final Capabilities baselineCapabilities;
    private final Set<Capability> protocolDisabled;
    private final boolean anthropicVersionHeader;
    private final String acceptedMediaType;

    private ProtocolDescriptor(Protocol protocol, ProtocolCodec codec, String defaultPath,
                               Capabilities baselineCapabilities, Set<Capability> protocolDisabled,
                               boolean anthropicVersionHeader, String acceptedMediaType) {
        this.protocol = Objects.requireNonNull(protocol, "protocol is required");
        this.codec = Objects.requireNonNull(codec, "codec is required");
        this.defaultPath = defaultPath;
        this.baselineCapabilities = baselineCapabilities;
        this.protocolDisabled = protocolDisabled == null
                ? Collections.<Capability>emptySet()
                : Collections.unmodifiableSet(protocolDisabled);
        this.anthropicVersionHeader = anthropicVersionHeader;
        this.acceptedMediaType = acceptedMediaType;
    }

    public static Builder builder(Protocol protocol, ProtocolCodec codec, String defaultPath) {
        return new Builder(protocol, codec, defaultPath);
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public ProtocolCodec getCodec() {
        return codec;
    }

    /** Default protocol route when the endpoint does not declare one. */
    public String getDefaultPath() {
        return defaultPath;
    }

    public Capabilities getBaselineCapabilities() {
        return baselineCapabilities;
    }

    /** Capabilities the protocol specification itself does not define (design 6.6 "无"). */
    public Set<Capability> getProtocolDisabled() {
        return protocolDisabled;
    }

    /** Whether the transport must send the {@code anthropic-version} header. */
    public boolean isAnthropicVersionHeader() {
        return anthropicVersionHeader;
    }

    /** Media type for the request body, or {@code null} for the default JSON type. */
    public String getAcceptedMediaType() {
        return acceptedMediaType;
    }

    @Override
    public String toString() {
        return "ProtocolDescriptor{" + protocol + ", path=" + defaultPath + '}';
    }

    /** Fluent builder. */
    public static final class Builder {

        private final Protocol protocol;
        private final ProtocolCodec codec;
        private final String defaultPath;
        private final EnumSet<Capability> baseline = EnumSet.noneOf(Capability.class);
        private final EnumSet<Capability> protocolDisabled = EnumSet.noneOf(Capability.class);
        private boolean anthropicVersionHeader;
        private String acceptedMediaType;

        private Builder(Protocol protocol, ProtocolCodec codec, String defaultPath) {
            this.protocol = protocol;
            this.codec = codec;
            this.defaultPath = defaultPath;
        }

        public Builder capability(Capability capability) {
            this.baseline.add(capability);
            return this;
        }

        public Builder capabilities(Set<Capability> capabilities) {
            if (capabilities != null) {
                this.baseline.addAll(capabilities);
            }
            return this;
        }

        /** Mark a capability as undefined by this protocol specification. */
        public Builder unsupported(Capability capability) {
            this.protocolDisabled.add(capability);
            return this;
        }

        /** Whether the transport must send the {@code anthropic-version} header. */
        public Builder anthropicVersionHeader(boolean anthropicVersionHeader) {
            this.anthropicVersionHeader = anthropicVersionHeader;
            return this;
        }

        public Builder acceptedMediaType(String acceptedMediaType) {
            this.acceptedMediaType = acceptedMediaType;
            return this;
        }

        public ProtocolDescriptor build() {
            return new ProtocolDescriptor(protocol, codec, defaultPath,
                    Capabilities.of(protocol, baseline), protocolDisabled,
                    anthropicVersionHeader, acceptedMediaType);
        }
    }
}
