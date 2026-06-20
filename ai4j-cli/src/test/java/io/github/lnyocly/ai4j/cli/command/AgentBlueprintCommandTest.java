package io.github.lnyocly.ai4j.cli.command;

import io.github.lnyocly.ai4j.tui.StreamsTerminalIO;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class AgentBlueprintCommandTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldPrintBundledSchema() {
        Path workspace = temporaryFolder.getRoot().toPath();
        AgentBlueprintCommand command = new AgentBlueprintCommand(workspace);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = command.run(Arrays.asList("schema"), terminal(out, err));

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(new String(err.toByteArray(), StandardCharsets.UTF_8), 0, exitCode);
        Assert.assertTrue(output.contains("\"$id\": \"https://schemas.ai4j.dev/agent-blueprint.v1.schema.json\""));
        Assert.assertTrue(output.contains("\"const\": \"ai4j.agent/v1\""));
        Assert.assertTrue(output.contains("\"workflow\""));
        Assert.assertTrue(output.contains("\"sandbox\""));
    }

    @Test
    public void shouldWriteBundledSchemaToOutputFile() throws Exception {
        Path workspace = temporaryFolder.newFolder("workspace").toPath();
        AgentBlueprintCommand command = new AgentBlueprintCommand(workspace);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = command.run(Arrays.asList("schema", "--out", "schema/agent-blueprint.schema.json"), terminal(out, err));

        Path output = workspace.resolve("schema/agent-blueprint.schema.json");
        Assert.assertEquals(new String(err.toByteArray(), StandardCharsets.UTF_8), 0, exitCode);
        Assert.assertTrue(Files.exists(output));
        Assert.assertTrue(new String(Files.readAllBytes(output), StandardCharsets.UTF_8).contains("Agent Blueprint"));
        Assert.assertTrue(new String(out.toByteArray(), StandardCharsets.UTF_8).contains("Agent Blueprint JSON Schema written"));
    }

    @Test
    public void shouldRejectUnexpectedBlueprintAction() {
        AgentBlueprintCommand command = new AgentBlueprintCommand(temporaryFolder.getRoot().toPath());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = command.run(Arrays.asList("unknown"), terminal(out, err));

        Assert.assertEquals(2, exitCode);
        Assert.assertTrue(new String(err.toByteArray(), StandardCharsets.UTF_8).contains("Unknown blueprint action: unknown"));
    }

    private StreamsTerminalIO terminal(ByteArrayOutputStream out, ByteArrayOutputStream err) {
        return new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), out, err);
    }
}
