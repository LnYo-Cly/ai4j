package io.github.lnyocly.ai4j.harness.fleet;

import io.github.lnyocly.ai4j.harness.ContentPinnedConsentRecord;
import io.github.lnyocly.ai4j.harness.ContentPinnedConsentSpec;
import io.github.lnyocly.ai4j.harness.EscalationRecord;
import io.github.lnyocly.ai4j.harness.ExecutionRecord;
import io.github.lnyocly.ai4j.harness.HarnessActor;
import io.github.lnyocly.ai4j.harness.HarnessCommandGateway;
import io.github.lnyocly.ai4j.harness.HarnessEntityRecord;
import io.github.lnyocly.ai4j.harness.HarnessExecutionOutcome;
import io.github.lnyocly.ai4j.harness.HarnessSupport;
import io.github.lnyocly.ai4j.harness.SteerRecord;
import io.github.lnyocly.ai4j.harness.SupervisoryAgenda;

import java.io.IOException;
import java.util.List;

/**
 * Topology-aware multi-node Fleet facade unifying all 5 deployment modes:
 * {@link FleetTopology#LOCAL}, {@link FleetTopology#SHARED_CLUSTER},
 * {@link FleetTopology#FLEET_CENTER}, {@link FleetTopology#FLEET_EDGE}, and
 * {@link FleetTopology#FLEET_PROXY}.
 */
public class FleetGateway implements AutoCloseable {

    private final FleetTopology topology;
    private final String nodeId;
    private final HarnessCommandGateway localGateway;
    private final FleetCenterService centerService;
    private final FleetTransport transport;
    private final FleetEdgeMirror edgeMirror;
    private final AutoClaimPolicy policy;

    private String credentialToken;
    private HttpFleetServer httpServer;

    private FleetGateway(FleetTopology topology,
                         String nodeId,
                         HarnessCommandGateway localGateway,
                         FleetCenterService centerService,
                         FleetTransport transport,
                         AutoClaimPolicy policy) {
        this.topology = topology;
        this.nodeId = nodeId;
        this.localGateway = localGateway;
        this.centerService = centerService;
        this.transport = transport;
        this.policy = policy == null ? AutoClaimPolicy.builder().build() : policy;
        this.edgeMirror = (transport != null && (topology == FleetTopology.FLEET_EDGE
                || topology == FleetTopology.SHARED_CLUSTER))
                ? new FleetEdgeMirror(nodeId, transport) : null;
    }

    /** Mode 1: Single-node embedded local Harness with no remote Fleet coordinator. */
    public static FleetGateway local(HarnessCommandGateway gateway) {
        if (gateway == null) {
            throw new IllegalArgumentException("HarnessCommandGateway is required");
        }
        FleetCenterService center = new FleetCenterService(gateway);
        return new FleetGateway(FleetTopology.LOCAL, "local", gateway, center,
                new InMemoryFleetTransport(center), null);
    }

    /** Mode 2: Multi-node shared cluster sharing an underlying durable store / gateway. */
    public static FleetGateway sharedCluster(HarnessCommandGateway gateway,
                                             String nodeId,
                                             AutoClaimPolicy policy) {
        if (gateway == null) {
            throw new IllegalArgumentException("HarnessCommandGateway is required");
        }
        String normalizedNodeId = HarnessSupport.requireText(nodeId, "nodeId");
        FleetCenterService center = new FleetCenterService(gateway);
        InMemoryFleetTransport transport = new InMemoryFleetTransport(center);
        FleetGateway fleet = new FleetGateway(FleetTopology.SHARED_CLUSTER, normalizedNodeId,
                gateway, center, transport, policy);
        fleet.join(normalizedNodeId);
        return fleet;
    }

    /** Mode 3: Fleet center coordinator authority owning global state and HTTP/in-memory transport. */
    public static FleetGateway center(HarnessCommandGateway gateway) {
        if (gateway == null) {
            throw new IllegalArgumentException("HarnessCommandGateway is required");
        }
        FleetCenterService center = new FleetCenterService(gateway);
        return new FleetGateway(FleetTopology.FLEET_CENTER, "fleet-center", gateway, center,
                new InMemoryFleetTransport(center), null);
    }

