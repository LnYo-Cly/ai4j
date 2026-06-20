package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxArtifact;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxEvent;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxEventType;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AgentSandboxSpiModelTest {

    @Test
    public void fakeProviderShouldCreateSessionAndExecuteCommand() throws Exception {
        FakeSandboxProvider provider = new FakeSandboxProvider("fake");
        SandboxSpec spec = SandboxSpec.builder()
                .providerId("fake")
                .profile("unit")
                .image("java8-test")
                .workspaceId("workspace-1")
                .label("task", "P2-A")
                .config("cpu", "small")
                .build();

        Assert.assertTrue(provider.supports(spec));
        SandboxSession session = provider.createSession(spec);

        Assert.assertEquals("fake", session.getProviderId());
        Assert.assertEquals(SandboxStatus.RUNNING, session.getStatus());
        Assert.assertEquals("workspace-1", session.getSpec().getWorkspaceId());

        SandboxResult result = session.execute(SandboxCommand.builder()
                .commandId("cmd-1")
                .command("echo hello")
                .workingDirectory("/workspace")
                .timeoutMillis(1000L)
                .environment("LANG", "C")
                .metadata("tool", "shell")
                .build());

        Assert.assertEquals("cmd-1", result.getCommandId());
        Assert.assertEquals(Integer.valueOf(0), result.getExitCode());
        Assert.assertTrue(result.getStdout().contains("echo hello"));
        Assert.assertEquals(1, result.getArtifacts().size());
        Assert.assertEquals(2, result.getEvents().size());
        Assert.assertEquals(SandboxEventType.COMMAND_STARTED, result.getEvents().get(0).getType());
        Assert.assertEquals(SandboxEventType.COMMAND_FINISHED, result.getEvents().get(1).getType());
        Assert.assertEquals(1, session.listArtifacts().size());
    }

    @Test
    public void specCommandAndResultShouldUseDefensiveCopies() {
        SandboxSpec spec = SandboxSpec.builder()
                .providerId("fake")
                .label("owner", "agent")
                .config("profile", "safe")
                .build();
        spec.getLabels().put("owner", "mutated");
        spec.getConfig().put("profile", "mutated");

        Assert.assertEquals("agent", spec.getLabels().get("owner"));
        Assert.assertEquals("safe", spec.getConfig().get("profile"));

        SandboxCommand command = SandboxCommand.builder()
                .commandId("cmd-copy")
                .command("pwd")
                .environment("A", "B")
                .metadata("kind", "test")
                .build();
        command.getEnvironment().put("A", "C");
        command.getMetadata().put("kind", "mutated");

        Assert.assertEquals("B", command.getEnvironment().get("A"));
        Assert.assertEquals("test", command.getMetadata().get("kind"));

        SandboxResult result = SandboxResult.builder()
                .commandId("cmd-copy")
                .exitCode(0)
                .artifact(SandboxArtifact.builder().artifactId("a1").name("log.txt").path("/tmp/log.txt").build())
                .event(SandboxEvent.builder().type(SandboxEventType.COMMAND_FINISHED).commandId("cmd-copy").build())
                .build();
        result.getArtifacts().clear();
        result.getEvents().clear();

        Assert.assertEquals(1, result.getArtifacts().size());
        Assert.assertEquals(1, result.getEvents().size());
    }

    @Test
    public void sessionShouldCancelAndRejectExecutionAfterClose() throws Exception {
        FakeSandboxProvider provider = new FakeSandboxProvider("fake");
        SandboxSession session = provider.createSession(SandboxSpec.builder().providerId("fake").build());

        Assert.assertFalse(session.cancel("missing"));
        session.execute(SandboxCommand.builder().commandId("cmd-close").command("sleep 1").build());
        Assert.assertTrue(session.cancel("cmd-close"));

        session.close();
        Assert.assertEquals(SandboxStatus.CLOSED, session.getStatus());
        try {
            session.execute(SandboxCommand.builder().command("echo after close").build());
            Assert.fail("expected sandbox exception");
        } catch (SandboxException expected) {
            Assert.assertTrue(expected.getMessage().contains("closed"));
        }
    }

    @Test
    public void commandShouldRejectBlankCommand() {
        try {
            SandboxCommand.builder().command("  ").build();
            Assert.fail("expected validation error");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("must not be blank"));
        }
    }

    private static class FakeSandboxProvider implements SandboxProvider {
        private final String providerId;
        private int sequence;

        private FakeSandboxProvider(String providerId) {
            this.providerId = providerId;
        }

        @Override
        public String getProviderId() {
            return providerId;
        }

        @Override
        public boolean supports(SandboxSpec spec) {
            return spec == null || spec.getProviderId() == null || providerId.equals(spec.getProviderId());
        }

        @Override
        public SandboxSession createSession(SandboxSpec spec) throws SandboxException {
            if (!supports(spec)) {
                throw new SandboxException("unsupported sandbox provider: " + (spec == null ? null : spec.getProviderId()));
            }
            sequence += 1;
            return new FakeSandboxSession(providerId, "fake-session-" + sequence, spec == null ? SandboxSpec.builder().providerId(providerId).build() : spec.copy());
        }
    }

    private static class FakeSandboxSession implements SandboxSession {
        private final String providerId;
        private final String sessionId;
        private final SandboxSpec spec;
        private final List<String> commandIds = new ArrayList<String>();
        private final List<String> canceled = new ArrayList<String>();
        private final List<SandboxArtifact> artifacts = new ArrayList<SandboxArtifact>();
        private SandboxStatus status = SandboxStatus.RUNNING;

        private FakeSandboxSession(String providerId, String sessionId, SandboxSpec spec) {
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
        public SandboxSpec getSpec() {
            return spec.copy();
        }

        @Override
        public SandboxStatus getStatus() {
            return status;
        }

        @Override
        public SandboxResult execute(SandboxCommand command) throws SandboxException {
            if (status == SandboxStatus.CLOSED) {
                throw new SandboxException("sandbox session is closed");
            }
            SandboxCommand safeCommand = command.copy();
            commandIds.add(safeCommand.getCommandId());
            SandboxArtifact artifact = SandboxArtifact.builder()
                    .artifactId("artifact-" + safeCommand.getCommandId())
                    .name("stdout.txt")
                    .path("/artifacts/" + safeCommand.getCommandId() + "/stdout.txt")
                    .mimeType("text/plain")
                    .sizeBytes(Long.valueOf(safeCommand.getCommand().length()))
                    .build();
            artifacts.add(artifact);
            List<SandboxEvent> events = Arrays.asList(
                    SandboxEvent.builder()
                            .type(SandboxEventType.COMMAND_STARTED)
                            .sessionId(sessionId)
                            .commandId(safeCommand.getCommandId())
                            .message("started")
                            .build(),
                    SandboxEvent.builder()
                            .type(SandboxEventType.COMMAND_FINISHED)
                            .sessionId(sessionId)
                            .commandId(safeCommand.getCommandId())
                            .message("finished")
                            .build());
            return SandboxResult.builder()
                    .commandId(safeCommand.getCommandId())
                    .exitCode(0)
                    .stdout("fake executed: " + safeCommand.getCommand())
                    .stderr("")
                    .durationMillis(1L)
                    .artifacts(Arrays.asList(artifact))
                    .events(events)
                    .build();
        }

        @Override
        public boolean cancel(String commandId) {
            if (commandIds.contains(commandId)) {
                canceled.add(commandId);
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
            status = SandboxStatus.CLOSED;
        }
    }
}
