package io.github.lnyocly.ai4j.ambient.heartbeat;

import io.github.lnyocly.ai4j.ambient.support.AmbientSupport;
import io.github.lnyocly.ai4j.service.ISystemOneService;
import io.github.lnyocly.ai4j.systemone.entity.ChoiceQuestion;
import io.github.lnyocly.ai4j.systemone.entity.NoulCriteria;
import io.github.lnyocly.ai4j.systemone.entity.NoulQuestion;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneAnswer;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneRequest;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tier-1 lightweight heartbeat sentinel backed by {@link ISystemOneService}
 * ({@code TypeSafeSystemOneService} / {@code ScriptedSystemOneService}).
 *
 * <p>Filters empty/no-op ticks at zero model cost and gates remaining ticks with
 * structured Choice + Noul evaluation so 99% of routine polls terminate quietly
 * without invoking the expensive Tier-2 main reasoning model.</p>
 */
public class SystemOneSentinel {

    public enum Decision {
        IGNORE,
        DIGEST,
        WAKE_MAIN_AGENT,
        ESCALATE_OWNER
    }

    public static final String QUESTION_TRIAGE = "triage";
    public static final String QUESTION_URGENT = "urgent";

    private final ISystemOneService systemOneService;
    private final String model;
    private final double minConfidence;
    private final double criticalUrgencyThreshold;

    private final AtomicLong totalTicks = new AtomicLong();
    private final AtomicLong zeroCostFilteredCount = new AtomicLong();
    private final AtomicLong systemOneEvaluatedCount = new AtomicLong();
    private final AtomicLong ignoredCount = new AtomicLong();
    private final AtomicLong digestCount = new AtomicLong();
    private final AtomicLong wakeupCount = new AtomicLong();
    private final AtomicLong escalateCount = new AtomicLong();

    public SystemOneSentinel(ISystemOneService systemOneService) {
        this(systemOneService, "jev-latest", 0.6d, 0.85d);
    }

    public SystemOneSentinel(ISystemOneService systemOneService,
                             String model,
                             double minConfidence,
                             double criticalUrgencyThreshold) {
        if (systemOneService == null) {
            throw new IllegalArgumentException("systemOneService must not be null");
        }
        this.systemOneService = systemOneService;
        this.model = AmbientSupport.trimToNull(model) == null ? "jev-latest" : model.trim();
        this.minConfidence = minConfidence;
        this.criticalUrgencyThreshold = criticalUrgencyThreshold;
    }

    public SentinelAssessment evaluate(HeartbeatTick tick) {
        if (tick == null) {
            throw new IllegalArgumentException("heartbeat tick must not be null");
        }
        totalTicks.incrementAndGet();

        if (tick.isIdleNoOp()) {
            zeroCostFilteredCount.incrementAndGet();
            ignoredCount.incrementAndGet();
            return SentinelAssessment.builder()
                    .tickId(tick.getTickId())
                    .decision(Decision.IGNORE)
                    .priority(SupervisoryAgenda.Priority.LOW)
                    .confidence(1.0d)
                    .urgencyProbability(0.0d)
                    .usedSystemOne(false)
                    .reason("zero-delta idle tick filtered at zero model cost")
                    .build();
        }

        systemOneEvaluatedCount.incrementAndGet();
        SystemOneRequest request = buildRequest(tick);
        SystemOneResponse response;
        try {
            response = systemOneService.evaluate(request);
        } catch (Exception e) {
            digestCount.incrementAndGet();
            return SentinelAssessment.builder()
                    .tickId(tick.getTickId())
                    .decision(tick.isUrgentHint() ? Decision.WAKE_MAIN_AGENT : Decision.DIGEST)
                    .priority(tick.isUrgentHint()
                            ? SupervisoryAgenda.Priority.HIGH
                            : SupervisoryAgenda.Priority.NORMAL)
                    .confidence(0.0d)
                    .urgencyProbability(tick.isUrgentHint() ? 1.0d : 0.5d)
                    .usedSystemOne(true)
                    .reason("system-one evaluation error fallback: " + e.getMessage())
                    .build();
        }

        Decision decision = Decision.DIGEST;
        double confidence = 0.0d;
        double urgency = tick.isUrgentHint() ? 0.7d : 0.2d;
        StringBuilder reason = new StringBuilder();

        if (response != null && response.getAnswers() != null) {
            SystemOneAnswer triage = response.getAnswers().get(QUESTION_TRIAGE);
            if (triage != null && triage.getChoice() != null) {
                Decision parsed = parseDecision(triage.getChoice());
                confidence = triage.getConfidence() == null ? 1.0d : triage.getConfidence().doubleValue();
                if (confidence >= minConfidence) {
                    decision = parsed;
                    reason.append("system-one choice=").append(parsed.name())
                            .append(" conf=").append(confidence);
                } else {
                    decision = tick.isUrgentHint() ? Decision.DIGEST : Decision.IGNORE;
                    reason.append("low confidence ").append(confidence)
                            .append(" < ").append(minConfidence);
                }
            }
            SystemOneAnswer urgentAns = response.getAnswers().get(QUESTION_URGENT);
            if (urgentAns != null && urgentAns.getNoul() != null) {
                urgency = urgentAns.getNoul().doubleValue();
                if (urgency >= criticalUrgencyThreshold && decision == Decision.DIGEST) {
                    decision = Decision.WAKE_MAIN_AGENT;
                    reason.append("; urgency ").append(urgency).append(" promoted to WAKE_MAIN_AGENT");
                }
            }
        } else {
            decision = tick.isUrgentHint() ? Decision.DIGEST : Decision.IGNORE;
            reason.append("empty system-one response");
        }

        SupervisoryAgenda.Priority priority = resolvePriority(decision, urgency, tick.isUrgentHint());
        recordDecisionMetric(decision);

        return SentinelAssessment.builder()
                .tickId(tick.getTickId())
                .decision(decision)
                .priority(priority)
                .confidence(confidence)
                .urgencyProbability(urgency)
                .usedSystemOne(true)
                .reason(reason.length() == 0 ? "evaluated" : reason.toString())
                .build();
    }

