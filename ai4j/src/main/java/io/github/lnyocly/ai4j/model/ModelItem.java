package io.github.lnyocly.ai4j.model;

import java.util.Map;

/**
 * A top-level ordered unit of a conversation turn.
 *
 * <p>{@link #getItemId()} is the protocol-native item id (for example a
 * Responses {@code item_id}); it is never a business identity.</p>
 */
public interface ModelItem {

    ItemType getType();

    /** Protocol-native item id, or {@code null} when the protocol has none. */
    String getItemId();

    /** Business correlation metadata; never serialized to the wire (design C13). */
    Map<String, Object> getApplicationMetadata();

    /** Protocol replay data kept for same-protocol history resends. */
    Map<String, Object> getProtocolData();
}
