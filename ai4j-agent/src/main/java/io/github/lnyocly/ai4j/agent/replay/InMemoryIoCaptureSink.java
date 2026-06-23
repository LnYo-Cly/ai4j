package io.github.lnyocly.ai4j.agent.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory {@link IoCaptureSink}. Keeps the original input/output objects so
 * {@link NodeReplayer#replayModelLive} can re-invoke the model with the real captured prompt.
 */
public class InMemoryIoCaptureSink implements IoCaptureSink {

    private final List<NodeIoRecord> records = Collections.synchronizedList(new ArrayList<NodeIoRecord>());

    @Override
    public void capture(NodeIoRecord record) {
        if (record != null) {
            records.add(record);
        }
    }

    @Override
    public List<NodeIoRecord> records() {
        synchronized (records) {
            return new ArrayList<NodeIoRecord>(records);
        }
    }

    @Override
    public List<NodeIoRecord> records(NodeIoRecord.NodeType type) {
        List<NodeIoRecord> out = new ArrayList<NodeIoRecord>();
        synchronized (records) {
            for (NodeIoRecord r : records) {
                if (r.getNodeType() == type) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    @Override
    public NodeIoRecord find(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        synchronized (records) {
            for (NodeIoRecord r : records) {
                if (nodeId.equals(r.getNodeId())) {
                    return r;
                }
            }
        }
        return null;
    }

    @Override
    public int size() {
        synchronized (records) {
            return records.size();
        }
    }

    @Override
    public void close() {
        // no-op
    }
}