    private SystemOneRequest buildRequest(HeartbeatTick tick) {
        Map<String, Object> state = new LinkedHashMap<String, Object>();
        state.put("tickId", tick.getTickId());
        state.put("scopeKey", tick.getScopeKey());
        state.put("source", tick.getSource());
        state.put("cursorBefore", tick.getCursorBefore());
        state.put("cursorAfter", tick.getCursorAfter());
        state.put("deltaCount", Integer.valueOf(tick.getDeltaCount()));
        state.put("urgentHint", Boolean.valueOf(tick.isUrgentHint()));
        state.put("summary", tick.getSummary());
        state.put("trustLevel", tick.getTrustLevel() == null ? null : tick.getTrustLevel().name());
        if (tick.getPayload() != null && !tick.getPayload().isEmpty()) {
            state.put("payload", tick.getPayload());
        }

        Map<String, Object> criteria = new LinkedHashMap<String, Object>();
        criteria.put(Decision.IGNORE.name(), "Routine noise or benign event requiring no action");
        criteria.put(Decision.DIGEST.name(), "Informational event suitable for batched digest");
        criteria.put(Decision.WAKE_MAIN_AGENT.name(), "Actionable anomaly or task requiring main agent execution");
        criteria.put(Decision.ESCALATE_OWNER.name(), "Critical blocker or high-risk choice needing owner ruling");

        Map<String, io.github.lnyocly.ai4j.systemone.entity.SystemOneQuestion> questions =
                new LinkedHashMap<String, io.github.lnyocly.ai4j.systemone.entity.SystemOneQuestion>();
        questions.put(QUESTION_TRIAGE, ChoiceQuestion.of(
                "Classify the heartbeat poll tick action.", criteria));
        questions.put(QUESTION_URGENT, NoulQuestion.of(
                "Does this tick require urgent attention or break SLA/build/security invariants?",
                new NoulCriteria("Urgent incident or time-sensitive decision", "Routine background poll")));

        return SystemOneRequest.of(state, model, questions);
    }

    private void recordDecisionMetric(Decision decision) {
        switch (decision) {
            case IGNORE:
                ignoredCount.incrementAndGet();
                break;
            case DIGEST:
                digestCount.incrementAndGet();
                break;
            case WAKE_MAIN_AGENT:
                wakeupCount.incrementAndGet();
                break;
            case ESCALATE_OWNER:
                escalateCount.incrementAndGet();
                break;
            default:
                break;
        }
    }

    private SupervisoryAgenda.Priority resolvePriority(Decision decision, double urgency, boolean urgentHint) {
        if (decision == Decision.ESCALATE_OWNER || urgency >= criticalUrgencyThreshold) {
            return SupervisoryAgenda.Priority.CRITICAL;
        }
        if (decision == Decision.WAKE_MAIN_AGENT || urgentHint) {
            return SupervisoryAgenda.Priority.HIGH;
        }
        if (decision == Decision.DIGEST) {
            return SupervisoryAgenda.Priority.NORMAL;
        }
        return SupervisoryAgenda.Priority.LOW;
    }

    private static Decision parseDecision(String raw) {
        if (raw == null) {
            return Decision.DIGEST;
        }
        try {
            return Decision.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Decision.DIGEST;
        }
    }

    public SentinelMetrics getMetrics() {
        long total = totalTicks.get();
        long filtered = ignoredCount.get();
        double rate = total == 0L ? 0.0d : (double) filtered / (double) total;
        return SentinelMetrics.builder()
                .totalTicks(total)
                .zeroCostFilteredCount(zeroCostFilteredCount.get())
                .systemOneEvaluatedCount(systemOneEvaluatedCount.get())
                .ignoredCount(filtered)
                .digestCount(digestCount.get())
                .wakeupCount(wakeupCount.get())
                .escalateCount(escalateCount.get())
                .filterRatio(rate)
                .build();
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentinelAssessment {
        private String tickId;
        private Decision decision;
        private SupervisoryAgenda.Priority priority;
        private double confidence;
        private double urgencyProbability;
        private boolean usedSystemOne;
        private String reason;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentinelMetrics {
        private long totalTicks;
        private long zeroCostFilteredCount;
        private long systemOneEvaluatedCount;
        private long ignoredCount;
        private long digestCount;
        private long wakeupCount;
        private long escalateCount;
        private double filterRatio;
    }
}
