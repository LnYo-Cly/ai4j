package io.github.lnyocly.ai4j.harness.fleet;

import io.github.lnyocly.ai4j.harness.ContentPinnedConsentRecord;
import io.github.lnyocly.ai4j.harness.EscalationRecord;
import io.github.lnyocly.ai4j.harness.ExecutionRecord;
import io.github.lnyocly.ai4j.harness.SteerRecord;
import io.github.lnyocly.ai4j.harness.SupervisoryAgenda;

import java.util.List;

/**
 * In-process {@link FleetTransport} backed directly by a {@link FleetCenterService}.
 */
public class InMemoryFleetTransport implements FleetTransport {

    private final FleetCenterService centerService;

    public InMemoryFleetTransport(FleetCenterService centerService) {
        if (centerService == null) {
            throw new IllegalArgumentException("FleetCenterService is required");
        }
        this.centerService = centerService;
    }

    public FleetCenterService getCenterService() {
        return centerService;
    }

    @Override
    public EdgeJoinResponse edgeJoin(EdgeJoinRequest request) {
        return centerService.edgeJoin(request);
    }

    @Override
    public EdgeSyncResponse edgeSync(EdgeSyncRequest request) {
        return centerService.edgeSync(request);
    }

    @Override
    public ClaimPullResponse pullClaimableExecutions(ClaimPullRequest request) {
        return centerService.pullClaimableExecutions(request);
    }

    @Override
    public ExecutionRecord claimExecution(ExecutionClaimRequest request) {
        return centerService.claimExecution(request);
    }

    @Override
    public ExecutionRecord persistOutcome(ExecutionOutcomeRequest request) {
        return centerService.persistOutcome(request);
    }

    @Override
    public SupervisoryAgenda fetchAgenda(String nodeId, String credentialToken, String actorId, String scopeKey) {
        return centerService.fetchAgenda(nodeId, credentialToken, actorId, scopeKey);
    }

    @Override
    public SteerRecord steer(String nodeId, String credentialToken, SteerRequest request) {
        return centerService.steer(nodeId, credentialToken, request);
    }

    @Override
    public ContentPinnedConsentRecord reviewConsent(String nodeId, String credentialToken, ConsentRequest request) {
        return centerService.reviewConsent(nodeId, credentialToken, request);
    }

    @Override
    public EscalationRecord answerEscalation(String nodeId, String credentialToken, EscalationAnswerRequest request) {
        return centerService.answerEscalation(nodeId, credentialToken, request);
    }

    @Override
    public UnregisterNodeResult unregisterNode(UnregisterNodeRequest request) {
        return centerService.unregisterNode(request);
    }

    @Override
    public List<OrphanHandoffExport> listOrphanExports(String scopeKey) {
        return centerService.listOrphanExports(scopeKey);
    }

    @Override
    public ExecutionRecord takeoverOrphan(TakeoverOrphanRequest request) {
        return centerService.takeoverOrphan(request);
    }
}
