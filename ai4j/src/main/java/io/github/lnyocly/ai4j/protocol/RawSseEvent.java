package io.github.lnyocly.ai4j.protocol;

import java.util.Objects;

/**
 * One raw SSE event as delivered by the transport.
 *
 * <p>Envelope fields ({@code id}/{@code type}/{@code retry}) are preserved so a
 * codec can retain them as raw data (design O3).</p>
 */
public final class RawSseEvent {

    private final String id;
    private final String type;
    private final String data;
    private final Long retryMillis;

    private RawSseEvent(String id, String type, String data, Long retryMillis) {
        this.id = id;
        this.type = type;
        this.data = Objects.requireNonNull(data, "data is required");
        this.retryMillis = retryMillis;
    }

    public static RawSseEvent of(String data) {
        return new RawSseEvent(null, null, data, null);
    }

    public static RawSseEvent of(String id, String type, String data) {
        return new RawSseEvent(id, type, data, null);
    }

    public static RawSseEvent of(String id, String type, String data, Long retryMillis) {
        return new RawSseEvent(id, type, data, retryMillis);
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getData() {
        return data;
    }

    public Long getRetryMillis() {
        return retryMillis;
    }

    @Override
    public String toString() {
        return "RawSseEvent{type=" + type + ", dataLength=" + data.length() + '}';
    }
}
