package io.github.lnyocly.ai4j.harness.fleet;

import io.github.lnyocly.ai4j.harness.ContentPinnedConsentRecord;
import io.github.lnyocly.ai4j.harness.EscalationRecord;
import io.github.lnyocly.ai4j.harness.ExecutionRecord;
import io.github.lnyocly.ai4j.harness.ExecutionStatus;
import io.github.lnyocly.ai4j.harness.HandoffRecord;
import io.github.lnyocly.ai4j.harness.HarnessActor;
import io.github.lnyocly.ai4j.harness.HarnessCommandGateway;
import io.github.lnyocly.ai4j.harness.HarnessConflictException;
import io.github.lnyocly.ai4j.harness.HarnessEntityRecord;
import io.github.lnyocly.ai4j.harness.HarnessEventRecord;
import io.github.lnyocly.ai4j.harness.HarnessJson;
import io.github.lnyocly.ai4j.harness.HarnessState;
import io.github.lnyocly.ai4j.harness.HarnessSupport;
import io.github.lnyocly.ai4j.harness.HarnessValidationException;
import io.github.lnyocly.ai4j.harness.LeaseRecord;
import io.github.lnyocly.ai4j.harness.SessionLeaseRecord;
import io.github.lnyocly.ai4j.harness.SteerRecord;
import io.github.lnyocly.ai4j.harness.SupervisoryAgenda;
import io.github.lnyocly.ai4j.harness.TaskRecord;
import io.github.lnyocly.ai4j.harness.TaskStatus;
import io.github.lnyocly.ai4j.harness.WriterEpochFence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Center authority coordinating multi-node {@code FLEET_EDGE} / {@code FLEET_PROXY}
 * registration, 3-way {@code base/local/center} sync, node-affinity execution pull,
 * lease claim/outcome forwarding, split-brain {@link WriterEpochFence} bumping, and
 * orphan execution export/takeover on forced node unregistration.
 */
public class FleetCenterService implements FleetTransport {

    public static final String ERROR_NODE_UNREGISTERED = "NODE_UNREGISTERED";
    public static final String ERROR_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ERROR_EPOCH_FENCED = "EPOCH_FENCED";
    public static final String ERROR_SCOPE_DENIED = "SCOPE_DENIED";
    public static final String ERROR_NODE_HAS_ACTIVE_WORK = "NODE_HAS_ACTIVE_WORK";
    public static final String ERROR_NODE_AFFINITY_MISMATCH = "NODE_AFFINITY_MISMATCH";
    public static final String ERROR_NODE_CAPACITY_EXHAUSTED = "NODE_CAPACITY_EXHAUSTED";
    public static final String ERROR_ORPHAN_NOT_PENDING = "ORPHAN_NOT_PENDING";

    private final HarnessCommandGateway gateway;
    private final AtomicLong tokenSeq = new AtomicLong(0L);

    public FleetCenterService(HarnessCommandGateway gateway) {
        if (gateway == null) {
            throw new IllegalArgumentException("HarnessCommandGateway is required");
        }
        this.gateway = gateway;
    }

    public HarnessCommandGateway getGateway() {
        return gateway;
    }

    @Override
    public EdgeJoinResponse edgeJoin(final EdgeJoinRequest request) {
        if (request == null) {
            throw new HarnessValidationException("edge join request is required");
        }
        final String nodeId = HarnessSupport.requireText(request.getNodeId(), "nodeId");
        final FleetTopology topology = request.getTopology() == null
                ? FleetTopology.FLEET_EDGE : request.getTopology();
        final WriterEpochFence currentEpoch = gateway.currentWriterEpoch();
        final long epochValue = currentEpoch == null ? 0L : currentEpoch.getEpoch();
        final String issuedToken = HarnessSupport.trimToNull(request.getCredentialToken()) != null
                ? request.getCredentialToken().trim()
                : "flt_tok_" + nodeId + "_" + tokenSeq.incrementAndGet() + "_"
                + UUID.randomUUID().toString().substring(0, 8);
        final int maxConcurrent = request.getMaxConcurrentExecutions() > 0
                ? request.getMaxConcurrentExecutions() : 4;

        final long currentHeadSeq = latestEventSeq(0L);
        FleetNodeRecord registered = HarnessSupport.write(gateway.getStore(), new HarnessSupport.StateCommand<FleetNodeRecord>() {
            @Override
            public FleetNodeRecord apply(HarnessState state) {
                long now = HarnessSupport.now();
                FleetNodeRecord existing = state.getFleetNodes().get(nodeId);
                long nextVersion = existing == null ? 1L : Math.max(1L, existing.getVersion()) + 1L;
                long joinedAt = existing == null || existing.getJoinedAtEpochMs() <= 0L
                        ? now : existing.getJoinedAtEpochMs();
                int activeCount = existing == null ? 0 : Math.max(0, existing.getActiveExecutions());

                FleetNodeRecord node = FleetNodeRecord.builder()
                        .nodeId(nodeId)
                        .topology(topology)
                        .displayName(HarnessSupport.trimToNull(request.getDisplayName()) == null
                                ? nodeId : request.getDisplayName().trim())
                        .status(FleetNodeStatus.ACTIVE)
                        .credentialToken(issuedToken)
                        .writerEpochAtJoin(epochValue)
                        .lastSyncSeq(currentHeadSeq)
                        .maxConcurrentExecutions(maxConcurrent)
                        .activeExecutions(activeCount)
                        .joinedAtEpochMs(joinedAt)
                        .lastHeartbeatEpochMs(now)
                        .unregisteredAtEpochMs(0L)
                        .unregisterReason(null)
                        .version(nextVersion)
                        .tags(HarnessSupport.copyList(request.getTags()))
                        .allowedAgents(HarnessSupport.copyList(request.getAllowedAgents()))
                        .allowedScopeKeys(HarnessSupport.copyList(request.getAllowedScopeKeys()))
                        .build();
                state.getFleetNodes().put(nodeId, node);
                return node.copy();
            }
        });

        gateway.recordEvent("fleet.node_joined", nodeId,
                HarnessSupport.mapOf("topology", topology.name(),
                        "writerEpoch", epochValue,
                        "tags", registered.getTags()),
                HarnessActor.worker("fleet-node:" + nodeId));

        long headSeqAfterJoin = latestEventSeq(currentHeadSeq);
        List<HarnessEntityRecord> visibleEntities = filterEntitiesByScope(
                gateway.snapshotProjectedEntities(), registered);

        return EdgeJoinResponse.builder()
                .node(registered)
                .credentialToken(issuedToken)
                .writerEpoch(epochValue)
                .headEventSeq(headSeqAfterJoin)
                .centerEntities(visibleEntities)
                .build();
    }

