package io.github.lnyocly.ai4j.protocol;

import io.github.lnyocly.ai4j.model.Protocol;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The resolved policy a codec reads while encoding a request.
 *
 * <p>Combines the protocol descriptor baseline with the endpoint's profile and
 * overrides (design 4.6). Codecs only read this object; they never branch on a
 * vendor name.</p>
 */
public final class EndpointPolicy {

    private final Protocol protocol;
    private final ProtocolDescriptor descriptor;
    private final ProviderProfile profile;
    private final Capabilities capabilities;
    private final String path;
    private final Map<String, String> extraHeaders;

    private EndpointPolicy(Protocol protocol, ProtocolDescriptor descriptor, ProviderProfile profile,
                           Capabilities capabilities, String path, Map<String, String> extraHeaders) {
        this.protocol = protocol;
        this.descriptor = descriptor;
        this.profile = profile;
        this.capabilities = capabilities;
        this.path = path;
        this.extraHeaders = extraHeaders == null || extraHeaders.isEmpty()
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(extraHeaders));
    }

    /** Resolve the policy for an endpoint from its descriptor baseline (design 4.6). */
    public static EndpointPolicy resolve(Endpoint endpoint, ProtocolDescriptor descriptor) {
        Objects.requireNonNull(endpoint, "endpoint is required");
        Objects.requireNonNull(descriptor, "descriptor is required");
        if (descriptor.getProtocol() != endpoint.getProtocol()) {
            throw new IllegalArgumentException("descriptor protocol " + descriptor.getProtocol()
                    + " does not match endpoint protocol " + endpoint.getProtocol());
        }
        ProviderProfile profile = endpoint.getProfile();
        Capabilities capabilities = descriptor.getBaselineCapabilities()
                .intersect(profile.getDisabledCapabilities().isEmpty()
                        ? null
                        : complement(profile.getDisabledCapabilities()));
        if (!endpoint.getCapabilityOverrides().isEmpty()) {
            capabilities = capabilities.plus(endpoint.getCapabilityOverrides());
        }
        String path = endpoint.getEndpointPath() != null
                ? endpoint.getEndpointPath()
                : descriptor.getDefaultPath();
        return new EndpointPolicy(endpoint.getProtocol(), descriptor, profile, capabilities, path,
                endpoint.getHeaders());
    }

    private static Set<Capability> complement(Set<Capability> disabled) {
        EnumSet<Capability> enabled = EnumSet.allOf(Capability.class);
        enabled.removeAll(disabled);
        return enabled;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public ProtocolDescriptor getDescriptor() {
        return descriptor;
    }

    public ProviderProfile getProfile() {
        return profile;
    }

    public Capabilities getCapabilities() {
        return capabilities;
    }

    /** Resolved request path for this endpoint. */
    public String getPath() {
        return path;
    }

    public Map<String, String> getExtraHeaders() {
        return extraHeaders;
    }

    @Override
    public String toString() {
        return "EndpointPolicy{" + protocol + ", path=" + path + ", profile=" + profile.getName() + '}';
    }
}
