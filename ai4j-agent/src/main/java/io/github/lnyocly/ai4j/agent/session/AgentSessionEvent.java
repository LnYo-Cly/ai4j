package io.github.lnyocly.ai4j.agent.session;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;

/**
 * One recorded event inside an agent session event log.
 */
public class AgentSessionEvent {

    private long sequence;
    private long recordedAtEpochMs;
    private AgentEvent event;

    public AgentSessionEvent() {
    }

    public AgentSessionEvent(long sequence, long recordedAtEpochMs, AgentEvent event) {
        this.sequence = sequence;
        this.recordedAtEpochMs = recordedAtEpochMs;
        this.event = event;
    }

    public AgentSessionEvent copy() {
        return new AgentSessionEvent(sequence, recordedAtEpochMs, copyEvent(event));
    }

    private static AgentEvent copyEvent(AgentEvent source) {
        if (source == null) {
            return null;
        }
        return AgentEvent.builder()
                .eventId(source.getEventId())
                .runId(source.getRunId())
                .sessionId(source.getSessionId())
                .turnId(source.getTurnId())
                .type(source.getType())
                .step(source.getStep())
                .message(source.getMessage())
                .payload(source.getPayload())
                .build();
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public long getRecordedAtEpochMs() {
        return recordedAtEpochMs;
    }

    public void setRecordedAtEpochMs(long recordedAtEpochMs) {
        this.recordedAtEpochMs = recordedAtEpochMs;
    }

    public AgentEvent getEvent() {
        return event;
    }

    public void setEvent(AgentEvent event) {
        this.event = event;
    }
}
