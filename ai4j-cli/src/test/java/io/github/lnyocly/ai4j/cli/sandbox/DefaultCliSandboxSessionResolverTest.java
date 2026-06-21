package io.github.lnyocly.ai4j.cli.sandbox;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxArtifact;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class DefaultCliSandboxSessionResolverTest {

    @Test
    public void cubesandboxProviderUsesLiveConnector() throws Exception {
        RecordingCubeConnector connector = new RecordingCubeConnector();
        DefaultCliSandboxSessionResolver resolver = new DefaultCliSandboxSessionResolver(connector);

        SandboxSession session = resolver.resolve(CliSandboxBinding.attach("cubesandbox", "sb-live-1", "/workspace"));

        Assert.assertSame(connector.session, session);
        Assert.assertEquals("sb-live-1", connector.sandboxId);
        Assert.assertEquals("cubesandbox", connector.spec.getProviderId());
        Assert.assertEquals("/workspace", connector.spec.getWorkspaceId());
        Assert.assertEquals("cli-attach", connector.spec.getLabels().get("source"));
        Assert.assertEquals("cli-attach", connector.spec.getLabels().get("mode"));
    }

    @Test
    public void cubeAliasUsesLiveConnector() throws Exception {
        RecordingCubeConnector connector = new RecordingCubeConnector();
        DefaultCliSandboxSessionResolver resolver = new DefaultCliSandboxSessionResolver(connector);

        SandboxSession session = resolver.resolve(CliSandboxBinding.attach("cube", "sb-live-2", null));

        Assert.assertSame(connector.session, session);
        Assert.assertEquals("sb-live-2", connector.sandboxId);
        Assert.assertEquals("cube", connector.spec.getProviderId());
    }

    @Test
    public void unknownProviderStaysMetadataOnly() throws Exception {
        DefaultCliSandboxSessionResolver resolver = new DefaultCliSandboxSessionResolver(new RecordingCubeConnector());

        SandboxSession session = resolver.resolve(CliSandboxBinding.attach("other", "sb-meta", null));

        Assert.assertTrue(session instanceof CliAttachedSandboxSession);
        try {
            session.execute(SandboxCommand.builder().command("echo hi").build());
            Assert.fail("Expected metadata-only session to reject execution");
        } catch (SandboxException ex) {
            Assert.assertTrue(ex.getMessage().contains("metadata-only"));
        }
    }

    private static final class RecordingCubeConnector implements DefaultCliSandboxSessionResolver.CubeSandboxConnector {
        private String sandboxId;
        private SandboxSpec spec;
        private final RecordingSandboxSession session = new RecordingSandboxSession();

        @Override
        public SandboxSession connect(String sandboxId, SandboxSpec spec) {
            this.sandboxId = sandboxId;
            this.spec = spec;
            return session;
        }
    }

    private static final class RecordingSandboxSession implements SandboxSession {

        @Override
        public String getSessionId() {
            return "sb-live";
        }

        @Override
        public String getProviderId() {
            return "cubesandbox";
        }

        @Override
        public SandboxSpec getSpec() {
            return SandboxSpec.builder().providerId(getProviderId()).workspaceId("/workspace").build();
        }

        @Override
        public SandboxStatus getStatus() {
            return SandboxStatus.RUNNING;
        }

        @Override
        public SandboxResult execute(SandboxCommand command) {
            return SandboxResult.builder().exitCode(Integer.valueOf(0)).stdout("").stderr("").build();
        }

        @Override
        public boolean cancel(String commandId) {
            return false;
        }

        @Override
        public List<SandboxArtifact> listArtifacts() {
            return Collections.emptyList();
        }

        @Override
        public void close() {
        }
    }
}
