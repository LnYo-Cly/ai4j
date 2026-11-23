package io.github.lnyocly.ai4j.harness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Execution context passed to {@link ScheduleHandler}. In {@link ScheduleMode#DETECT}
 * mode, mutations are restricted strictly to recording facts and emitting alerts.
 */
public final class ScheduleContext {

    private final HarnessCommandGateway gateway;
    private final ScheduleRecord schedule;
    private final String tickKey;
    private final HarnessActor actor;
    private final List<String> recordedFactIds = new ArrayList<String>();
    private final List<String> alerts = new ArrayList<String>();
    private String remediationTaskId;
    private String remediationExecutionId;

    public ScheduleContext(HarnessCommandGateway gateway,
                           ScheduleRecord schedule,
                           String tickKey,
                           HarnessActor actor) {
        this.gateway = gateway;
        this.schedule = schedule;
        this.tickKey = tickKey;
        this.actor = actor;
    }

    public ScheduleRecord getSchedule() {
        return schedule == null ? null : schedule.copy();
    }

    public String getTickKey() {
        return tickKey;
    }

    public ScheduleMode getMode() {
        return schedule == null ? null : schedule.getMode();
    }

    public FactRecord recordFact(HarnessFactSpec spec) {
        if (spec == null) {
            throw new HarnessValidationException("fact specification is required");
        }
        HarnessFactSpec effective = spec.getScopeKey() == null && schedule != null
                ? spec.toBuilder().scopeKey(schedule.getScopeKey()).build()
                : spec;
        FactRecord fact = gateway.recordFact(effective, actor);
        recordedFactIds.add(fact.getFactId());
        return fact;
    }

    public void emitAlert(String message) {
        String normalized = HarnessSupport.requireText(message, "alert message");
        alerts.add(normalized);
        gateway.recordEvent("schedule.alert", schedule == null ? null : schedule.getScheduleId(),
                HarnessSupport.mapOf("tickKey", tickKey, "message", normalized), actor);
    }

    public TaskRecord createRemediationTask(HarnessTaskSpec spec) {
        assertRemediateMode();
        HarnessTaskSpec effective = spec == null ? HarnessTaskSpec.builder().build() : spec;
        if (effective.getScopeKey() == null && schedule != null) {
            effective = effective.toBuilder().scopeKey(schedule.getScopeKey()).build();
        }
        TaskRecord task = gateway.createTask(effective, actor);
        remediationTaskId = task.getTaskId();
        return task;
    }

    public ExecutionRecord startRemediationExecution(HarnessExecutionSpec spec) {
        assertRemediateMode();
        HarnessExecutionSpec effective = spec == null ? HarnessExecutionSpec.builder().build() : spec;
        if (effective.getScopeKey() == null && schedule != null) {
            effective = effective.toBuilder().scopeKey(schedule.getScopeKey()).build();
        }
        ExecutionRecord execution = gateway.createExecution(effective, actor);
        remediationExecutionId = execution.getExecutionId();
        return execution;
    }

    private void assertRemediateMode() {
        if (schedule == null || schedule.getMode() != ScheduleMode.REMEDIATE) {
            throw new HarnessValidationException(
                    "DETECT schedule is read-only and cannot create or run remediation tasks");
        }
    }

    public List<String> getRecordedFactIds() {
        return Collections.unmodifiableList(new ArrayList<String>(recordedFactIds));
    }

    public List<String> getAlerts() {
        return Collections.unmodifiableList(new ArrayList<String>(alerts));
    }

    public String getRemediationTaskId() {
        return remediationTaskId;
    }

    public String getRemediationExecutionId() {
        return remediationExecutionId;
    }
}
