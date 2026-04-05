package io.github.lnyocly.ai4j.agent.team;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class AgentTeamOptions {

    @Builder.Default
    private boolean parallelDispatch = true;

    @Builder.Default
    private int maxConcurrency = 4;

    @Builder.Default
    private boolean continueOnMemberError = true;

    @Builder.Default
    private boolean broadcastOnPlannerFailure = true;

    @Builder.Default
    private boolean failOnUnknownMember = false;

    @Builder.Default
    private boolean includeOriginalObjectiveInDispatch = true;

    @Builder.Default
    private boolean includeTaskContextInDispatch = true;

    @Builder.Default
    private boolean includeMessageHistoryInDispatch = true;

    @Builder.Default
    private int messageHistoryLimit = 20;

    @Builder.Default
    private boolean enableMessageBus = true;

    @Builder.Default
    private boolean allowDynamicMemberRegistration = true;

    @Builder.Default
    private boolean requirePlanApproval = false;

    @Builder.Default
    private int maxRounds = 64;

    @Builder.Default
    private long taskClaimTimeoutMillis = 0L;

    @Builder.Default
    private boolean enableMemberTeamTools = true;
}