    @Override
    public EdgeSyncResponse edgeSync(final EdgeSyncRequest request) {
        if (request == null) {
            throw new HarnessValidationException("edge sync request is required");
        }
        FleetNodeRecord node = authenticateActiveNode(
                request.getNodeId(), request.getCredentialToken(), Long.valueOf(request.getWriterEpoch()));

        Map<String, HarnessEntityRecord> baseMap = indexByKey(request.getBaseEntities());
        Map<String, HarnessEntityRecord> localMap = indexByKey(request.getLocalEntities());

        for (HarnessEntityRecord localEntity : localMap.values()) {
            String entityScope = extractEntityScope(localEntity);
            if (!node.supportsScope(entityScope)) {
                throw new HarnessValidationException(ERROR_SCOPE_DENIED,
                        "node " + node.getNodeId() + " cannot write entity in scope: " + entityScope);
            }
        }

        Map<String, HarnessEntityRecord> centerMap = indexByKey(gateway.snapshotProjectedEntities());
        Set<String> allKeys = new LinkedHashSet<String>();
        allKeys.addAll(centerMap.keySet());
        allKeys.addAll(baseMap.keySet());
        allKeys.addAll(localMap.keySet());

        List<EdgeSyncResolution> resolutions = new ArrayList<EdgeSyncResolution>();
        List<EdgeSyncConflict> conflicts = new ArrayList<EdgeSyncConflict>();
        List<HarnessEntityRecord> mergedEntities = new ArrayList<HarnessEntityRecord>();
        HarnessActor syncActor = HarnessActor.worker("fleet-node:" + node.getNodeId());

        for (String key : allKeys) {
            HarnessEntityRecord base = baseMap.get(key);
            HarnessEntityRecord local = localMap.get(key);
            HarnessEntityRecord center = centerMap.get(key);

            String sampleScope = pickScope(center, local, base);
            if (!node.supportsScope(sampleScope)) {
                continue;
            }

            ThreeWayEntityMerger.MergeOutcome outcome = ThreeWayEntityMerger.merge(
                    base, local, center, request.getConflictPolicy());

            if (!outcome.getConflicts().isEmpty()) {
                conflicts.addAll(outcome.getConflicts());
            }

            HarnessEntityRecord resulting = outcome.getMergedEntity();
            if (resulting != null
                    && (outcome.getAction() == EdgeSyncResolution.Action.PUSHED_TO_CENTER
                    || outcome.getAction() == EdgeSyncResolution.Action.THREE_WAY_MERGED)) {
                gateway.applySyncedEntity(resulting, syncActor);
            }

            String[] parts = splitKey(key);
            resolutions.add(EdgeSyncResolution.builder()
                    .entityType(parts[0])
                    .entityId(parts[1])
                    .action(outcome.getAction())
                    .resultingEntity(resulting == null ? null : resulting.copy())
                    .build());

            if (resulting != null) {
                mergedEntities.add(resulting.copy());
            }
        }

        List<HarnessEventRecord> eventsSinceBase = gateway.eventsSince(Math.max(0L, request.getBaseSyncSeq()), 500);
        final WriterEpochFence currentFence = gateway.currentWriterEpoch();
        final long currentEpoch = currentFence == null ? 0L : currentFence.getEpoch();
        final String nodeId = node.getNodeId();
        final long newBaseSeq = eventsSinceBase.isEmpty()
                ? latestEventSeq(request.getBaseSyncSeq())
                : eventsSinceBase.get(eventsSinceBase.size() - 1).getSeq();

        HarnessSupport.write(gateway.getStore(), new HarnessSupport.StateCommand<Void>() {
            @Override
            public Void apply(HarnessState state) {
                long now = HarnessSupport.now();
                FleetNodeRecord target = state.getFleetNodes().get(nodeId);
                if (target != null) {
                    target.setLastSyncSeq(newBaseSeq);
                    target.setWriterEpochAtJoin(Math.max(target.getWriterEpochAtJoin(), currentEpoch));
                    target.setLastHeartbeatEpochMs(now);
                    target.setVersion(Math.max(1L, target.getVersion()) + 1L);
                }
                return null;
            }
        });

        return EdgeSyncResponse.builder()
                .synced(conflicts.isEmpty())
                .hasConflicts(!conflicts.isEmpty())
                .newBaseSeq(newBaseSeq)
                .writerEpoch(currentEpoch)
                .resolutions(resolutions)
                .conflicts(conflicts)
                .mergedEntities(mergedEntities)
                .eventsSinceBase(eventsSinceBase)
                .build();
    }

