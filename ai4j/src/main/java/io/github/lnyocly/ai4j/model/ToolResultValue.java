package io.github.lnyocly.ai4j.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The authoritative tool-result value object shared by {@link ToolResultItem}
 * and {@link ToolResultBlock} (design 4.5).
 */
public final class ToolResultValue {

    private final String callId;
    private final List<Block> content;
    private final boolean error;
    private final String errorKind;
    private final Map<String, Object> protocolData;

    private ToolResultValue(String callId, List<Block> content, boolean error, String errorKind,
                            Map<String, Object> protocolData) {
        this.callId = Objects.requireNonNull(callId, "callId is required");
        this.content = immutableCopy(content);
        this.error = error;
        this.errorKind = errorKind;
        this.protocolData = JsonValues.copyObject(protocolData);
    }

    public static ToolResultValue of(String callId, List<Block> content) {
        return new ToolResultValue(callId, content, false, null, null);
    }

    public static ToolResultValue of(String callId, List<Block> content, boolean error, String errorKind) {
        return new ToolResultValue(callId, content, error, errorKind, null);
    }

    public static ToolResultValue of(String callId, List<Block> content, boolean error, String errorKind,
                                     Map<String, Object> protocolData) {
        return new ToolResultValue(callId, content, error, errorKind, protocolData);
    }

    public static ToolResultValue text(String callId, String text) {
        return new ToolResultValue(callId,
                Collections.<Block>singletonList(TextBlock.of(text)), false, null, null);
    }

    public static ToolResultValue text(String callId, String text, boolean error, String errorKind) {
        return new ToolResultValue(callId,
                Collections.<Block>singletonList(TextBlock.of(text)), error, errorKind, null);
    }

    private static List<Block> immutableCopy(List<Block> content) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }
        List<Block> copy = new ArrayList<Block>(content.size());
        for (Block block : content) {
            copy.add(Objects.requireNonNull(block, "tool result content blocks must not be null"));
        }
        return Collections.unmodifiableList(copy);
    }

    public String getCallId() {
        return callId;
    }

    public List<Block> getContent() {
        return content;
    }

    public boolean isError() {
        return error;
    }

    /** Optional host-side error classification, e.g. {@code RESULT_UNKNOWN} (design 6.5). */
    public String getErrorKind() {
        return errorKind;
    }

    public Map<String, Object> getProtocolData() {
        return protocolData;
    }

    @Override
    public String toString() {
        return "ToolResultValue{callId=" + callId + ", error=" + error + ", blocks=" + content.size() + '}';
    }
}
