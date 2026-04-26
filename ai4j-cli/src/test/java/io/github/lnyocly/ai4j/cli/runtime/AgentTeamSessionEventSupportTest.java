package io.github.lnyocly.ai4j.cli.runtime;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class AgentTeamSessionEventSupportTest {

    @Test
    public void shouldMapTeamTaskEventsToTaskSessionEvents() {
        Map<String, Object> createdPayload = new LinkedHashMap<String, Object>();
        createdPayload.put("taskId", "team-task:collect");
        createdPayload.put("callId", "team-task:collect");
        createdPayload.put("title", "Team task collect");
        createdPayload.put("status", "planned");
        createdPayload.put("memberId", "researcher");
        createdPayload.put("memberName", "Researcher");
        createdPayload.put("task", "Collect requirements");
        createdPayload.put("dependsOn", Arrays.asList("seed"));
        createdPayload.put("detail", "Task planned.");
        createdPayload.put("phase", "planned");
        createdPayload.put("percent", Integer.valueOf(0));

        AgentEvent createdEvent = AgentEvent.builder()
                .type(AgentEventType.TEAM_TASK_CREATED)
                .step(1)
                .message("Team task collect")
                .payload(createdPayload)
                .build();

        Assert.assertTrue(AgentTeamSessionEventSupport.supports(createdEvent));
        Assert.assertEquals(SessionEventType.TASK_CREATED, AgentTeamSessionEventSupport.resolveSessionEventType(createdEvent));
        Assert.assertEquals("Team task collect [planned]", AgentTeamSessionEventSupport.buildSummary(createdEvent));

        Map<String, Object> createdSessionPayload = AgentTeamSessionEventSupport.buildPayload(createdEvent);
        Assert.assertEquals("team-task:collect", createdSessionPayload.get("taskId"));
        Assert.assertEquals("researcher", createdSessionPayload.get("memberId"));
        Assert.assertEquals("Researcher", createdSessionPayload.get("memberName"));
        Assert.assertEquals("Collect requirements", createdSessionPayload.get("task"));
        Assert.assertEquals("planned", createdSessionPayload.get("phase"));
        Assert.assertEquals(Integer.valueOf(0), createdSessionPayload.get("percent"));

        Map<String, Object> updatedPayload = new LinkedHashMap<String, Object>();
        updatedPayload.put("taskId", "team-task:collect");
        updatedPayload.put("callId", "team-task:collect");
        updatedPayload.put("title", "Team task collect");
        updatedPayload.put("status", "completed");
        updatedPayload.put("phase", "completed");
        updatedPayload.put("percent", Integer.valueOf(100));
        updatedPayload.put("heartbeatCount", Integer.valueOf(2));
        updatedPayload.put("output", "Collected requirement list\nCaptured risks");
        updatedPayload.put("durationMillis", Long.valueOf(33L));

        AgentEvent updatedEvent = AgentEvent.builder()
                .type(AgentEventType.TEAM_TASK_UPDATED)
                .step(1)
                .message("Team task collect")
                .payload(updatedPayload)
                .build();

        Assert.assertEquals(SessionEventType.TASK_UPDATED, AgentTeamSessionEventSupport.resolveSessionEventType(updatedEvent));
        Map<String, Object> updatedSessionPayload = AgentTeamSessionEventSupport.buildPayload(updatedEvent);
        Assert.assertEquals("completed", updatedSessionPayload.get("status"));
        Assert.assertEquals("Collected requirement list\nCaptured risks", updatedSessionPayload.get("output"));
        Assert.assertEquals("completed", updatedSessionPayload.get("phase"));
        Assert.assertEquals(Integer.valueOf(100), updatedSessionPayload.get("percent"));
        Assert.assertEquals(Integer.valueOf(2), updatedSessionPayload.get("heartbeatCount"));
        Assert.assertEquals(Arrays.asList("Collected requirement list", "Captured risks"), updatedSessionPayload.get("previewLines"));
    }
}