    @Override
    public ClaimPullResponse pullClaimableExecutions(ClaimPullRequest request) {
        if (request == null) {
            throw new HarnessValidationException("claim pull request is required");
        }
        FleetNodeRecord node = authenticateActiveNode(request.getNodeId(), request.getCredentialToken(), null);
        int requestedMax = request.getMaxCount() > 0 ? request.getMaxCount() : 1;
        int remainingCapacity = Math.max(0, node.getMaxConcurrentExecutions() - node.getActiveExecutions());
        int limit = request.isClaimImmediately() ? Math.min(requestedMax, remainingCapacity) : requestedMax;

        List<ExecutionRecord> result = new ArrayList<ExecutionRecord>();
        if (limit > 0) {
            HarnessState state = gateway.getState();
            List<ExecutionRecord> candidates = new ArrayList<ExecutionRecord>(state.getExecutions().values());
            Collections.sort(candidates, new Comparator<ExecutionRecord>() {
                @Override
                public int compare(ExecutionRecord left, ExecutionRecord right) {
                    return Long.compare(left.getCreatedAtEpochMs(), right.getCreatedAtEpochMs());
                }
            });

            String workerId = HarnessSupport.trimToNull(request.getWorkerId()) != null
                    ? request.getWorkerId().trim()
                    : "fleet-worker:" + node.getNodeId();

            for (ExecutionRecord candidate : candidates) {
                if (result.size() >= limit) {
                    break;
                }
                if (candidate == null || candidate.getStatus() != ExecutionStatus.READY) {
                    continue;
                }
                if (!matchesScopeFilter(candidate.getScopeKey(), request.getScopeKeys(), node)) {
                    continue;
                }
                TaskRecord task = candidate.getTaskId() == null ? null : state.getTasks().get(candidate.getTaskId());
                if (task != null && !isClaimableTaskStatus(task.getStatus())) {
                    continue;
                }
                if (!matchesAgentFilter(task, request.getAgentId(), request.getAllowedAgents(), node)) {
                    continue;
                }
                NodeAffinity affinity = resolveAffinity(candidate, task);
                if (!affinity.matches(node)) {
                    continue;
                }
                if (request.isClaimImmediately()) {
                    try {
                        ExecutionRecord claimed = claimExecutionForNode(
                                node, candidate.getExecutionId(), workerId, request.getLeaseDurationMillis());
                        result.add(claimed);
                    } catch (HarnessConflictException ignored) {
                        // Concurrent claimant or task status changed; continue to next candidate
                    }
                } else {
                    result.add(candidate.copy());
                }
            }
        }

        WriterEpochFence fence = gateway.currentWriterEpoch();
        return ClaimPullResponse.builder()
                .writerEpoch(fence == null ? 0L : fence.getEpoch())
                .executions(result)
                .build();
    }

    @Override
    public ExecutionRecord claimExecution(ExecutionClaimRequest request) {
        if (request == null) {
            throw new HarnessValidationException("execution claim request is required");
        }
        FleetNodeRecord node = authenticateActiveNode(request.getNodeId(), request.getCredentialToken(), null);
        String executionId = HarnessSupport.requireText(request.getExecutionId(), "executionId");
        String workerId = HarnessSupport.trimToNull(request.getWorkerId()) != null
                ? request.getWorkerId().trim()
                : "fleet-worker:" + node.getNodeId();
        return claimExecutionForNode(node, executionId, workerId, request.getLeaseDurationMillis());
    }

    @Override
    public ExecutionRecord persistOutcome(ExecutionOutcomeRequest request) {
        if (request == null || request.getOutcome() == null) {
            throw new HarnessValidationException("execution outcome request is required");
        }
        final FleetNodeRecord node = authenticateActiveNode(request.getNodeId(), request.getCredentialToken(), null);
        ExecutionRecord updated = gateway.persistExecutionOutcome(request.getOutcome());
        recalculateActiveExecutions(node.getNodeId());
        return updated;
    }

    @Override
    public SupervisoryAgenda fetchAgenda(String nodeId, String credentialToken, String actorId, String scopeKey) {
        FleetNodeRecord node = authenticateActiveNode(nodeId, credentialToken, null);
        if (!node.supportsScope(scopeKey)) {
            throw new HarnessValidationException(ERROR_SCOPE_DENIED,
                    "node " + node.getNodeId() + " cannot read agenda for scope: " + scopeKey);
        }
        return gateway.projectAgenda(actorId, scopeKey);
    }

