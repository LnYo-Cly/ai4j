package io.github.lnyocly.ai4j.client;

import io.github.lnyocly.ai4j.model.Block;
import io.github.lnyocly.ai4j.model.DroppedField;
import io.github.lnyocly.ai4j.model.EventDeltaKind;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.ModelUsage;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ReasoningBlock;
import io.github.lnyocly.ai4j.model.TextBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional convenience aggregator that turns a stream event sequence into a
 * {@link ModelResult}.
 *
 * <p>The direct channel does not aggregate (design 4.4/11): consumers that want
 * only events just ignore the aggregated result. Deltas are merged by content
 * index so interleaved blocks keep their order.</p>
 */
final class StreamAggregator {

    private final Map<Integer, StreamSlot> slots = new LinkedHashMap<Integer, StreamSlot>();
    private final List<ModelItem> completedItems = new ArrayList<ModelItem>();
    private final List<DroppedField> warnings = new ArrayList<DroppedField>();
    private ModelUsage usage;
    private String stopReason;
    private String responseId;

    void addWarnings(List<DroppedField> encodeWarnings) {
        if (encodeWarnings != null) {
            warnings.addAll(encodeWarnings);
        }
    }

    void accept(ModelEvent event) {
        if (event == null) {
            return;
        }
        switch (event.getType()) {
            case MESSAGE_START:
                if (event.getItemId() != null) {
                    responseId = event.getItemId();
                }
                if (event.getUsage() != null) {
                    usage = event.getUsage();
                }
                break;
            case ITEM_START:
                slotFor(indexOf(event)).start(event);
                break;
            case ITEM_DELTA:
                handleDelta(event);
                break;
            case ITEM_DONE:
                ModelItem completed = slotFor(indexOf(event)).finish(event);
                if (completed != null) {
                    completedItems.add(completed);
                }
                break;
            case USAGE:
                if (event.getUsage() != null) {
                    usage = event.getUsage();
                }
                break;
            case DONE:
                if (event.getUsage() != null) {
                    usage = event.getUsage();
                }
                break;
            default:
                break;
        }
    }

    private void handleDelta(ModelEvent event) {
        if (event.getDeltaKind() == EventDeltaKind.OTHER
                && "message_delta".equals(event.getItemType())) {
            if (event.getDeltaText() != null) {
                stopReason = event.getDeltaText();
            }
            if (event.getUsage() != null) {
                usage = event.getUsage();
            }
            return;
        }
        if (event.getDeltaKind() == null || event.getDeltaText() == null) {
            return;
        }
        StreamSlot slot = slotFor(indexOf(event));
        slot.observe(event);
        if (event.getDeltaKind() == EventDeltaKind.OTHER
                && "signature_delta".equals(event.getItemType())) {
            slot.signature(event.getDeltaText());
            return;
        }
        slot.append(event.getDeltaKind(), event.getDeltaText());
    }

    private static int indexOf(ModelEvent event) {
        if (event.getContentIndex() != null) {
            return event.getContentIndex();
        }
        if (event.getOutputIndex() != null) {
            return event.getOutputIndex();
        }
        return -1;
    }

    private StreamSlot slotFor(int index) {
        StreamSlot slot = slots.get(index);
        if (slot == null) {
            slot = new StreamSlot();
            slots.put(index, slot);
        }
        return slot;
    }

    ModelResult result(Protocol protocol) {
        ModelResult.Builder builder = ModelResult.builder();
        builder.items(completedItems);
        // Slots that never produced an explicit item still contribute content.
        for (StreamSlot slot : slots.values()) {
            ModelItem item = slot.toItem();
            if (item != null && !completedItems.contains(item)) {
                builder.item(item);
            }
        }
        builder.usage(usage);
        builder.stopReason(stopReason);
        builder.responseId(responseId);
        builder.warnings(warnings);
        return builder.build();
    }

    /** Per-index accumulation of deltas. */
    private static final class StreamSlot {

        private final StringBuilder text = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final StringBuilder toolArgs = new StringBuilder();
        private StringBuilder signature;
        private ModelItem doneItem;
        private String itemType;
        private String itemId;

        void start(ModelEvent event) {
            itemType = event.getItemType();
            itemId = event.getItemId();
        }

        /** Record identity carried by deltas when no explicit ITEM_START arrived. */
        void observe(ModelEvent event) {
            if (itemId == null && event.getItemId() != null) {
                itemId = event.getItemId();
            }
            if (itemType == null && event.getItemType() != null) {
                itemType = event.getItemType();
            }
        }

        void append(EventDeltaKind kind, String delta) {
            if (kind == EventDeltaKind.TEXT) {
                text.append(delta);
            } else if (kind == EventDeltaKind.REASONING) {
                reasoning.append(delta);
            } else if (kind == EventDeltaKind.TOOL_ARGS) {
                toolArgs.append(delta);
            }
        }

        void signature(String delta) {
            if (signature == null) {
                signature = new StringBuilder();
            }
            signature.append(delta);
        }

        ModelItem finish(ModelEvent event) {
            if (event.getItem() != null) {
                doneItem = event.getItem();
                return doneItem;
            }
            return toItem();
        }

        ModelItem toItem() {
            if (doneItem != null) {
                return doneItem;
            }
            if (reasoning.length() > 0 || signature != null) {
                return io.github.lnyocly.ai4j.model.ReasoningItem.of(
                        reasoning.toString(),
                        signature == null ? null : signature.toString());
            }
            if (text.length() > 0) {
                return io.github.lnyocly.ai4j.model.Message.assistant()
                        .block(TextBlock.of(text.toString()))
                        .build();
            }
            if (toolArgs.length() > 0 && itemId != null) {
                return io.github.lnyocly.ai4j.model.ToolCallItem.of(
                        io.github.lnyocly.ai4j.model.ToolCallValue.of(
                                itemId, itemType == null ? "" : itemType, toolArgs.toString()));
            }
            return null;
        }
    }

}
