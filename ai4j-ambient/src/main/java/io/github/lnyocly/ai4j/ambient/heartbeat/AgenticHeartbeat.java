package io.github.lnyocly.ai4j.ambient.heartbeat;

import io.github.lnyocly.ai4j.ambient.support.AmbientSupport;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.harness.AgentHarness;
import io.github.lnyocly.ai4j.harness.HarnessRunRequest;
import io.github.lnyocly.ai4j.harness.HarnessRunResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Two-tier ambient heartbeat loop:
 * <ol>
 *   <li>Tier 1: {@link SystemOneSentinel} filters idle and benign ticks at zero/minimal cost.</li>
 *   <li>Tier 2: Escalating ticks wake the main reasoning agent / Harness execution and
 *       route supervisory rulings or quiet-hours notifications into {@link SupervisoryAgenda}.</li>
 * </ol>
 */
public class AgenticHeartbeat {

    public enum OutcomeType {
        SILENT_NOOP,
        QUEUED_DIGEST,
        MAIN_AGENT_EXECUTED,
        ESCALATED_IMMEDIATE,
        DEFERRED_QUIET_HOURS
    }

    private final SystemOneSentinel sentinel;
    private final MainAgentWakeupHandler wakeupHandler;
    private final SupervisoryAgenda agenda;
    private final QuietHoursPolicy quietHoursPolicy;
    private final Clock clock;

    public AgenticHeartbeat(SystemOneSentinel sentinel,
                            MainAgentWakeupHandler wakeupHandler,
                            SupervisoryAgenda agenda,
                            QuietHoursPolicy quietHoursPolicy) {
        this(sentinel, wakeupHandler, agenda, quietHoursPolicy, Clock.systemUTC());
    }