    @Override
    public SteerRecord steer(String nodeId, String credentialToken, SteerRequest request) {
        if (request == null) {
            throw new HarnessValidationException("steer request is required");
        }
        authenticateActiveNode(nodeId, credentialToken, null);
        return gateway.steer(
                request.getExecutionId(),
                request.getExpectedTaskId(),
                request.getExpectedVersion(),
                request.getInstructions(),
                request.getActor());
    }

    @Override
    public ContentPinnedConsentRecord reviewConsent(String nodeId, String credentialToken, ConsentRequest request) {
        if (request == null) {
            throw new HarnessValidationException("consent request is required");
        }
        authenticateActiveNode(nodeId, credentialToken, null);
        return gateway.reviewConsent(request.getSpec(), request.getActor());
    }

    @Override
    public EscalationRecord answerEscalation(String nodeId, String credentialToken, EscalationAnswerRequest request) {
        if (request == null) {
            throw new HarnessValidationException("escalation answer request is required");
        }
        authenticateActiveNode(nodeId, credentialToken, null);
        return gateway.answerEscalation(
                request.getEscalationId(),
                request.getAnswer(),
                request.getSelectedOption(),
                request.getRationale(),
                request.getActor());
    }

    @Override
    public UnregisterNodeResult unregisterNode(UnregisterNodeRequest request) {
        if (request == null) {
            throw new HarnessValidationException("unregister request is required");
        }
        final String nodeId = HarnessSupport.requireText(request.getNodeId(), "nodeId");
        final HarnessActor effectiveActor = HarnessSupport.normalizeActor(request.getActor(), gateway.getDefaultActor());
        final String reason = HarnessSupport.trimToNull(request.getReason()) == null
                ? "unregistered" : request.getReason().trim();

        HarnessState initialSnapshot = gateway.getState();
        FleetNodeRecord existingNode = initialSnapshot.getFleetNodes().get(nodeId);
        if (existingNode == null) {
            throw new HarnessValidationException("fleet node not found: " + nodeId);
        }

        List<ExecutionCandidate> orphanCandidates = collectNodeExecutions(initialSnapshot, nodeId);
        boolean hasActiveWork = !orphanCandidates.isEmpty() || existingNode.getActiveExecutions() > 0;
        if (hasActiveWork && !request.isForce()) {
            throw new HarnessConflictException(ERROR_NODE_HAS_ACTIVE_WORK,
                    "fleet node has active executions or leases (" + orphanCandidates.size()
                            + "); pass force=true to increment WriterEpoch and export orphans: " + nodeId);
        }

        WriterEpochFence prevFence = gateway.currentWriterEpoch();
        long prevEpoch = prevFence == null ? 0L : prevFence.getEpoch();
        WriterEpochFence newFence = gateway.claimWriterEpoch("fleet-unregister:" + nodeId, prevEpoch);
        final long newEpoch = newFence.getEpoch();

        final List<String> revokedLeaseIds = new ArrayList<String>();
        final List<OrphanHandoffExport> exports = new ArrayList<OrphanHandoffExport>();
        final Map<String, ExecutionCandidate> preExportDetails = new LinkedHashMap<String, ExecutionCandidate>();
        for (ExecutionCandidate candidate : orphanCandidates) {
            preExportDetails.put(candidate.executionId, candidate);
        }

        // First mark node unregistered and invalidate credentials so it cannot issue new claims
        FleetNodeRecord unregisteredNode = HarnessSupport.write(gateway.getStore(),
                new HarnessSupport.StateCommand<FleetNodeRecord>() {
                    @Override
                    public FleetNodeRecord apply(HarnessState state) {
                        long now = HarnessSupport.now();
                        FleetNodeRecord node = state.getFleetNodes().get(nodeId);
                        node.setStatus(FleetNodeStatus.UNREGISTERED);
                        node.setCredentialToken(null);
                        node.setUnregisteredAtEpochMs(now);
                        node.setUnregisterReason(reason);
                        node.setActiveExecutions(0);
                        node.setLastHeartbeatEpochMs(now);
                        node.setVersion(Math.max(1L, node.getVersion()) + 1L);
                        return node.copy();
                    }
                });

        // Export handoffs for each non-terminal execution owned by this node
        for (ExecutionCandidate candidate : orphanCandidates) {
            HandoffRecord handoff = gateway.exportHandoff(
                    candidate.executionId,
                    null,
                    candidate.targetModel,
                    "orphan-export:" + nodeId + ":" + reason,
                    0L,
                    effectiveActor);

            final String execId = candidate.executionId;
            final String handoffId = handoff.getHandoffId();
            final ExecutionCandidate meta = preExportDetails.get(execId);

            OrphanHandoffExport exportRecord = HarnessSupport.write(gateway.getStore(),
                    new HarnessSupport.StateCommand<OrphanHandoffExport>() {
                        @Override
                        public OrphanHandoffExport apply(HarnessState state) {
                            long now = HarnessSupport.now();
                            // Ensure all active leases matching execution or worker on this node are released
                            for (LeaseRecord lease : state.getLeases().values()) {
                                if (lease != null && lease.getReleasedAtEpochMs() <= 0L
                                        && execId.equals(lease.getExecutionId())) {
                                    lease.setReleasedAtEpochMs(now);
                                    if (!revokedLeaseIds.contains(lease.getLeaseId())) {
                                        revokedLeaseIds.add(lease.getLeaseId());
                                    }
                                }
                            }
                            if (meta.leaseId != null && !revokedLeaseIds.contains(meta.leaseId)) {
                                revokedLeaseIds.add(meta.leaseId);
                            }
                            for (SessionLeaseRecord sl : state.getSessionLeases().values()) {
                                if (sl != null && sl.getReleasedAtEpochMs() <= 0L
                                        && execId.equals(sl.getExecutionId())) {
                                    sl.setReleasedAtEpochMs(now);
                                }
                            }
                            ExecutionRecord exec = state.getExecutions().get(execId);
                            if (exec != null) {
                                // Clear dead node pinning if it explicitly targeted the unregistered node
                                if (nodeId.equals(exec.getTargetNodeId())) {
                                    exec.setTargetNodeId(null);
                                }
                                exec.setAssignedNodeId(null);
                            }

                            String exportId = HarnessSupport.valueOrGenerated(null, "orphan_");
                            OrphanHandoffExport export = OrphanHandoffExport.builder()
                                    .exportId(exportId)
                                    .scopeKey(meta.scopeKey)
                                    .executionId(execId)
                                    .taskId(meta.taskId)
                                    .handoffId(handoffId)
                                    .checkpointId(meta.checkpointId)
                                    .previousNodeId(nodeId)
                                    .previousWorkerId(meta.workerId)
                                    .previousLeaseId(meta.leaseId)
                                    .previousFencingToken(meta.fencingToken)
                                    .fencedWriterEpoch(newEpoch)
                                    .status(OrphanHandoffExport.Status.PENDING_TAKEOVER)
                                    .exportedAtEpochMs(now)
                                    .requiredTags(HarnessSupport.copyList(meta.requiredTags))
                                    .build();
                            state.getOrphanExports().put(exportId, export);
                            return export.copy();
                        }
                    });
            exports.add(exportRecord);

            gateway.recordEvent("fleet.orphan_exported", exportRecord.getExportId(),
                    HarnessSupport.mapOf("nodeId", nodeId,
                            "executionId", execId,
                            "handoffId", handoffId,
                            "fencedWriterEpoch", newEpoch),
                    effectiveActor);
        }

        // Also revoke any dangling leases on state belonging to nodeId workers without assigned executions
        HarnessSupport.write(gateway.getStore(), new HarnessSupport.StateCommand<Void>() {
            @Override
            public Void apply(HarnessState state) {
                long now = HarnessSupport.now();
                for (LeaseRecord lease : state.getLeases().values()) {
                    if (lease != null && lease.getReleasedAtEpochMs() <= 0L
                            && belongsToNodeWorker(lease.getWorkerId(), nodeId)) {
                        lease.setReleasedAtEpochMs(now);
                        if (!revokedLeaseIds.contains(lease.getLeaseId())) {
                            revokedLeaseIds.add(lease.getLeaseId());
                        }
                    }
                }
                return null;
            }
        });

        gateway.recordEvent("fleet.node_unregistered", nodeId,
                HarnessSupport.mapOf("force", request.isForce(),
                        "reason", reason,
                        "previousWriterEpoch", prevEpoch,
                        "newWriterEpoch", newEpoch,
                        "revokedLeaseIds", revokedLeaseIds,
                        "orphanCount", exports.size()),
                effectiveActor);

        return UnregisterNodeResult.builder()
                .node(unregisteredNode)
                .previousWriterEpoch(prevEpoch)
                .newWriterEpoch(newEpoch)
                .revokedLeaseIds(revokedLeaseIds)
                .orphanExports(exports)
                .build();
    }

