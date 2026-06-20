package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.memory.InMemoryAgentMemory;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxArtifact;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import io.github.lnyocly.ai4j.agent.session.AgentSessionEvent;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSandboxBinding;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import io.github.lnyocly.ai4j.agent.session.InMemoryAgentSessionStore;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AgentSessionSandboxBindingTest {

    @Test
    public void sessionShouldBindSandboxSummaryIntoSnapshotAndEventLog() {
        io.github.lnyocly.ai4j.agent.AgentSession session = testAgent().newSession();
        FakeSandboxSession sandbox = new FakeSandboxSession();

        session.bindSandbox(sandbox);

        AgentSessionSandboxBinding binding = session.getSandboxBinding();
        Assert.assertNotNull(binding);
        Assert.assertEquals("fake", binding.getProviderId());
        Assert.assertEquals("sandbox-1", binding.getSandboxSessionId());
        Assert.assertEquals(SandboxStatus.RUNNING, binding.getStatus());
        Assert.assertEquals("unit", binding.getProfile());
        Assert.assertEquals("java8-test", binding.getImage());
        Assert.assertEquals("workspace-1", binding.getWorkspaceId());
        Assert.assertEquals("P2-B", binding.getLabels().get("task"));
        Assert.assertFalse(binding.getLabels().containsKey("apiToken"));
        Assert.assertFalse(binding.getLabels().containsKey("secret_name"));

        AgentSessionSnapshot snapshot = session.snapshot();
        Assert.assertEquals("fake", snapshot.getSandboxBinding().getProviderId());
        Assert.assertTrue(hasType(session.getEventLog().getEvents(), AgentEventType.SANDBOX_BOUND));
    }

    @Test
    public void snapshotRestoreAndStoreShouldPreserveSandboxBinding() {
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        Agent agent = testAgent(store);
        io.github.lnyocly.ai4j.agent.AgentSession session = agent.newSession();
        session.bindSandbox(new FakeSandboxSession());
        session.updateSandboxStatus(SandboxStatus.CLOSED);

        session.save();

        io.github.lnyocly.ai4j.agent.AgentSession resumed = agent.resumeSession(session.getSessionId());
        AgentSessionSandboxBinding binding = resumed.getSandboxBinding();

        Assert.assertNotNull(binding);
        Assert.assertEquals("fake", binding.getProviderId());
        Assert.assertEquals("sandbox-1", binding.getSandboxSessionId());
        Assert.assertEquals(SandboxStatus.CLOSED, binding.getStatus());
        Assert.assertTrue(hasType(resumed.getEventLog().getEvents(), AgentEventType.SANDBOX_BOUND));
        Assert.assertTrue(hasType(resumed.getEventLog().getEvents(), AgentEventType.SANDBOX_UPDATED));
    }

    @Test
    public void sandboxBindingShouldUseDefensiveCopies() {
        io.github.lnyocly.ai4j.agent.AgentSession session = testAgent().newSession();
        session.bindSandbox(new FakeSandboxSession());

        AgentSessionSandboxBinding binding = session.getSandboxBinding();
        binding.getLabels().put("task", "mutated");
        Assert.assertEquals("P2-B", session.getSandboxBinding().getLabels().get("task"));

        AgentSessionSnapshot snapshot = session.snapshot();
        snapshot.getSandboxBinding().getLabels().put("task", "snapshot-mutated");
        Assert.assertEquals("P2-B", session.snapshot().getSandboxBinding().getLabels().get("task"));

        session.clearSandbox();
        Assert.assertNull(session.getSandboxBinding());
        Assert.assertTrue(hasType(session.getEventLog().getEvents(), AgentEventType.SANDBOX_CLEARED));
    }

    @Test
    public void bindingCanBeCreatedWithoutLiveSandboxSession() {
        io.github.lnyocly.ai4j.agent.AgentSession session = testAgent().newSession();
        AgentSessionSandboxBinding binding = AgentSessionSandboxBinding.builder()
                .providerId("external")
                .sandboxSessionId("external-session")
                .status(SandboxStatus.CREATED)
                .workspaceId("external-workspace")
                .label("owner", "agent")
                .label("password", "must-not-persist")
                .build();

        session.bindSandbox(binding);

        Assert.assertEquals("external", session.snapshot().getSandboxBinding().getProviderId());
        Assert.assertEquals("agent", session.snapshot().getSandboxBinding().getLabels().get("owner"));
        Assert.assertFalse(session.snapshot().getSandboxBinding().getLabels().containsKey("password"));
    }

    private static boolean hasType(List<AgentSessionEvent> events, AgentEventType type) {
        for (AgentSessionEvent event : events) {
            if (event != null && event.getEvent() != null && event.getEvent().getType() == type) {
                return true;
            }
        }
        return false;
    }

    private static Agent testAgent() {
        return testAgent(null);
    }

    private static Agent testAgent(InMemoryAgentSessionStore store) {
        return Agents.react()
                .modelClient(new StaticModelClient())
                .memorySupplier(InMemoryAgentMemory::new)
                .options(AgentOptions.builder().maxSteps(2).build())
                .model("test-model")
                .sessionStore(store)
                .build();
    }

    private static class StaticModelClient implements AgentModelClient {
        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return result();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return result();
        }

        private AgentModelResult result() {
            return AgentModelResult.builder()
                    .outputText("session-answer")
                    .memoryItems(new ArrayList<Object>())
                    .toolCalls(new ArrayList<AgentToolCall>())
                    .build();
        }
    }

    private static class FakeSandboxSession implements SandboxSession {
        @Override
        public String getSessionId() {
            return "sandbox-1";
        }

        @Override
        public String getProviderId() {
            return "fake";
        }

        @Override
        public SandboxSpec getSpec() {
            return SandboxSpec.builder()
                    .providerId("fake")
                    .profile("unit")
                    .image("java8-test")
                    .workspaceId("workspace-1")
                    .label("task", "P2-B")
                    .label("apiToken", "must-not-persist")
                    .label("secret_name", "must-not-persist")
                    .config("secret", "must-not-persist")
                    .build();
        }

        @Override
        public SandboxStatus getStatus() {
            return SandboxStatus.RUNNING;
        }

        @Override
        public SandboxResult execute(SandboxCommand command) {
            return SandboxResult.builder()
                    .commandId(command.getCommandId())
                    .exitCode(0)
                    .stdout("ok")
                    .build();
        }

        @Override
        public boolean cancel(String commandId) {
            return false;
        }

        @Override
        public List<SandboxArtifact> listArtifacts() {
            return new ArrayList<SandboxArtifact>();
        }

        @Override
        public void close() throws SandboxException {
        }
    }
}
