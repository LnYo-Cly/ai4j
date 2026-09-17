package io.github.lnyocly.ai4j.coding.loop;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.event.AgentListener;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.CodingAgentRequest;
import io.github.lnyocly.ai4j.coding.CodingAgentResult;
import io.github.lnyocly.ai4j.coding.CodingAgents;
import io.github.lnyocly.ai4j.coding.CodingSession;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * A model stream that reports an error and completes with blank output must end
 * the loop with ERROR instead of auto-continuing — regardless of loop limits.
 */
public class StreamErrorLoopReproTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldStopWithErrorWhenModelErrorsWithBlankOutput() throws Exception {
        CodingAgentResult result = runWithOptions(CodingAgentOptions.builder().build());
        assertEquals(CodingStopReason.ERROR, result.getStopReason());
        assertEquals(1, result.getTurns());
    }

    @Test
    public void shouldStopWithErrorEvenWhenLoopLimitsAreUnbounded() throws Exception {
        CodingAgentResult result = runWithOptions(CodingAgentOptions.builder()
                .maxAutoFollowUps(0)
                .maxTotalTurns(0)
                .build());
        assertEquals(CodingStopReason.ERROR, result.getStopReason());
        assertEquals(1, result.getTurns());
    }

    private CodingAgentResult runWithOptions(CodingAgentOptions codingOptions) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AgentModelClient modelClient = new AgentModelClient() {
            @Override
            public AgentModelResult create(AgentPrompt prompt) {
                calls.incrementAndGet();
                return AgentModelResult.builder().outputText("").build();
            }

            @Override
            public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
                calls.incrementAndGet();
                AgentModelResult result = AgentModelResult.builder().outputText("").build();
                if (listener != null) {
                    listener.onError(new RuntimeException("Invalid API key"));
                    listener.onComplete(result);
                }
                return result;
            }
        };

        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model("fake-model")
                .workspaceContext(WorkspaceContext.builder()
                        .rootPath(temporaryFolder.newFolder().toString())
                        .build())
                .agentOptions(io.github.lnyocly.ai4j.agent.AgentOptions.builder().stream(true).build())
                .codingOptions(codingOptions)
                .build();

        AtomicInteger errorEvents = new AtomicInteger();
        try (CodingSession session = agent.newSession()) {
            CodingAgentResult result = session.runStream(
                    CodingAgentRequest.builder().input("stream auth fail").build(),
                    new AgentListener() {
                        @Override
                        public void onEvent(AgentEvent event) {
                            if (event != null && event.getType() == AgentEventType.ERROR) {
                                errorEvents.incrementAndGet();
                            }
                        }
                    });

            assertEquals("model calls", 1, calls.get());
            assertEquals("error events", 1, errorEvents.get());
            return result;
        }
    }
}
