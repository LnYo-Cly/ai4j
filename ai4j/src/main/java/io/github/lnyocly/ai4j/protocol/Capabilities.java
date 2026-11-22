package io.github.lnyocly.ai4j.protocol;

import io.github.lnyocly.ai4j.model.Protocol;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * An immutable capability set resolved as
 * {@code protocol baseline ∩ profile narrowing ∩ endpoint override} (design 4.6).
 */
public final class Capabilities {

    private final Protocol protocol;
    private final Set<Capability> capabilities;

    private Capabilities(Protocol protocol, Set<Capability> capabilities) {
        this.protocol = protocol;
        this.capabilities = capabilities.isEmpty()
                ? Collections.<Capability>emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
    }

    public static Capabilities of(Protocol protocol, Set<Capability> capabilities) {
        return new Capabilities(protocol, capabilities == null
                ? EnumSet.noneOf(Capability.class)
                : EnumSet.copyOf(capabilities));
    }

    public static Capabilities none(Protocol protocol) {
        return new Capabilities(protocol, EnumSet.noneOf(Capability.class));
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public boolean supports(Capability capability) {
        return capabilities.contains(capability);
    }

    public Set<Capability> asSet() {
        return capabilities;
    }

    /** Intersection with another set; the capability is available only in both. */
    public Capabilities intersect(Set<Capability> other) {
        if (other == null) {
            return this;
        }
        EnumSet<Capability> result = EnumSet.noneOf(Capability.class);
        for (Capability capability : capabilities) {
            if (other.contains(capability)) {
                result.add(capability);
            }
        }
        return new Capabilities(protocol, result);
    }

    /** Remove every capability in {@code other}; the endpoint-disabled layer (design 4.6). */
    public Capabilities subtract(Set<Capability> other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        EnumSet<Capability> result = EnumSet.noneOf(Capability.class);
        for (Capability capability : capabilities) {
            if (!other.contains(capability)) {
                result.add(capability);
            }
        }
        return new Capabilities(protocol, result);
    }

    public Capabilities plus(Set<Capability> other) {
        EnumSet<Capability> result = EnumSet.noneOf(Capability.class);
        result.addAll(capabilities);
        if (other != null) {
            result.addAll(other);
        }
        return new Capabilities(protocol, result);
    }

    /**
     * Fail fast when the capability is not declared.
     *
     * @throws UnsupportedCapabilityException when the capability is unavailable
     */
    public void require(Capability capability) {
        if (!supports(capability)) {
            throw new UnsupportedCapabilityException(protocol, capability,
                    "capability " + capability + " is not available for protocol " + protocol);
        }
    }

    @Override
    public String toString() {
        return "Capabilities{" + protocol + ", " + capabilities + '}';
    }
}
