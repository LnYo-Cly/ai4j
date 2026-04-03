package io.github.lnyocly.ai4j.cli.runtime;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class AgentHandoffSessionEventSupportTest {

    @Test
    public void shouldMapHandoffEventsToTaskSessionEvents() {
        Map<String, Object> startPayload = new LinkedHashMap<String, Object>();
        startPayload.put("handoffId", "handoff:call-1");
        startPayload.put("tool", "subagent_review");
        startPayload.put("subagent", "review");
        startPayload.put("title", "Subagent review");
        startPayload.put("detail", "Delegating to subagent review.");
        startPayload.put("status", "starting");
        startPayload.put("sessionMode", "new_session");

        AgentEvent startEvent = AgentEvent.builder()
                .type(AgentEventType.HANDOFF_START)
                .step(0)
                .message("Subagent review")
                .payload(startPayload)
                .build();

        Assert.assertTrue(AgentHandoffSessionEventSupport.supports(startEvent));
        Assert.assertEquals(SessionEventType.TASK_CREATED, AgentHandoffSessionEventSupport.resolveSessionEventType(startEvent));
        Assert.assertEquals("Subagent review [starting]", AgentHandoffSessionEventSupport.buildSummary(startEvent));

        Map<String, Object> createdPayload = AgentHandoffSessionEventSupport.buildPayload(startEvent);
        Assert.assertEquals("handoff:call-1", createdPayload.get("taskId"));
        Assert.assertEquals("handoff:call-1", createdPayload.get("callId"));
        Assert.assertEquals("Subagent review", createdPayload.get("title"));
        Assert.assertEquals("starting", createdPayload.get("status"));
        Assert.assertEquals("new_session", createdPayload.get("sessionMode"));

        Map<String, Object> endPayload = new LinkedHashMap<String, Object>();
        endPayload.put("handoffId", "handoff:call-1");
        endPayload.put("tool", "subagent_review");
        endPayload.put("subagent", "review");
        endPayload.put("title", "Subagent review");
        endPayload.put("detail", "Subagent completed.");
        endPayload.put("status", "completed");
        endPayload.put("output", "review line 1\nreview line 2");
        endPayload.put("attempts", Integer.valueOf(1));
        endPayload.put("durationMillis", Long.valueOf(42L));

        AgentEvent endEvent = AgentEvent.builder()
                .type(AgentEventType.HANDOFF_END)
                .step(0)
                .message("Subagent review")
                .payload(endPayload)
                .build();

        Assert.assertEquals(SessionEventType.TASK_UPDATED, AgentHandoffSessionEventSupport.resolveSessionEventType(endEvent));
        Map<String, Object> updatedPayload = AgentHandoffSessionEventSupport.buildPayload(endEvent);
        Assert.assertEquals("completed", updatedPayload.get("status"));
        Assert.assertEquals("review line 1\nreview line 2", updatedPayload.get("output"));
        Assert.assertEquals(Arrays.asList("review line 1", "review line 2"), updatedPayload.get("previewLines"));
    }
}
