package io.github.lnyocly.ai4j.model;

import java.util.Map;

/**
 * An ordered content block inside a {@link Message}.
 *
 * <p>Block instances are immutable. {@link #getBlockId()} is the SDK business
 * identity and is never mixed with protocol ids; protocol ids live in
 * {@link #getProtocolData()}.</p>
 */
public interface Block {

    BlockType getType();

    /** SDK business identity of this block; never a protocol id. */
    String getBlockId();

    /** Business correlation metadata; never serialized to the wire (design C13). */
    Map<String, Object> getApplicationMetadata();

    /** Protocol replay data kept for same-protocol history; never blindly forwarded. */
    Map<String, Object> getProtocolData();
}
