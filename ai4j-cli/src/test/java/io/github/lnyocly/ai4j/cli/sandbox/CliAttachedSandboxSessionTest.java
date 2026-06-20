package io.github.lnyocly.ai4j.cli.sandbox;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import org.junit.Assert;
import org.junit.Test;

public class CliAttachedSandboxSessionTest {

    @Test
    public void executeFailsLoudlyWithoutLocalFallback() throws Exception {
        CliSandboxBinding binding = CliSandboxBinding.attach("cubesandbox", "sbx_123", "workspace-a");
        CliAttachedSandboxSession session = CliAttachedSandboxSession.unsupported(binding);

        try {
            session.execute(SandboxCommand.builder().command("echo hi").build());
            Assert.fail("Expected metadata-only session to reject execution");
        } catch (SandboxException ex) {
            Assert.assertTrue(ex.getMessage().contains("metadata-only"));
            Assert.assertTrue(ex.getMessage().contains("Command was not executed locally"));
            Assert.assertTrue(ex.getMessage().contains("cubesandbox/sbx_123"));
        }
    }

    @Test
    public void exposesNonSensitiveBindingMetadata() {
        CliSandboxBinding binding = CliSandboxBinding.attach("cubesandbox", "sbx_123", "workspace-a");
        CliAttachedSandboxSession session = CliAttachedSandboxSession.unsupported(binding);

        Assert.assertEquals("cubesandbox", session.getProviderId());
        Assert.assertEquals("sbx_123", session.getSessionId());
        Assert.assertEquals("workspace-a", session.getSpec().getWorkspaceId());
    }
}