    /** Mode 4: Fleet edge worker/mirror node connected via {@link FleetTransport}. */
    public static FleetGateway edge(String nodeId, FleetTransport transport, AutoClaimPolicy policy) {
        String normalizedNodeId = HarnessSupport.requireText(nodeId, "nodeId");
        if (transport == null) {
            throw new IllegalArgumentException("FleetTransport is required");
        }
        return new FleetGateway(FleetTopology.FLEET_EDGE, normalizedNodeId,
                null, null, transport, policy);
    }

    /** Mode 5: Lightweight human/agent proxy forwarding agenda/steer/consent/escalation to center. */
    public static FleetGateway proxy(String nodeId, FleetTransport transport) {
        String normalizedNodeId = HarnessSupport.requireText(nodeId, "nodeId");
        if (transport == null) {
            throw new IllegalArgumentException("FleetTransport is required");
        }
        AutoClaimPolicy proxyPolicy = AutoClaimPolicy.builder()
                .maxConcurrentExecutions(0)
                .batchSize(0)
                .build();
        FleetGateway gateway = new FleetGateway(FleetTopology.FLEET_PROXY, normalizedNodeId,
                null, null, transport, proxyPolicy);
        gateway.join(normalizedNodeId);
        return gateway;
    }

    public HttpFleetServer startHttpServer(String host, int port) throws IOException {
        if (centerService == null) {
            throw new IllegalStateException("HTTP server can only be hosted by LOCAL or FLEET_CENTER topology");
        }
        if (httpServer == null) {
            httpServer = new HttpFleetServer(centerService, host, port);
            httpServer.start();
        }
        return httpServer;
    }

    public EdgeJoinResponse join(String displayName) {
        EdgeJoinRequest request = EdgeJoinRequest.builder()
                .nodeId(nodeId)
                .topology(topology)
                .displayName(displayName)
                .maxConcurrentExecutions(policy.getMaxConcurrentExecutions())
                .tags(HarnessSupport.copyList(policy.getTags()))
                .allowedAgents(HarnessSupport.copyList(policy.getAllowedAgents()))
                .allowedScopeKeys(HarnessSupport.copyList(policy.getAllowedScopeKeys()))
                .build();
        EdgeJoinResponse response;
        if (edgeMirror != null) {
            response = edgeMirror.join(request);
        } else {
            response = transport.edgeJoin(request);
        }
        this.credentialToken = response.getCredentialToken();
        return response;
    }

    public EdgeSyncResponse sync(EdgeSyncConflictPolicy conflictPolicy) {
        if (edgeMirror == null) {
            throw new IllegalStateException("edgeSync requires FLEET_EDGE or SHARED_CLUSTER topology");
        }
        return edgeMirror.sync(conflictPolicy);
    }

    public void putLocalEntity(HarnessEntityRecord entity) {
        if (edgeMirror == null) {
            throw new IllegalStateException("local mirror requires FLEET_EDGE or SHARED_CLUSTER topology");
        }
        edgeMirror.putLocalEntity(entity);
    }

    public HarnessEntityRecord getLocalEntity(String entityType, String entityId) {
        if (edgeMirror == null) {
            throw new IllegalStateException("local mirror requires FLEET_EDGE or SHARED_CLUSTER topology");
        }
        return edgeMirror.getLocalEntity(entityType, entityId);
    }

    public AutoClaimWorker createAutoClaimWorker(String workerId) {
        if (topology == FleetTopology.FLEET_PROXY) {
            throw new IllegalStateException("FLEET_PROXY nodes do not claim or execute workloads");
        }
        ensureJoinedIfEdge();
        return new AutoClaimWorker(nodeId, workerId, credentialToken, policy, transport);
    }

    public ClaimPullResponse pullClaimableExecutions(String workerId, int maxCount, boolean claimImmediately) {
        ensureJoinedIfEdge();
        return transport.pullClaimableExecutions(ClaimPullRequest.builder()
                .nodeId(nodeId)
                .credentialToken(credentialToken)
                .workerId(workerId)
                .allowedAgents(HarnessSupport.copyList(policy.getAllowedAgents()))
                .scopeKeys(HarnessSupport.copyList(policy.getAllowedScopeKeys()))
                .tags(HarnessSupport.copyList(policy.getTags()))
                .maxCount(maxCount)
                .leaseDurationMillis(policy.getLeaseDurationMillis())
                .claimImmediately(claimImmediately)
                .build());
    }