    public AgenticHeartbeat(SystemOneSentinel sentinel,
                            MainAgentWakeupHandler wakeupHandler,
                            SupervisoryAgenda agenda,
                            QuietHoursPolicy quietHoursPolicy,
                            Clock clock) {
        if (sentinel == null) {
            throw new IllegalArgumentException("sentinel must not be null");
        }
        if (agenda == null) {
            throw new IllegalArgumentException("supervisory agenda must not be null");
        }
        this.sentinel = sentinel;
        this.wakeupHandler = wakeupHandler;
        this.agenda = agenda;
        this.quietHoursPolicy = quietHoursPolicy == null ? QuietHoursPolicy.disabled() : quietHoursPolicy;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public static MainAgentWakeupHandler fromAgent(final Agent agent) {
        if (agent == null) {
            throw new IllegalArgumentException("agent must not be null");
        }
        return new MainAgentWakeupHandler() {
            @Override
            public WakeupOutcome wake(HeartbeatTick tick, SystemOneSentinel.SentinelAssessment assessment) throws Exception {
                String prompt = "Ambient wakeup [" + tick.getSource() + "]: "
                        + (tick.getSummary() != null ? tick.getSummary() : tick.getTickId());
                AgentResult result = agent.newSession().run(AgentRequest.builder().input(prompt).build());
                return WakeupOutcome.builder()
                        .executionId(result.getRunId())
                        .outputText(result.getOutputText())
                        .requiresOwnerRuling(assessment.getDecision() == SystemOneSentinel.Decision.ESCALATE_OWNER)
                        .rulingTitle("Ambient wakeup follow-up: " + tick.getSource())
                        .rulingSummary(result.getOutputText())
                        .build();
            }
        };
    }

    public static MainAgentWakeupHandler fromHarness(final AgentHarness harness) {
        if (harness == null) {
            throw new IllegalArgumentException("harness must not be null");
        }
        return new MainAgentWakeupHandler() {
            @Override
            public WakeupOutcome wake(HeartbeatTick tick, SystemOneSentinel.SentinelAssessment assessment) {
                String prompt = "Ambient wakeup [" + tick.getSource() + "]: "
                        + (tick.getSummary() != null ? tick.getSummary() : tick.getTickId());
                HarnessRunResult result = harness.run(HarnessRunRequest.builder()
                        .scopeKey(tick.getScopeKey())
                        .input(prompt)
                        .build());
                return WakeupOutcome.builder()
                        .taskId(result.getTask() == null ? null : result.getTask().getTaskId())
                        .executionId(result.getExecution() == null ? null : result.getExecution().getExecutionId())
                        .waitId(result.getWaitId())
                        .outputText(result.getOutputText())
                        .requiresOwnerRuling(result.getWaitId() != null
                                || assessment.getDecision() == SystemOneSentinel.Decision.ESCALATE_OWNER)
                        .rulingTitle("Ambient harness escalation: " + tick.getSource())
                        .rulingSummary(result.getOutputText() != null ? result.getOutputText() : tick.getSummary())
                        .build();
            }
        };
    }

    public HeartbeatResult tick(HeartbeatTick tick) {
        if (tick == null) {
            throw new IllegalArgumentException("heartbeat tick must not be null");
        }
        Instant now = clock.instant();
        boolean inQuietHours = quietHoursPolicy.isQuietAt(now);
        SystemOneSentinel.SentinelAssessment assessment = sentinel.evaluate(tick);

        if (assessment.getDecision() == SystemOneSentinel.Decision.IGNORE) {
            return HeartbeatResult.builder()
                    .tickId(tick.getTickId())
                    .outcomeType(OutcomeType.SILENT_NOOP)
                    .assessment(assessment)
                    .mainAgentInvoked(false)
                    .inQuietHours(inQuietHours)
                    .build();
        }

        if (assessment.getDecision() == SystemOneSentinel.Decision.DIGEST) {
            SupervisoryAgenda.AgendaItem queued = agenda.route(
                    SupervisoryAgenda.AgendaItem.builder()
                            .scopeKey(tick.getScopeKey())
                            .source(tick.getSource())
                            .title("Digest: " + safeLabel(tick.getSource(), tick.getTickId()))
                            .summary(safeLabel(tick.getSummary(), assessment.getReason()))
                            .priority(assessment.getPriority())
                            .informationalOnly(true)
                            .build(),
                    quietHoursPolicy,
                    now
            );
            return HeartbeatResult.builder()
                    .tickId(tick.getTickId())
                    .outcomeType(OutcomeType.QUEUED_DIGEST)
                    .assessment(assessment)
                    .agendaItem(queued)
                    .mainAgentInvoked(false)
                    .inQuietHours(inQuietHours)
                    .build();
        }

        boolean isCritical = assessment.getPriority() == SupervisoryAgenda.Priority.CRITICAL;
        if (inQuietHours && quietHoursPolicy.isDeferNonCriticalMainWakeup()
                && !(isCritical && quietHoursPolicy.isBypassForCritical())) {
            SupervisoryAgenda.AgendaItem deferred = agenda.route(
                    SupervisoryAgenda.AgendaItem.builder()
                            .scopeKey(tick.getScopeKey())
                            .source(tick.getSource())
                            .title("Deferred wakeup: " + safeLabel(tick.getSource(), tick.getTickId()))
                            .summary(safeLabel(tick.getSummary(), assessment.getReason()))
                            .priority(assessment.getPriority())
                            .informationalOnly(false)
                            .build(),
                    quietHoursPolicy,
                    now
            );
            return HeartbeatResult.builder()
                    .tickId(tick.getTickId())
                    .outcomeType(OutcomeType.DEFERRED_QUIET_HOURS)
                    .assessment(assessment)
                    .agendaItem(deferred)
                    .mainAgentInvoked(false)
                    .inQuietHours(true)
                    .build();
        }

        WakeupOutcome wakeupOutcome = null;
        if (wakeupHandler != null) {
            try {
                wakeupOutcome = wakeupHandler.wake(tick, assessment);
            } catch (Exception e) {
                SupervisoryAgenda.AgendaItem errorEscalation = agenda.route(
                        SupervisoryAgenda.AgendaItem.builder()
                                .scopeKey(tick.getScopeKey())
                                .source(tick.getSource())
                                .title("Wakeup failure: " + safeLabel(tick.getSource(), tick.getTickId()))
                                .summary(e.getMessage())
                                .priority(SupervisoryAgenda.Priority.CRITICAL)
                                .informationalOnly(false)
                                .build(),
                        quietHoursPolicy,
                        now
                );
                return HeartbeatResult.builder()
                        .tickId(tick.getTickId())
                        .outcomeType(OutcomeType.ESCALATED_IMMEDIATE)
                        .assessment(assessment)
                        .agendaItem(errorEscalation)
                        .mainAgentInvoked(true)
                        .inQuietHours(inQuietHours)
                        .error(e.getMessage())
                        .build();
            }
        }

        boolean needsRuling = assessment.getDecision() == SystemOneSentinel.Decision.ESCALATE_OWNER
                || (wakeupOutcome != null && wakeupOutcome.isRequiresOwnerRuling());

        if (needsRuling) {
            SupervisoryAgenda.AgendaItem item = agenda.route(
                    SupervisoryAgenda.AgendaItem.builder()
                            .scopeKey(tick.getScopeKey())
                            .source(tick.getSource())
                            .taskId(wakeupOutcome == null ? null : wakeupOutcome.getTaskId())
                            .executionId(wakeupOutcome == null ? null : wakeupOutcome.getExecutionId())
                            .waitId(wakeupOutcome == null ? null : wakeupOutcome.getWaitId())
                            .title(wakeupOutcome != null && wakeupOutcome.getRulingTitle() != null
                                    ? wakeupOutcome.getRulingTitle()
                                    : "Owner ruling required: " + safeLabel(tick.getSource(), tick.getTickId()))
                            .summary(wakeupOutcome != null && wakeupOutcome.getRulingSummary() != null
                                    ? wakeupOutcome.getRulingSummary()
                                    : safeLabel(tick.getSummary(), assessment.getReason()))
                            .priority(assessment.getPriority())
                            .options(wakeupOutcome == null
                                    ? Collections.<String>emptyList()
                                    : wakeupOutcome.getOptions())
                            .recommendedOption(wakeupOutcome == null ? null : wakeupOutcome.getRecommendedOption())
                            .informationalOnly(false)
                            .build(),
                    quietHoursPolicy,
                    now
            );
            OutcomeType type = item.getStatus() == SupervisoryAgenda.Status.DIGEST_PENDING
                    ? OutcomeType.DEFERRED_QUIET_HOURS
                    : OutcomeType.ESCALATED_IMMEDIATE;
            return HeartbeatResult.builder()
                    .tickId(tick.getTickId())
                    .outcomeType(type)
                    .assessment(assessment)
                    .wakeupOutcome(wakeupOutcome)
                    .agendaItem(item)
                    .mainAgentInvoked(true)
                    .inQuietHours(inQuietHours)
                    .build();
        }

        return HeartbeatResult.builder()
                .tickId(tick.getTickId())
                .outcomeType(OutcomeType.MAIN_AGENT_EXECUTED)
                .assessment(assessment)
                .wakeupOutcome(wakeupOutcome)
                .mainAgentInvoked(true)
                .inQuietHours(inQuietHours)
                .build();
    }

    public List<HeartbeatResult> tickAll(List<HeartbeatTick> ticks) {
        if (ticks == null || ticks.isEmpty()) {
            return Collections.emptyList();
        }
        List<HeartbeatResult> results = new ArrayList<HeartbeatResult>(ticks.size());
        for (HeartbeatTick tick : ticks) {
            results.add(tick(tick));
        }
        return results;
    }

    public SystemOneSentinel getSentinel() {
        return sentinel;
    }

    public SupervisoryAgenda getAgenda() {
        return agenda;
    }

    public QuietHoursPolicy getQuietHoursPolicy() {
        return quietHoursPolicy;
    }

    private static String safeLabel(String preferred, String fallback) {
        String p = AmbientSupport.trimToNull(preferred);
        return p != null ? p : AmbientSupport.emptyToEmpty(fallback);
    }

    @FunctionalInterface
    public interface MainAgentWakeupHandler {
        WakeupOutcome wake(HeartbeatTick tick, SystemOneSentinel.SentinelAssessment assessment) throws Exception;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WakeupOutcome {
        private String taskId;
        private String executionId;
        private String waitId;
        private String outputText;
        private boolean requiresOwnerRuling;
        private String rulingTitle;
        private String rulingSummary;
        @Builder.Default
        private List<String> options = new ArrayList<String>();
        private String recommendedOption;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeartbeatResult {
        private String tickId;
        private OutcomeType outcomeType;
        private SystemOneSentinel.SentinelAssessment assessment;
        private WakeupOutcome wakeupOutcome;
        private SupervisoryAgenda.AgendaItem agendaItem;
        private boolean mainAgentInvoked;
        private boolean inQuietHours;
        private String error;
    }
}
