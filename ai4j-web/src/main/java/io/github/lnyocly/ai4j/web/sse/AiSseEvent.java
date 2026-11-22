package io.github.lnyocly.ai4j.web.sse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable Server-Sent Events (SSE) frame supporting standard SSE fields,
 * multi-line {@code data:} serialization, and comment frames.
 */
public final class AiSseEvent {

    private final String id;
    private final String event;
    private final String data;
    private final Long retryMs;
    private final String comment;

    private AiSseEvent(Builder builder) {
        if (builder.id != null && containsLineBreakOrNul(builder.id)) {
            throw new IllegalArgumentException("SSE id must not contain CR, LF, or NUL");
        }
        if (builder.event != null && containsLineBreak(builder.event)) {
            throw new IllegalArgumentException("SSE event must not contain CR or LF");
        }
        if (builder.retryMs != null && builder.retryMs.longValue() < 0L) {
            throw new IllegalArgumentException("SSE retryMs must not be negative");
        }
        if (builder.id == null
                && builder.event == null
                && builder.data == null
                && builder.retryMs == null
                && builder.comment == null) {
            throw new IllegalArgumentException("SSE event must define at least one field");
        }
        this.id = builder.id;
        this.event = builder.event;
        this.data = builder.data;
        this.retryMs = builder.retryMs;
        this.comment = builder.comment;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AiSseEvent ofData(String data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        return builder().data(data).build();
    }

    public static AiSseEvent ofEvent(String event, String data) {
        if (event == null || event.trim().isEmpty()) {
            throw new IllegalArgumentException("event must not be blank");
        }
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        return builder().event(event).data(data).build();
    }

    public static AiSseEvent ofEvent(String id, String event, String data) {
        if (event == null || event.trim().isEmpty()) {
            throw new IllegalArgumentException("event must not be blank");
        }
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        return builder().id(id).event(event).data(data).build();
    }

    public static AiSseEvent commentOnly(String comment) {
        if (comment == null) {
            throw new IllegalArgumentException("comment must not be null");
        }
        return builder().comment(comment).build();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .event(this.event)
                .data(this.data)
                .retryMs(this.retryMs)
                .comment(this.comment);
    }

    public String getId() {
        return id;
    }

    public String getEvent() {
        return event;
    }

    public String getData() {
        return data;
    }

    public Long getRetryMs() {
        return retryMs;
    }

    public String getComment() {
        return comment;
    }

    /**
     * Renders this frame in standard UTF-8 SSE wire format terminated by a blank line.
     * Multi-line {@code data} and {@code comment} payloads are split safely across
     * separate prefixed lines so embedded newlines cannot inject synthetic SSE fields.
     */
    public String toWireFormat() {
        StringBuilder sb = new StringBuilder();
        if (comment != null) {
            for (String line : splitLines(comment)) {
                sb.append(':');
                if (!line.isEmpty()) {
                    if (!line.startsWith(" ")) {
                        sb.append(' ');
                    }
                    sb.append(line);
                }
                sb.append('\n');
            }
        }
        if (id != null) {
            sb.append("id: ").append(id).append('\n');
        }
        if (event != null) {
            sb.append("event: ").append(event).append('\n');
        }
        if (retryMs != null) {
            sb.append("retry: ").append(retryMs.longValue()).append('\n');
        }
        if (data != null) {
            for (String line : splitLines(data)) {
                sb.append("data: ").append(line).append('\n');
            }
        }
        sb.append('\n');
        return sb.toString();
    }

    /**
     * Splits text on CRLF, LF, or CR while preserving trailing empty lines.
     */
    public static List<String> splitLines(String text) {
        if (text == null) {
            return Collections.emptyList();
        }
        if (text.isEmpty()) {
            return Collections.singletonList("");
        }
        List<String> lines = new ArrayList<String>();
        int start = 0;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);
            if (ch == '\r' || ch == '\n') {
                lines.add(text.substring(start, i));
                if (ch == '\r' && i + 1 < len && text.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }
        lines.add(text.substring(start));
        return Collections.unmodifiableList(lines);
    }

    private static boolean containsLineBreak(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\r' || ch == '\n') {
                return true;
            }
        }
        return false;
    }

    private static boolean containsLineBreakOrNul(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\r' || ch == '\n' || ch == '\0') {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AiSseEvent)) {
            return false;
        }
        AiSseEvent that = (AiSseEvent) o;
        return Objects.equals(id, that.id)
                && Objects.equals(event, that.event)
                && Objects.equals(data, that.data)
                && Objects.equals(retryMs, that.retryMs)
                && Objects.equals(comment, that.comment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, event, data, retryMs, comment);
    }

    @Override
    public String toString() {
        return "AiSseEvent{"
                + "id='" + id + '\''
                + ", event='" + event + '\''
                + ", data='" + data + '\''
                + ", retryMs=" + retryMs
                + ", comment='" + comment + '\''
                + '}';
    }

    public static final class Builder {
        private String id;
        private String event;
        private String data;
        private Long retryMs;
        private String comment;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder event(String event) {
            this.event = event;
            return this;
        }

        public Builder data(String data) {
            this.data = data;
            return this;
        }

        public Builder retryMs(Long retryMs) {
            this.retryMs = retryMs;
            return this;
        }

        public Builder comment(String comment) {
            this.comment = comment;
            return this;
        }

        public AiSseEvent build() {
            return new AiSseEvent(this);
        }
    }
}