    public ExecutionRecord claimExecution(String executionId, String workerId, long leaseDurationMillis) {
        ensureJoinedIfEdge();
        return transport.claimExecution(ExecutionClaimRequest.builder()
                .nodeId(nodeId)
                .credentialToken(credentialToken)
                .executionId(executionId)
                .workerId(workerId)
                .leaseDurationMillis(leaseDurationMillis)
                .build());
    }

    public ExecutionRecord persistOutcome(HarnessExecutionOutcome outcome) {
        ensureJoinedIfEdge();
        return transport.persistOutcome(ExecutionOutcomeRequest.builder()
                .nodeId(nodeId)
                .credentialToken(credentialToken)
                .outcome(outcome)
                .build());
    }

    public SupervisoryAgenda fetchAgenda(String actorId, String scopeKey) {
        ensureJoinedIfEdge();
        return transport.fetchAgenda(nodeId, credentialToken, actorId, scopeKey);
    }

    public SteerRecord steer(String executionId,
                             String expectedTaskId,
                             long expectedVersion,
                             String instructions,
                             HarnessActor actor) {
        ensureJoinedIfEdge();
        return transport.steer(nodeId, credentialToken, SteerRequest.builder()
                .executionId(executionId)
                .expectedTaskId(expectedTaskId)
                .expectedVersion(expectedVersion)
                .instructions(instructions)
                .actor(actor)
                .build());
    }

    public ContentPinnedConsentRecord reviewConsent(ContentPinnedConsentSpec spec, HarnessActor actor) {
        ensureJoinedIfEdge();
        return transport.reviewConsent(nodeId, credentialToken, ConsentRequest.builder()
                .spec(spec)
                .actor(actor)
                .build());
    }

    public EscalationRecord answerEscalation(String escalationId,
                                             String answer,
                                             String selectedOption,
                                             String rationale,
                                             HarnessActor actor) {
        ensureJoinedIfEdge();
        return transport.answerEscalation(nodeId, credentialToken, EscalationAnswerRequest.builder()
                .escalationId(escalationId)
                .answer(answer)
                .selectedOption(selectedOption)
                .rationale(rationale)
                .actor(actor)
                .build());
    }

    public UnregisterNodeResult unregisterNode(String targetNodeId,
                                               boolean force,
                                               String reason,
                                               HarnessActor actor) {
        return transport.unregisterNode(UnregisterNodeRequest.builder()
                .nodeId(targetNodeId)
                .force(force)
                .reason(reason)
                .actor(actor)
                .build());
    }

    public List<OrphanHandoffExport> listOrphanExports(String scopeKey) {
        return transport.listOrphanExports(scopeKey);
    }

    public ExecutionRecord takeoverOrphan(String exportId,
                                          String workerId,
                                          long leaseDurationMillis,
                                          HarnessActor actor) {
        ensureJoinedIfEdge();
        return transport.takeoverOrphan(TakeoverOrphanRequest.builder()
                .exportId(exportId)
                .targetNodeId(nodeId)
                .credentialToken(credentialToken)
                .workerId(workerId)
                .leaseDurationMillis(leaseDurationMillis)
                .actor(actor)
                .build());
    }

    public FleetTopology getTopology() {
        return topology;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getCredentialToken() {
        return credentialToken;
    }

    public HarnessCommandGateway getLocalGateway() {
        return localGateway;
    }

    public FleetCenterService getCenterService() {
        return centerService;
    }

    public FleetTransport getTransport() {
        return transport;
    }

    public FleetEdgeMirror getEdgeMirror() {
        return edgeMirror;
    }

    public AutoClaimPolicy getPolicy() {
        return policy;
    }

    private void ensureJoinedIfEdge() {
        if (credentialToken == null && transport != null && topology != FleetTopology.LOCAL) {
            join(nodeId);
        }
    }

    @Override
    public void close() {
        if (httpServer != null) {
            httpServer.close();
            httpServer = null;
        }
        if (transport != null) {
            transport.close();
        }
    }
}
