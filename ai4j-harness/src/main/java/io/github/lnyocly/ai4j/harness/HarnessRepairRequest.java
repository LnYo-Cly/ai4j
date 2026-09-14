package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.AgentRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Host-owned proposal for one repair continuation. The input is transient and
 * is passed to the adapter; only its bounded summary is persisted with the
 * child execution so secrets and large payloads do not enter the ledger.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessRepairRequest {

    /** Optional stable audit id supplied by the host. */
    private String requestId;
    private String parentExecutionId;
    /** Optional acceptance that motivated this repair. */
    private String acceptanceId;
    private String reason;
    private String repairHint;
    /** Explicit host decision; structured requests must set this field. */
    private HarnessRepairDecision decision;
    /** Optional idempotency key for retrying the same approved proposal. */
    private String idempotencyKey;
    private Object input;
    private AgentRequest agentRequest;
    private HarnessRunBudget budget;
}
