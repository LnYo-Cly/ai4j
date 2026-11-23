package io.github.lnyocly.ai4j.model;

import java.util.Map;
import java.util.Objects;

/**
 * Protocol-native item preserved verbatim.
 *
 * <p>Native items are carried in-model and reported; they are never serialized
 * to a target wire unless the encode policy proves they are replay-valid for
 * that target (design F3/R2).</p>
 */
public final class NativeItem implements ModelItem {

    private final Protocol protocol;
    private final Map<String, Object> payload;
    private final String itemId;
    private final Map<String, Object> applicationMetadata;
    private final Map<String, Object> protocolData;

    private NativeItem(Protocol protocol, Map<String, Object> payload, String itemId,
                       Map<String, Object> applicationMetadata, Map<String, Object> protocolData) {
        this.protocol = Objects.requireNonNull(protocol, "protocol is required");
        this.payload = JsonValues.copyObject(Objects.requireNonNull(payload, "payload is required"));
        this.itemId = itemId;
        this.applicationMetadata = JsonValues.copyObject(applicationMetadata);
        this.protocolData = JsonValues.copyObject(protocolData);
    }

    public static NativeItem of(Protocol protocol, Map<String, Object> payload) {
        return new NativeItem(protocol, payload, null, null, null);
    }

    public static NativeItem of(Protocol protocol, Map<String, Object> payload, String itemId,
                                Map<String, Object> applicationMetadata,
                                Map<String, Object> protocolData) {
        return new NativeItem(protocol, payload, itemId, applicationMetadata, protocolData);
    }

    @Override
    public ItemType getType() {
        return ItemType.NATIVE;
    }

    @Override
    public String getItemId() {
        return itemId;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    @Override
    public Map<String, Object> getApplicationMetadata() {
        return applicationMetadata;
    }

    @Override
    public Map<String, Object> getProtocolData() {
        return protocolData;
    }

    @Override
    public String toString() {
        return "NativeItem{protocol=" + protocol + '}';
    }
}
