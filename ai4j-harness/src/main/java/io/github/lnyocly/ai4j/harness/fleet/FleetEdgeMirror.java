package io.github.lnyocly.ai4j.harness.fleet;

import io.github.lnyocly.ai4j.harness.HarnessEntityRecord;
import io.github.lnyocly.ai4j.harness.HarnessEventRecord;
import io.github.lnyocly.ai4j.harness.HarnessSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local edge mirror maintaining {@code base}, {@code local}, and event buffers
 * for offline/weak-network edge operation and 3-way {@link #sync} against a center.
 */
public class FleetEdgeMirror {

    private final String nodeId;
    private final FleetTransport transport;

    private String credentialToken;
    private long writerEpoch;
    private long baseSyncSeq;
    private final Map<String, HarnessEntityRecord> baseEntities = new LinkedHashMap<String, HarnessEntityRecord>();
    private final Map<String, HarnessEntityRecord> localEntities = new LinkedHashMap<String, HarnessEntityRecord>();
    private final List<HarnessEventRecord> localEvents = new ArrayList<HarnessEventRecord>();

    public FleetEdgeMirror(String nodeId, FleetTransport transport) {
        this.nodeId = HarnessSupport.requireText(nodeId, "nodeId");
        if (transport == null) {
            throw new IllegalArgumentException("FleetTransport is required");
        }
        this.transport = transport;
    }

    public EdgeJoinResponse join(EdgeJoinRequest request) {
        EdgeJoinRequest effective = request == null
                ? EdgeJoinRequest.builder().nodeId(nodeId).build()
                : request.toBuilder().nodeId(nodeId).build();
        EdgeJoinResponse response = transport.edgeJoin(effective);
        this.credentialToken = response.getCredentialToken();
        this.writerEpoch = response.getWriterEpoch();
        this.baseSyncSeq = response.getHeadEventSeq();
        seedEntities(response.getCenterEntities());
        return response;
    }

    public void putLocalEntity(HarnessEntityRecord entity) {
        if (entity == null || entity.getEntityType() == null || entity.getEntityId() == null) {
            throw new IllegalArgumentException("valid HarnessEntityRecord is required");
        }
        localEntities.put(entityKey(entity.getEntityType(), entity.getEntityId()), entity.copy());
    }

    public HarnessEntityRecord getLocalEntity(String entityType, String entityId) {
        HarnessEntityRecord record = localEntities.get(entityKey(entityType, entityId));
        return record == null ? null : record.copy();
    }

    public HarnessEntityRecord getBaseEntity(String entityType, String entityId) {
        HarnessEntityRecord record = baseEntities.get(entityKey(entityType, entityId));
        return record == null ? null : record.copy();
    }

    public void recordLocalEvent(HarnessEventRecord event) {
        if (event != null) {
            localEvents.add(event.copy());
        }
    }

    public EdgeSyncResponse sync(EdgeSyncConflictPolicy policy) {
        EdgeSyncRequest request = EdgeSyncRequest.builder()
                .nodeId(nodeId)
                .credentialToken(credentialToken)
                .writerEpoch(writerEpoch)
                .baseSyncSeq(baseSyncSeq)
                .conflictPolicy(policy == null ? EdgeSyncConflictPolicy.DETECT_AND_REPORT : policy)
                .baseEntities(snapshotValues(baseEntities))
                .localEntities(snapshotValues(localEntities))
                .localEvents(new ArrayList<HarnessEventRecord>(localEvents))
                .build();
        EdgeSyncResponse response = transport.edgeSync(request);
        this.writerEpoch = response.getWriterEpoch();
        this.baseSyncSeq = response.getNewBaseSeq();
        if (response.isSynced()) {
            seedEntities(response.getMergedEntities());
            localEvents.clear();
        } else if (response.getResolutions() != null) {
            // Advance non-conflicting entries while leaving conflicted local entities inspectable
            for (EdgeSyncResolution res : response.getResolutions()) {
                if (res != null && res.getAction() != EdgeSyncResolution.Action.CONFLICT
                        && res.getResultingEntity() != null) {
                    String key = entityKey(res.getEntityType(), res.getEntityId());
                    baseEntities.put(key, res.getResultingEntity().copy());
                    localEntities.put(key, res.getResultingEntity().copy());
                }
            }
        }
        return response;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getCredentialToken() {
        return credentialToken;
    }

    public long getWriterEpoch() {
        return writerEpoch;
    }

    public long getBaseSyncSeq() {
        return baseSyncSeq;
    }

    private void seedEntities(List<HarnessEntityRecord> entities) {
        baseEntities.clear();
        localEntities.clear();
        if (entities == null) {
            return;
        }
        for (HarnessEntityRecord entity : entities) {
            if (entity != null && entity.getEntityType() != null && entity.getEntityId() != null) {
                String key = entityKey(entity.getEntityType(), entity.getEntityId());
                baseEntities.put(key, entity.copy());
                localEntities.put(key, entity.copy());
            }
        }
    }

    private static List<HarnessEntityRecord> snapshotValues(Map<String, HarnessEntityRecord> map) {
        List<HarnessEntityRecord> copy = new ArrayList<HarnessEntityRecord>(map.size());
        for (HarnessEntityRecord record : map.values()) {
            if (record != null) {
                copy.add(record.copy());
            }
        }
        return copy;
    }

    private static String entityKey(String entityType, String entityId) {
        return entityType + "::" + entityId;
    }
}
