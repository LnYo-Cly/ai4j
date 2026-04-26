package io.github.lnyocly.ai4j.cli.runtime;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class AgentTeamMessageSessionEventSupportTest {

    @Test
    public void shouldMapTeamMessageEventToSessionEvent() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("messageId", "msg-1");
        payload.put("fromMemberId", "reviewer");
        payload.put("toMemberId", "lead");
        payload.put("taskId", "review");
        payload.put("type", "task.result");
        payload.put("content", "Patch looks good.\nNo blocking issue found.");

        AgentEvent event = AgentEvent.builder()
                .type(AgentEventType.TEAM_MESSAGE)
                .step(2)
                .message("Patch looks good.")
                .payload(payload)
                .build();

        Assert.assertTrue(AgentTeamMessageSessionEventSupport.supports(event));
        Assert.assertEquals("Team message reviewer -> lead [task.result]",
                AgentTeamMessageSessionEventSupport.buildSummary(event));

        SessionEvent sessionEvent = AgentTeamMessageSessionEventSupport.toSessionEvent("session-1", "turn-1", event);
        Assert.assertNotNull(sessionEvent);
        Assert.assertEquals(SessionEventType.TEAM_MESSAGE, sessionEvent.getType());
        Assert.assertEquals("msg-1", sessionEvent.getPayload().get("messageId"));
        Assert.assertEquals("team-task:review", sessionEvent.getPayload().get("callId"));
        Assert.assertEquals("task.result", sessionEvent.getPayload().get("messageType"));
        Assert.assertEquals(Arrays.asList("Patch looks good.", "No blocking issue found."),
                sessionEvent.getPayload().get("previewLines"));
    }
}
