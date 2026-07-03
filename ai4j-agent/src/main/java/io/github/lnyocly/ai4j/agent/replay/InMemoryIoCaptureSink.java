package io.github.lnyocly.ai4j.agent.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
            List<NodeIoRecord> sorted = new ArrayList<NodeIoRecord>(records);
            // ponytail: causal order by node start time, not capture-flush order.
            // TOOL_RESULT flushes before STEP_END, so otherwise a MODEL node that decided the
            // tool call would appear *after* the tool in the list (wrong causality).
            sorted.sort(Comparator.comparingLong(NodeIoRecord::getStartedAtEpochMs));
            return sorted;
        }
    }

    @Override
    public List<NodeIoRecord> records(NodeIoRecord.NodeType type) {
        List<NodeIoRecord> out = new ArrayList<NodeIoRecord>();
        for (NodeIoRecord r : records()) {
            if (r.getNodeType() == type) {
                out.add(r);
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
