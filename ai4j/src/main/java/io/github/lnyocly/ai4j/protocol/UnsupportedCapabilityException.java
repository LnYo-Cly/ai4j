package io.github.lnyocly.ai4j.protocol;

import io.github.lnyocly.ai4j.exception.Ai4jException;
import io.github.lnyocly.ai4j.model.Protocol;

/**
 * Thrown when a declared capability is not available for the resolved
 * protocol/profile/endpoint combination (design 4.6). Capability checks read
 * the declared matrix only; they never guess from tool names (C12).
 */
public class UnsupportedCapabilityException extends Ai4jException {

    private final Protocol protocol;
    private final Capability capability;

    public UnsupportedCapabilityException(Protocol protocol, Capability capability, String message) {
        super(message);
        this.protocol = protocol;
        this.capability = capability;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public Capability getCapability() {
        return capability;
    }
}