    @Override
    public List<OrphanHandoffExport> listOrphanExports(String scopeKey) {
        HarnessState state = gateway.getState();
        List<OrphanHandoffExport> result = new ArrayList<OrphanHandoffExport>();
        for (OrphanHandoffExport export : state.getOrphanExports().values()) {
            if (export == null) {
                continue;
            }
            if (HarnessSupport.visibleScope(export.getScopeKey(), scopeKey)) {
                result.add(export.copy());
            }
        }
        Collections.sort(result, new Comparator<OrphanHandoffExport>() {
            @Override
            public int compare(OrphanHandoffExport left, OrphanHandoffExport right) {
                return Long.compare(left.getExportedAtEpochMs(), right.getExportedAtEpochMs());
            }
        });
        return result;
    }

    @Override
    public ExecutionRecord takeoverOrphan(TakeoverOrphanRequest request) {
        if (request == null) {
            throw new HarnessValidationException("takeover orphan request is required");
        }
        final String exportId = HarnessSupport.requireText(request.getExportId(), "exportId");
        final FleetNodeRecord targetNode = authenticateActiveNode(
                request.getTargetNodeId(), request.getCredentialToken(), null);

        HarnessState state = gateway.getState();
        OrphanHandoffExport export = state.getOrphanExports().get(exportId);
        if (export == null) {
            throw new HarnessValidationException("orphan export not found: " + exportId);
        }
        if (export.getStatus() != OrphanHandoffExport.Status.PENDING_TAKEOVER) {
            throw new HarnessConflictException(ERROR_ORPHAN_NOT_PENDING,
                    "orphan export is not PENDING_TAKEOVER: " + exportId + " (" + export.getStatus() + ")");
        }
        if (!targetNode.supportsScope(export.getScopeKey())) {
            throw new HarnessValidationException(ERROR_SCOPE_DENIED,
                    "node " + targetNode.getNodeId() + " cannot take over scope: " + export.getScopeKey());
        }
        if (!targetNode.supportsTags(export.getRequiredTags())) {
            throw new HarnessConflictException(ERROR_NODE_AFFINITY_MISMATCH,
                    "target node " + targetNode.getNodeId() + " lacks required tags: " + export.getRequiredTags());
        }
        if (targetNode.getActiveExecutions() >= targetNode.getMaxConcurrentExecutions()) {
            throw new HarnessConflictException(ERROR_NODE_CAPACITY_EXHAUSTED,
                    "target node at capacity: " + targetNode.getNodeId());
        }

        final String workerId = HarnessSupport.trimToNull(request.getWorkerId()) != null
                ? request.getWorkerId().trim()
                : "fleet-worker:" + targetNode.getNodeId();
        final HarnessActor actor = HarnessSupport.normalizeActor(
                request.getActor(), HarnessActor.worker(workerId));
        long leaseMillis = request.getLeaseDurationMillis() > 0L ? request.getLeaseDurationMillis() : 60_000L;

        if (export.getHandoffId() != null) {
            gateway.claimHandoff(export.getHandoffId(), targetNode.getNodeId(), workerId, leaseMillis, actor);
        } else {
            gateway.claimExecution(export.getExecutionId(), workerId, leaseMillis);
        }

        ExecutionRecord claimedExecution = HarnessSupport.write(gateway.getStore(),
                new HarnessSupport.StateCommand<ExecutionRecord>() {
                    @Override
                    public ExecutionRecord apply(HarnessState s) {
                        long now = HarnessSupport.now();
                        OrphanHandoffExport record = s.getOrphanExports().get(exportId);
                        if (record != null) {
                            record.setStatus(OrphanHandoffExport.Status.TAKEN_OVER);
                            record.setTakenOverByNodeId(targetNode.getNodeId());
                            record.setTakenOverByWorkerId(workerId);
                            record.setTakenOverAtEpochMs(now);
                        }
                        ExecutionRecord exe = s.getExecutions().get(export.getExecutionId());
                        if (exe != null) {
                            exe.setAssignedNodeId(targetNode.getNodeId());
                        }
                        recomputeNodeActiveCountInState(s, targetNode.getNodeId(), now);
                        return exe == null ? null : exe.copy();
                    }
                });

        gateway.recordEvent("fleet.orphan_taken_over", exportId,
                HarnessSupport.mapOf("executionId", export.getExecutionId(),
                        "targetNodeId", targetNode.getNodeId(),
                        "workerId", workerId),
                actor);

        return claimedExecution;
    }

