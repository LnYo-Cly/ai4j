package io.github.lnyocly.ai4j.harness.fleet;

import io.github.lnyocly.ai4j.harness.ContentPinnedConsentRecord;
import io.github.lnyocly.ai4j.harness.EscalationRecord;
import io.github.lnyocly.ai4j.harness.ExecutionRecord;
import io.github.lnyocly.ai4j.harness.SteerRecord;
import io.github.lnyocly.ai4j.harness.SupervisoryAgenda;

import java.util.List;

/**
 * Transport abstraction connecting {@code FLEET_EDGE} / {@code FLEET_PROXY}
 * nodes to a {@code FLEET_CENTER} authority. Implemented by both
 * {@link InMemoryFleetTransport} and {@link HttpFleetTransport}.
 */
public interface FleetTransport extends AutoCloseable {

    EdgeJoinResponse edgeJoin(EdgeJoinRequest request);

    EdgeSyncResponse edgeSync(EdgeSyncRequest request);

    ClaimPullResponse pullClaimableExecutions(ClaimPullRequest request);

    ExecutionRecord claimExecution(ExecutionClaimRequest request);

    ExecutionRecord persistOutcome(ExecutionOutcomeRequest request);

    SupervisoryAgenda fetchAgenda(String nodeId, String credentialToken, String actorId, String scopeKey);

    SteerRecord steer(String nodeId, String credentialToken, SteerRequest request);

    ContentPinnedConsentRecord reviewConsent(String nodeId, String credentialToken, ConsentRequest request);

    EscalationRecord answerEscalation(String nodeId, String credentialToken, EscalationAnswerRequest request);

    UnregisterNodeResult unregisterNode(UnregisterNodeRequest request);

    List<OrphanHandoffExport> listOrphanExports(String scopeKey);

    ExecutionRecord takeoverOrphan(TakeoverOrphanRequest request);

    @Override
    default void close() {
    }
}
