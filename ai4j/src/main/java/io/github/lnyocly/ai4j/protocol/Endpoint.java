package io.github.lnyocly.ai4j.protocol;

import io.github.lnyocly.ai4j.model.JsonValues;
import io.github.lnyocly.ai4j.model.Protocol;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An endpoint is the decision axis of the new chain: protocol + base URL +
 * credential + parameter profile (design 4.1 / O2).
 *
 * <p>There is deliberately no vendor enum and no factory switch: a standard
 * shape vendor onboards by declaring an endpoint. {@code endpointPath} is the
 * protocol route appended to {@code baseUrl}; when {@code null} the descriptor
 * default applies.</p>
 */
public final class Endpoint {

    private final Protocol protocol;
    private final String name;
    private final String baseUrl;
    private final String endpointPath;
    private final Credentials credentials;
    private final ProviderProfile profile;
    private final Map<String, String> headers;
    private final Set<Capability> capabilityOverrides;

    private Endpoint(Protocol protocol, String name, String baseUrl, String endpointPath,
                     Credentials credentials, ProviderProfile profile,
                     Map<String, String> headers, Set<Capability> capabilityOverrides) {
        this.protocol = Objects.requireNonNull(protocol, "protocol is required");
        this.name = name;
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl is required");
        this.endpointPath = endpointPath;
        this.credentials = credentials == null ? Credentials.none() : credentials;
        this.profile = profile == null ? ProviderProfile.CUSTOM : profile;
        this.headers = headers == null || headers.isEmpty()
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new java.util.LinkedHashMap<String, String>(headers));
        this.capabilityOverrides = capabilityOverrides == null
                ? Collections.<Capability>emptySet()
                : Collections.unmodifiableSet(capabilityOverrides);
    }

    public static Builder builder(Protocol protocol) {
        return new Builder(protocol);
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public String getName() {
        return name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /** Explicit protocol route, or {@code null} to use the descriptor default. */
    public String getEndpointPath() {
        return endpointPath;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    public ProviderProfile getProfile() {
        return profile;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /** Endpoint-level capability overrides applied on top of protocol/profile (design 4.6). */
    public Set<Capability> getCapabilityOverrides() {
        return capabilityOverrides;
    }

    @Override
    public String toString() {
        return "Endpoint{" + protocol + ", name=" + name + ", baseUrl=" + baseUrl + '}';
    }

    /** Fluent builder. */
    public static final class Builder {

        private final Protocol protocol;
        private String name;
        private String baseUrl;
        private String endpointPath;
        private Credentials credentials;
        private ProviderProfile profile;
        private final Map<String, String> headers = new java.util.LinkedHashMap<String, String>();
        private final Set<Capability> capabilityOverrides = EnumSet.noneOf(Capability.class);

        private Builder(Protocol protocol) {
            this.protocol = protocol;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder endpointPath(String endpointPath) {
            this.endpointPath = endpointPath;
            return this;
        }

        public Builder credentials(Credentials credentials) {
            this.credentials = credentials;
            return this;
        }

        public Builder profile(ProviderProfile profile) {
            this.profile = profile;
            return this;
        }

        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        /** Declare that this endpoint supports a capability the protocol baseline lacks. */
        public Builder enableCapability(Capability capability) {
            this.capabilityOverrides.add(capability);
            return this;
        }

        /** Declare that this endpoint does not support a baseline capability. */
        public Builder disableCapability(Capability capability) {
            this.capabilityOverrides.remove(capability);
            return this;
        }

        public Endpoint build() {
            return new Endpoint(protocol, name, baseUrl, endpointPath, credentials, profile,
                    headers, capabilityOverrides);
        }
    }
}
