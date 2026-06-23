package io.github.lnyocly.ai4j.cli.sandbox;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class CliSandboxSessionResolverTest {

    @Test
    public void rejectsUnsupportedProvidersBeforeReadingCredentials() {
        CliSandboxSessionResolver resolver = new CliSandboxSessionResolver();
        CliSandboxCommand command = CliSandboxCommand.parse("enable unknown-provider");

        try {
            resolver.open(command, Collections.<String, String>emptyMap());
            Assert.fail("Expected unsupported provider to be rejected");
        } catch (SandboxException ex) {
            Assert.assertTrue(ex.getMessage().contains("Unsupported sandbox provider"));
            Assert.assertTrue(ex.getMessage().contains("daytona"));
        }
    }

    @Test
    public void rejectsStatusAndDisableCommandsAsNonOpeningCommands() {
        CliSandboxSessionResolver resolver = new CliSandboxSessionResolver();

        assertDoesNotOpen(resolver, CliSandboxCommand.status());
        assertDoesNotOpen(resolver, CliSandboxCommand.disable());
    }

    private void assertDoesNotOpen(CliSandboxSessionResolver resolver, CliSandboxCommand command) {
        try {
            resolver.open(command, Collections.<String, String>emptyMap());
            Assert.fail("Expected non-opening command to be rejected");
        } catch (SandboxException ex) {
            Assert.assertTrue(ex.getMessage().contains("does not create a session"));
        }
    }
}
