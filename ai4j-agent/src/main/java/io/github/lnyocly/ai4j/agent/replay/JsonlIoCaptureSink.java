package io.github.lnyocly.ai4j.agent.replay;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Durable {@link IoCaptureSink}: appends each {@link NodeIoRecord} as one JSON line. The resulting
 * file is the audit/replay artifact — append-only, grep-able, replayable.
 *
 * <p>Note: records loaded back from JSON have their {@code inputs}/{@code outputs} as parsed
 * JSON objects (not the original typed objects), so this sink is for durable storage / audit;
 * use {@link InMemoryIoCaptureSink} when live re-invocation of the captured objects is needed.</p>
 */
public class JsonlIoCaptureSink implements IoCaptureSink {

    private final Path path;
    private final BufferedWriter writer;
    private final Object lock = new Object();

    public JsonlIoCaptureSink(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        this.path = path;
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        this.writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public Path getPath() {
        return path;
    }

    @Override
    public void capture(NodeIoRecord record) {
        if (record == null) {
            return;
        }
        synchronized (lock) {
            try {
                writer.write(JSON.toJSONString(record));
                writer.write("\n");
                writer.flush();
            } catch (IOException e) {
                throw new RuntimeException("failed to write capture record to " + path, e);
            }
        }
    }

    @Override
    public List<NodeIoRecord> records() {
        return load(path);
    }

    @Override
    public List<NodeIoRecord> records(NodeIoRecord.NodeType type) {
        List<NodeIoRecord> out = new ArrayList<NodeIoRecord>();
        for (NodeIoRecord r : load(path)) {
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
        for (NodeIoRecord r : load(path)) {
            if (nodeId.equals(r.getNodeId())) {
                return r;
            }
        }
        return null;
    }

    @Override
    public int size() {
        return load(path).size();
    }

    @Override
    public void close() {
        synchronized (lock) {
            try {
                writer.close();
            } catch (IOException ignored) {
                // best-effort close
            }
        }
    }

    /** Loads all records from a JSONL file (one record per line). */
    public static List<NodeIoRecord> load(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return Collections.emptyList();
        }
        List<NodeIoRecord> out = new ArrayList<NodeIoRecord>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                try {
                    JSONObject obj = JSON.parseObject(line);
                    if (obj == null) {
                        continue;
                    }
                    // NodeIoRecord has no no-arg ctor, so rebuild via the Builder; inputs/outputs
                    // come back as parsed JSON (original typed objects don't round-trip from JSON).
                    NodeIoRecord.NodeType type = obj.getObject("nodeType", NodeIoRecord.NodeType.class);
                    NodeIoRecord.Builder b = NodeIoRecord.builder(type == null ? NodeIoRecord.NodeType.MODEL : type)
                            .recordId(obj.getString("recordId"))
                            .runId(obj.getString("runId"))
                            .sessionId(obj.getString("sessionId"))
                            .turnId(obj.getString("turnId"))
                            .step(obj.getInteger("step"))
                            .nodeId(obj.getString("nodeId"))
                            .modelId(obj.getString("modelId"))
                            .inputs(obj.get("inputs"))
                            .outputs(obj.get("outputs"));
                    Integer capturedAt = obj.getInteger("capturedAtEpochMs");
                    if (capturedAt != null) {
                        b.capturedAtEpochMs(capturedAt.longValue());
                    }
                    out.add(b.build());
                } catch (Exception ignored) {
                    // skip malformed line rather than failing the whole load
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to read capture file " + path, e);
        }
        return out;
    }
}