    public List<FleetNodeRecord> listNodes() {
        HarnessState state = gateway.getState();
        List<FleetNodeRecord> nodes = new ArrayList<FleetNodeRecord>();
        for (FleetNodeRecord node : state.getFleetNodes().values()) {
            if (node != null) {
                nodes.add(node.copy());
            }
        }
        return nodes;
    }

    public FleetNodeRecord getNode(String nodeId) {
        FleetNodeRecord node = gateway.getState().getFleetNodes().get(HarnessSupport.trimToNull(nodeId));
        return node == null ? null : node.copy();
    }

    private ExecutionRecord claimExecutionForNode(final FleetNodeRecord node,
                                                  String executionId,
                                                  String workerId,
                                                  long leaseDurationMillis) {
        HarnessState state = gateway.getState();
        ExecutionRecord exec = HarnessSupport.requireExecution(state, executionId);
        if (!node.supportsScope(exec.getScopeKey())) {
            throw new HarnessValidationException(ERROR_SCOPE_DENIED,
                    "node " + node.getNodeId() + " cannot claim execution in scope: " + exec.getScopeKey());
        }
        TaskRecord task = exec.getTaskId() == null ? null : state.getTasks().get(exec.getTaskId());
        NodeAffinity affinity = resolveAffinity(exec, task);
        if (!affinity.matches(node)) {
            throw new HarnessConflictException(ERROR_NODE_AFFINITY_MISMATCH,
                    "node " + node.getNodeId() + " does not satisfy execution affinity ("
                            + affinity.getTargetNodeId() + ", tags=" + affinity.getRequiredTags() + ")");
        }
        if (node.getActiveExecutions() >= node.getMaxConcurrentExecutions()
                && !node.getNodeId().equals(exec.getAssignedNodeId())) {
            throw new HarnessConflictException(ERROR_NODE_CAPACITY_EXHAUSTED,
                    "node at max concurrent capacity: " + node.getNodeId());
        }

        long duration = leaseDurationMillis > 0L ? leaseDurationMillis : 60_000L;
        final ExecutionRecord claimed = gateway.claimExecution(executionId, workerId, duration);

        return HarnessSupport.write(gateway.getStore(), new HarnessSupport.StateCommand<ExecutionRecord>() {
            @Override
            public ExecutionRecord apply(HarnessState s) {
                long now = HarnessSupport.now();
                ExecutionRecord stored = s.getExecutions().get(claimed.getExecutionId());
                if (stored != null) {
                    stored.setAssignedNodeId(node.getNodeId());
                    stored.setUpdatedAtEpochMs(now);
                }
                recomputeNodeActiveCountInState(s, node.getNodeId(), now);
                return stored == null ? claimed : stored.copy();
            }
        });
    }

