package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.runner.AgentRunnerEvent;
import io.github.lnyocly.ai4j.agent.runner.AgentRunnerEventListener;
import io.github.lnyocly.ai4j.agent.runner.AgentRunnerEventType;
import io.github.lnyocly.ai4j.agent.runner.AgentRunnerException;
import io.github.lnyocly.ai4j.agent.runner.AgentRunnerProvider;
import io.github.lnyocly.ai4j.agent.runner.AgentRunnerRequest;
import io.github.lnyocly.ai4j.agent.runner.AgentRunnerResult;
import io.github.lnyocly.ai4j.agent.runner.AgentRunnerSession;
import io.github.lnyocly.ai4j.agent.runner.AgentRunnerSpec;
import io.github.lnyocly.ai4j.agent.runner.AgentRunnerStatus;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxArtifact;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AgentRunnerSpiContractTest {

    @Test
    public void fakeRunnerProviderShouldCreateSessionAndRunRequest() throws Exception {
        FakeAgentRunnerProvider provider = new FakeAgentRunnerProvider("fake-runner");
        AgentRunnerSpec spec = AgentRunnerSpec.builder()
                .providerId("fake-runner")
                .profile("unit")
                .runnerImage("ai4j-runner:test")
                .workspaceId("workspace-1")
                .sandboxSpec(SandboxSpec.builder().providerId("fake-sandbox").workspaceId("workspace-1").build())
                .label("task", "P5")
                .config("cpu", "small")
                .build();

        Assert.assertTrue(provider.supports(spec));
        AgentRunnerSession session = provider.createSession(spec);

        Assert.assertEquals("fake-runner", session.getProviderId());
        Assert.assertEquals(AgentRunnerStatus.IDLE, session.getStatus());
        Assert.assertEquals("workspace-1", session.getSpec().getWorkspaceId());
        Assert.assertEquals("fake-sandbox", session.getSpec().getSandboxSpec().getProviderId());

        AgentRunnerResult result = session.run(AgentRunnerRequest.builder()
                .runId("run-1")
                .input("hello")
                .timeoutMillis(1000L)
                .metadata("source", "unit-test")
                .build());

        Assert.assertEquals("run-1", result.getRunId());
        Assert.assertEquals(AgentRunnerStatus.IDLE, result.getStatus());
        Assert.assertEquals("fake answer: hello", result.getOutputText());
        Assert.assertEquals("fake answer: hello", result.getAgentResult().getOutputText());
        Assert.assertEquals(1, result.getArtifacts().size());
        Assert.assertEquals(3, result.getEvents().size());
        Assert.assertEquals(AgentRunnerEventType.RUN_STARTED, result.getEvents().get(0).getType());
        Assert.assertEquals(AgentRunnerEventType.MODEL_DELTA, result.getEvents().get(1).getType());
        Assert.assertEquals(AgentRunnerEventType.RUN_FINISHED, result.getEvents().get(2).getType());
        Assert.assertEquals(1, session.listArtifacts().size());
    }

    @Test
    public void streamRunShouldDeliverEventsToListener() throws Exception {
        AgentRunnerSession session = new FakeAgentRunnerProvider("fake-runner")
                .createSession(AgentRunnerSpec.builder().providerId("fake-runner").build());
        final List<AgentRunnerEvent> streamed = new ArrayList<AgentRunnerEvent>();

        AgentRunnerResult result = session.runStream(AgentRunnerRequest.builder()
                .runId("run-stream")
                .input("stream")
                .build(), new AgentRunnerEventListener() {
            @Override
            public void onEvent(AgentRunnerEvent event) {
                streamed.add(event);
            }
        });

        Assert.assertEquals("run-stream", result.getRunId());
        Assert.assertEquals(3, streamed.size());
        Assert.assertEquals(AgentRunnerEventType.RUN_FINISHED, streamed.get(2).getType());
    }

    @Test
    public void runnerDtosShouldUseDefensiveCopies() {
        AgentRunnerSpec spec = AgentRunnerSpec.builder()
                .providerId("fake-runner")
                .label("owner", "agent")
                .config("profile", "safe")
                .sandboxSpec(SandboxSpec.builder().providerId("sandbox").label("k", "v").build())
                .build();
        spec.getLabels().put("owner", "mutated");
        spec.getConfig().put("profile", "mutated");
        spec.getSandboxSpec().getLabels().put("k", "mutated");

        Assert.assertEquals("agent", spec.getLabels().get("owner"));
        Assert.assertEquals("safe", spec.getConfig().get("profile"));
        Assert.assertEquals("v", spec.getSandboxSpec().getLabels().get("k"));

        AgentRunnerRequest request = AgentRunnerRequest.builder()
                .runId("run-copy")
                .metadata("kind", "test")
                .build();
        request.getMetadata().put("kind", "mutated");
        Assert.assertEquals("test", request.getMetadata().get("kind"));

        AgentRunnerResult result = AgentRunnerResult.builder()
                .runId("run-copy")
                .status(AgentRunnerStatus.IDLE)
                .artifact(SandboxArtifact.builder().artifactId("a1").name("log.txt").path("/tmp/log.txt").build())
                .event(AgentRunnerEvent.builder().type(AgentRunnerEventType.RUN_FINISHED).runId("run-copy").build())
                .build();
        result.getArtifacts().clear();
        result.getEvents().clear();

        Assert.assertEquals(1, result.getArtifacts().size());
        Assert.assertEquals(1, result.getEvents().size());
    }

    @Test
    public void runnerSessionShouldCancelAndRejectRunAfterClose() throws Exception {
        AgentRunnerSession session = new FakeAgentRunnerProvider("fake-runner")
                .createSession(AgentRunnerSpec.builder().providerId("fake-runner").build());

        Assert.assertFalse(session.cancel("missing"));
        session.run(AgentRunnerRequest.builder().runId("run-close").input("before close").build());
        Assert.assertTrue(session.cancel("run-close"));

        session.close();
        Assert.assertEquals(AgentRunnerStatus.CLOSED, session.getStatus());
        try {
            session.run(AgentRunnerRequest.builder().input("after close").build());
            Assert.fail("expected runner exception");
        } catch (AgentRunnerException expected) {
            Assert.assertTrue(expected.getMessage().contains("closed"));
        }
    }

    @Test
    public void eventShouldRejectMissingType() {
        try {
            AgentRunnerEvent.builder().message("missing type").build();
            Assert.fail("expected validation error");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("must not be null"));
        }
    }

    private static class FakeAgentRunnerProvider implements AgentRunnerProvider {
        private final String providerId;
        private int sequence;

        private FakeAgentRunnerProvider(String providerId) {
            this.providerId = providerId;
        }

        @Override
        public String getProviderId() {
            return providerId;
        }

        @Override
        public boolean supports(AgentRunnerSpec spec) {
            return spec == null || spec.getProviderId() == null || providerId.equals(spec.getProviderId());
        }

        @Override
        public AgentRunnerSession createSession(AgentRunnerSpec spec) throws AgentRunnerException {
            if (!supports(spec)) {
                throw new AgentRunnerException("unsupported runner provider: " + (spec == null ? null : spec.getProviderId()));
            }
            sequence += 1;
            return new FakeAgentRunnerSession(providerId, "runner-session-" + sequence,
                    spec == null ? AgentRunnerSpec.builder().providerId(providerId).build() : spec.copy());
        }
    }

    private static class FakeAgentRunnerSession implements AgentRunnerSession {
        private final String providerId;
        private final String sessionId;
        private final AgentRunnerSpec spec;
        private final List<String> runIds = new ArrayList<String>();
        private final List<String> canceled = new ArrayList<String>();
        private final List<SandboxArtifact> artifacts = new ArrayList<SandboxArtifact>();
        private AgentRunnerStatus status = AgentRunnerStatus.IDLE;

        private FakeAgentRunnerSession(String providerId, String sessionId, AgentRunnerSpec spec) {
            this.providerId = providerId;
            this.sessionId = sessionId;
            this.spec = spec;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public String getProviderId() {
            return providerId;
        }

        @Override
        public AgentRunnerSpec getSpec() {
            return spec.copy();
        }

        @Override
        public AgentRunnerStatus getStatus() {
            return status;
        }

        @Override
        public AgentRunnerResult run(AgentRunnerRequest request) throws AgentRunnerException {
            return runInternal(request, null);
        }

        @Override
        public AgentRunnerResult runStream(AgentRunnerRequest request, AgentRunnerEventListener listener) throws AgentRunnerException {
            return runInternal(request, listener);
        }

        private AgentRunnerResult runInternal(AgentRunnerRequest request, AgentRunnerEventListener listener) throws AgentRunnerException {
            if (status == AgentRunnerStatus.CLOSED) {
                throw new AgentRunnerException("agent runner session is closed");
            }
            AgentRunnerRequest safeRequest = request == null ? AgentRunnerRequest.builder().build() : request.copy();
            runIds.add(safeRequest.getRunId());
            status = AgentRunnerStatus.RUNNING;
            String output = "fake answer: " + String.valueOf(safeRequest.getInput());
            SandboxArtifact artifact = SandboxArtifact.builder()
                    .artifactId("artifact-" + safeRequest.getRunId())
                    .name("runner-output.txt")
                    .path("/artifacts/" + safeRequest.getRunId() + "/runner-output.txt")
                    .mimeType("text/plain")
                    .sizeBytes(Long.valueOf(output.length()))
                    .build();
            artifacts.add(artifact);
            List<AgentRunnerEvent> events = Arrays.asList(
                    event(AgentRunnerEventType.RUN_STARTED, safeRequest.getRunId(), "started", null),
                    event(AgentRunnerEventType.MODEL_DELTA, safeRequest.getRunId(), "delta", output),
                    event(AgentRunnerEventType.RUN_FINISHED, safeRequest.getRunId(), "finished", output));
            if (listener != null) {
                for (AgentRunnerEvent event : events) {
                    listener.onEvent(event.copy());
                }
            }
            status = AgentRunnerStatus.IDLE;
            return AgentRunnerResult.builder()
                    .runId(safeRequest.getRunId())
                    .status(status)
                    .agentResult(AgentResult.builder().outputText(output).steps(Integer.valueOf(1)).build())
                    .outputText(output)
                    .durationMillis(Long.valueOf(1L))
                    .artifacts(Arrays.asList(artifact))
                    .events(events)
                    .build();
        }

        private AgentRunnerEvent event(AgentRunnerEventType type, String runId, String message, Object payload) {
            return AgentRunnerEvent.builder()
                    .type(type)
                    .sessionId(sessionId)
                    .runId(runId)
                    .message(message)
                    .payload(payload)
                    .build();
        }

        @Override
        public boolean cancel(String runId) {
            if (runIds.contains(runId)) {
                canceled.add(runId);
                return true;
            }
            return false;
        }

        @Override
        public List<SandboxArtifact> listArtifacts() {
            List<SandboxArtifact> copy = new ArrayList<SandboxArtifact>();
            for (SandboxArtifact artifact : artifacts) {
                copy.add(artifact.copy());
            }
            return copy;
        }

        @Override
        public void close() {
            status = AgentRunnerStatus.CLOSED;
        }
    }
}
