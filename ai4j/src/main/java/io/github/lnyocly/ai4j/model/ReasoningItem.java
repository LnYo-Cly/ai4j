package io.github.lnyocly.ai4j.model;

import java.util.Map;

/**
 * Model reasoning / thinking item kept for replay.
 *
 * <p>The provider signature is preserved so the reasoning can be replayed
 * inside its protocol/endpoint scope; without a valid signature the item is not
 * replayable to {@code MESSAGES} (design F4/O5).</p>
 */
public final class ReasoningItem implements ModelItem {

    private final String text;
    private final String summary;
    private final String signature;
    private final String itemId;
    private final Map<String, Object> applicationMetadata;
    private final Map<String, Object> protocolData;

    private ReasoningItem(String text, String summary, String signature, String itemId,
                          Map<String, Object> applicationMetadata,
                          Map<String, Object> protocolData) {
        this.text = text;
        this.summary = summary;
        this.signature = signature;
        this.itemId = itemId;
        this.applicationMetadata = JsonValues.copyObject(applicationMetadata);
        this.protocolData = JsonValues.copyObject(protocolData);
    }

    public static ReasoningItem of(String text) {
        return new ReasoningItem(text, null, null, null, null, null);
    }

    public static ReasoningItem of(String text, String signature) {
        return new ReasoningItem(text, null, signature, null, null, null);
    }

    public static ReasoningItem of(String text, String summary, String signature, String itemId,
                                   Map<String, Object> applicationMetadata,
                                   Map<String, Object> protocolData) {
        return new ReasoningItem(text, summary, signature, itemId, applicationMetadata, protocolData);
    }

    @Override
    public ItemType getType() {
        return ItemType.REASONING;
    }

    @Override
    public String getItemId() {
        return itemId;
    }

    public String getText() {
        return text;
    }

    public String getSummary() {
        return summary;
    }

    public String getSignature() {
        return signature;
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
        return "ReasoningItem{signed=" + (signature != null) + '}';
    }
}
