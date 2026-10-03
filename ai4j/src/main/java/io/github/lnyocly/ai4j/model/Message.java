package io.github.lnyocly.ai4j.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A conversational message with an ordered list of {@link Block}s.
 *
 * <p>Instances are immutable and always defensively copy their inputs, so a
 * caller can never mutate kernel state and the kernel can never rewrite caller
 * input (design C11).</p>
 */
public final class Message implements ModelItem {

    private final MessageRole role;
    private final List<Block> blocks;
    private final String itemId;
    private final Map<String, Object> applicationMetadata;
    private final Map<String, Object> protocolData;
    private final String name;

    private Message(MessageRole role, List<Block> blocks, String itemId, String name,
                    Map<String, Object> applicationMetadata, Map<String, Object> protocolData) {
        this.role = Objects.requireNonNull(role, "role is required");
        this.blocks = immutableBlocks(blocks);
        this.itemId = itemId;
        this.name = name;
        this.applicationMetadata = JsonValues.copyObject(applicationMetadata);
        this.protocolData = JsonValues.copyObject(protocolData);
    }

    private static List<Block> immutableBlocks(List<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return Collections.emptyList();
        }
        List<Block> copy = new ArrayList<Block>(blocks.size());
        for (Block block : blocks) {
            copy.add(Objects.requireNonNull(block, "message blocks must not be null"));
        }
        return Collections.unmodifiableList(copy);
    }

    public static Builder user() {
        return new Builder(MessageRole.USER);
    }

    public static Builder assistant() {
        return new Builder(MessageRole.ASSISTANT);
    }

    public static Builder system() {
        return new Builder(MessageRole.SYSTEM);
    }

    public static Builder tool() {
        return new Builder(MessageRole.TOOL);
    }

    public static Builder builder(MessageRole role) {
        return new Builder(role);
    }

    @Override
    public ItemType getType() {
        return ItemType.MESSAGE;
    }

    @Override
    public String getItemId() {
        return itemId;
    }

    public MessageRole getRole() {
        return role;
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    /** Participant name for protocols that support it, or {@code null}. */
    public String getName() {
        return name;
    }

    @Override
    public Map<String, Object> getApplicationMetadata() {
        return applicationMetadata;
    }

    @Override
    public Map<String, Object> getProtocolData() {
        return protocolData;
    }

    /** Concatenated text of all {@link TextBlock}s in order, ignoring other block kinds. */
    public String textContent() {
        StringBuilder sb = new StringBuilder();
        for (Block block : blocks) {
            if (block instanceof TextBlock) {
                sb.append(((TextBlock) block).getText());
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "Message{role=" + role + ", blocks=" + blocks.size() + '}';
    }

    /** Fluent builder for immutable {@link Message} instances. */
    public static final class Builder {

        private final MessageRole role;
        private final List<Block> blocks = new ArrayList<Block>();
        private String itemId;
        private String name;
        private Map<String, Object> applicationMetadata;
        private Map<String, Object> protocolData;

        private Builder(MessageRole role) {
            this.role = role;
        }

        public Builder block(Block block) {
            blocks.add(Objects.requireNonNull(block, "block is required"));
            return this;
        }

        public Builder blocks(List<Block> blocks) {
            if (blocks != null) {
                for (Block block : blocks) {
                    block(block);
                }
            }
            return this;
        }

        public Builder text(String text) {
            return block(TextBlock.of(text));
        }

        public Builder itemId(String itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder applicationMetadata(Map<String, Object> applicationMetadata) {
            this.applicationMetadata = applicationMetadata;
            return this;
        }

        public Builder protocolData(Map<String, Object> protocolData) {
            this.protocolData = protocolData;
            return this;
        }

        public Message build() {
            return new Message(role, blocks, itemId, name, applicationMetadata, protocolData);
        }
    }
}