    private FleetNodeRecord authenticateActiveNode(String nodeId, String credentialToken, Long writerEpoch) {
        String key = HarnessSupport.requireText(nodeId, "nodeId");
        HarnessState state = gateway.getState();
        FleetNodeRecord node = state.getFleetNodes().get(key);
        if (node == null) {
            throw new HarnessConflictException(ERROR_NODE_UNREGISTERED,
                    "fleet node is not registered: " + key);
        }
        if (node.getStatus() != FleetNodeStatus.ACTIVE) {
            throw new HarnessConflictException(ERROR_NODE_UNREGISTERED,
                    "fleet node is fenced/unregistered (" + node.getStatus() + "): " + key);
        }
        if (node.getCredentialToken() == null || !node.getCredentialToken().equals(credentialToken)) {
            throw new HarnessValidationException(ERROR_UNAUTHORIZED,
                    "invalid fleet credential token for node: " + key);
        }
        if (writerEpoch != null && writerEpoch.longValue() < node.getWriterEpochAtJoin()) {
            throw new HarnessConflictException(ERROR_EPOCH_FENCED,
                    "stale writer epoch " + writerEpoch + " < node join epoch " + node.getWriterEpochAtJoin());
        }
        return node.copy();
    }

    private void recalculateActiveExecutions(final String nodeId) {
        HarnessSupport.write(gateway.getStore(), new HarnessSupport.StateCommand<Void>() {
            @Override
            public Void apply(HarnessState state) {
                recomputeNodeActiveCountInState(state, nodeId, HarnessSupport.now());
                return null;
            }
        });
    }

    private static void recomputeNodeActiveCountInState(HarnessState state, String nodeId, long now) {
        FleetNodeRecord node = state.getFleetNodes().get(nodeId);
        if (node == null) {
            return;
        }
        int active = 0;
        for (ExecutionRecord exec : state.getExecutions().values()) {
            if (exec != null && nodeId.equals(exec.getAssignedNodeId())
                    && exec.getStatus() == ExecutionStatus.RUNNING) {
                active++;
            }
        }
        node.setActiveExecutions(active);
        node.setLastHeartbeatEpochMs(now);
    }

    private List<ExecutionCandidate> collectNodeExecutions(HarnessState state, String nodeId) {
        List<ExecutionCandidate> candidates = new ArrayList<ExecutionCandidate>();
        for (ExecutionRecord exec : state.getExecutions().values()) {
            if (exec == null) {
                continue;
            }
            if (exec.getStatus() == ExecutionStatus.SUCCEEDED
                    || exec.getStatus() == ExecutionStatus.FAILED
                    || exec.getStatus() == ExecutionStatus.CANCELLED) {
                continue;
            }
            boolean assignedHere = nodeId.equals(exec.getAssignedNodeId())
                    || belongsToNodeWorker(exec.getWorkerId(), nodeId);
            if (!assignedHere) {
                continue;
            }
            if (exec.getStatus() == ExecutionStatus.RUNNING
                    || exec.getStatus() == ExecutionStatus.WAITING
                    || exec.getStatus() == ExecutionStatus.UNKNOWN) {
                TaskRecord task = exec.getTaskId() == null ? null : state.getTasks().get(exec.getTaskId());
                NodeAffinity affinity = resolveAffinity(exec, task);
                List<String> tags = new ArrayList<String>(affinity.getRequiredTags());
                candidates.add(new ExecutionCandidate(
                        exec.getExecutionId(),
                        exec.getScopeKey(),
                        exec.getTaskId(),
                        exec.getCheckpointId(),
                        exec.getWorkerId(),
                        exec.getLeaseId(),
                        exec.getFencingToken(),
                        exec.getTargetModel(),
                        tags));
            }
        }
        return candidates;
    }

    private static boolean belongsToNodeWorker(String workerId, String nodeId) {
        if (workerId == null || nodeId == null) {
            return false;
        }
        return workerId.equals(nodeId)
                || workerId.startsWith(nodeId + ":")
                || workerId.equals("fleet-worker:" + nodeId)
                || workerId.startsWith("fleet-worker:" + nodeId + ":");
    }

