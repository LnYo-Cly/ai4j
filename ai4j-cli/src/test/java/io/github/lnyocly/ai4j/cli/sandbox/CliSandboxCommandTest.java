package io.github.lnyocly.ai4j.cli.sandbox;

import org.junit.Assert;
import org.junit.Test;

public class CliSandboxCommandTest {

    @Test
    public void parseBlankCommandAsStatus() {
        CliSandboxCommand command = CliSandboxCommand.parse(" ");

        Assert.assertEquals(CliSandboxCommand.Action.STATUS, command.getAction());
        Assert.assertNull(command.getProviderId());
    }

    @Test
    public void parseEnableDaytonaWithWorkspaceAndLifecycleFlags() {
        CliSandboxCommand command = CliSandboxCommand.parse(
                "enable daytona --workspace ai4j-work --image java-dev --delete-on-close --no-create-if-missing"
        );

        Assert.assertEquals(CliSandboxCommand.Action.ENABLE, command.getAction());
        Assert.assertEquals("daytona", command.getProviderId());
        Assert.assertEquals("ai4j-work", command.getWorkspaceId());
        Assert.assertEquals("java-dev", command.getImage());
        Assert.assertTrue(command.isDeleteOnClose());
        Assert.assertFalse(command.isCreateIfMissing());
    }

    @Test
    public void parseAttachDaytonaSandboxIdAndAllowsCreateIfMissing() {
        CliSandboxCommand command = CliSandboxCommand.parse(
                "attach daytona sbx_123 --workspace ai4j-work --create-if-missing"
        );

        Assert.assertEquals(CliSandboxCommand.Action.ATTACH, command.getAction());
        Assert.assertEquals("daytona", command.getProviderId());
        Assert.assertEquals("sbx_123", command.getSandboxIdOrName());
        Assert.assertEquals("ai4j-work", command.getWorkspaceId());
        Assert.assertTrue(command.isCreateIfMissing());
    }

    @Test
    public void rejectsCredentialArguments() {
        try {
            CliSandboxCommand.parse("enable daytona --api-key secret");
            Assert.fail("Expected --api-key to be rejected");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage().contains("Credentials must come from environment or local config"));
        }
    }

    @Test
    public void splitShellLikePreservesWindowsBackslashes() {
        CliSandboxCommand command = CliSandboxCommand.parse(
                "enable daytona --workspace C:\\tmp\\ai4j\\sandbox --image \"java dev\""
        );

        Assert.assertEquals("C:\\tmp\\ai4j\\sandbox", command.getWorkspaceId());
        Assert.assertEquals("java dev", command.getImage());
    }

    @Test
    public void splitShellLikeHandlesEscapedQuotes() {
        CliSandboxCommand command = CliSandboxCommand.parse(
                "enable daytona --workspace \"ai4j \\\"quoted\\\" work\""
        );

        Assert.assertEquals("ai4j \"quoted\" work", command.getWorkspaceId());
    }
}
