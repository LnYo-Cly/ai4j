package io.github.lnyocly.ai4j.agent.replay;

import java.io.Closeable;
import java.util.List;

/**
 * Receives {@link NodeIoRecord}s from {@link IoCaptureAgentListener} and makes them queryable.
 * Implementations: {@link InMemoryIoCaptureSink} (tests / live replay) and
 * {@link JsonlIoCaptureSink} (durable audit/replay artifact).
 */
public interface IoCaptureSink extends Closeable {

    void capture(NodeIoRecord record);

    List<NodeIoRecord> records();

    List<NodeIoRecord> records(NodeIoRecord.NodeType type);

    /** Returns the first record whose nodeId matches, or null. */
    NodeIoRecord find(String nodeId);

    int size();
}