    private static NodeAffinity resolveAffinity(ExecutionRecord exec, TaskRecord task) {
        String targetNode = exec.getTargetNodeId();
        if (targetNode == null && task != null) {
            targetNode = task.getTargetNodeId();
        }
        List<String> tags = exec.getRequiredTags() != null && !exec.getRequiredTags().isEmpty()
                ? exec.getRequiredTags()
                : (task != null ? task.getRequiredTags() : Collections.<String>emptyList());
        return NodeAffinity.builder()
                .targetNodeId(targetNode)
                .requiredTags(tags == null ? new ArrayList<String>() : new ArrayList<String>(tags))
                .build();
    }

    private static boolean matchesScopeFilter(String executionScope,
                                              List<String> requestScopes,
                                              FleetNodeRecord node) {
        if (!node.supportsScope(executionScope)) {
            return false;
        }
        if (requestScopes == null || requestScopes.isEmpty()) {
            return true;
        }
        for (String allowed : requestScopes) {
            if (HarnessSupport.visibleScope(executionScope, allowed)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAgentFilter(TaskRecord task,
                                              String requestedAgentId,
                                              List<String> requestAllowedAgents,
                                              FleetNodeRecord node) {
        String taskAgent = task == null ? null : HarnessSupport.trimToNull(task.getAssignedAgentId());
        if (requestedAgentId != null && !requestedAgentId.trim().isEmpty()) {
            if (taskAgent != null && !requestedAgentId.trim().equals(taskAgent)) {
                return false;
            }
        }
        if (requestAllowedAgents != null && !requestAllowedAgents.isEmpty() && taskAgent != null) {
            if (!requestAllowedAgents.contains(taskAgent)) {
                return false;
            }
        }
        if (node.getAllowedAgents() != null && !node.getAllowedAgents().isEmpty() && taskAgent != null) {
            return node.getAllowedAgents().contains(taskAgent);
        }
        return true;
    }

    private static boolean isClaimableTaskStatus(TaskStatus status) {
        return status != TaskStatus.DONE
                && status != TaskStatus.CANCELLED
                && status != TaskStatus.BLOCKED
                && status != TaskStatus.IN_REVIEW;
    }

    private static List<HarnessEntityRecord> filterEntitiesByScope(List<HarnessEntityRecord> entities,
                                                                   FleetNodeRecord node) {
        List<HarnessEntityRecord> filtered = new ArrayList<HarnessEntityRecord>();
        for (HarnessEntityRecord entity : entities) {
            if (entity != null && node.supportsScope(extractEntityScope(entity))) {
                filtered.add(entity.copy());
            }
        }
        return filtered;
    }

    private static Map<String, HarnessEntityRecord> indexByKey(List<HarnessEntityRecord> entities) {
        Map<String, HarnessEntityRecord> map = new LinkedHashMap<String, HarnessEntityRecord>();
        if (entities == null) {
            return map;
        }
        for (HarnessEntityRecord entity : entities) {
            if (entity == null || entity.getEntityType() == null || entity.getEntityId() == null) {
                continue;
            }
            map.put(entity.getEntityType() + "::" + entity.getEntityId(), entity.copy());
        }
        return map;
    }

    private static String[] splitKey(String key) {
        int idx = key.indexOf("::");
        if (idx < 0) {
            return new String[]{key, ""};
        }
        return new String[]{key.substring(0, idx), key.substring(idx + 2)};
    }

    private static String pickScope(HarnessEntityRecord a, HarnessEntityRecord b, HarnessEntityRecord c) {
        String s = extractEntityScope(a);
        if (s != null) return s;
        s = extractEntityScope(b);
        return s != null ? s : extractEntityScope(c);
    }

    private static String extractEntityScope(HarnessEntityRecord record) {
        if (record == null || record.getPayloadJson() == null || record.getPayloadJson().trim().isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> parsed = HarnessJson.parseMap(record.getPayloadJson());
            Object scope = parsed == null ? null : parsed.get("scopeKey");
            return scope == null ? null : String.valueOf(scope);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private long latestEventSeq(long floorSeq) {
        long cursor = Math.max(0L, floorSeq);
        while (true) {
            List<HarnessEventRecord> batch = gateway.eventsSince(cursor, 500);
            if (batch == null || batch.isEmpty()) {
                return cursor;
            }
            cursor = batch.get(batch.size() - 1).getSeq();
            if (batch.size() < 500) {
                return cursor;
            }
        }
    }

    private static final class ExecutionCandidate {
        private final String executionId;
        private final String scopeKey;
        private final String taskId;
        private final String checkpointId;
        private final String workerId;
        private final String leaseId;
        private final long fencingToken;
        private final String targetModel;
        private final List<String> requiredTags;

        private ExecutionCandidate(String executionId,
                                   String scopeKey,
                                   String taskId,
                                   String checkpointId,
                                   String workerId,
                                   String leaseId,
                                   long fencingToken,
                                   String targetModel,
                                   List<String> requiredTags) {
            this.executionId = executionId;
            this.scopeKey = scopeKey;
            this.taskId = taskId;
            this.checkpointId = checkpointId;
            this.workerId = workerId;
            this.leaseId = leaseId;
            this.fencingToken = fencingToken;
            this.targetModel = targetModel;
            this.requiredTags = requiredTags;
        }
    }
}
