package io.github.lnyocly.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprint;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintLoader;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintSchemas;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintValidationReport;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintValidator;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AgentBlueprintSchemasTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldExposeStableJsonSchemaResource() {
        String schema = AgentBlueprintSchemas.v1JsonSchema();
        JSONObject object = JSON.parseObject(schema);

        Assert.assertEquals("https://schemas.ai4j.dev/agent-blueprint.v1.schema.json", object.getString("$id"));
        Assert.assertTrue(schema.contains("\"const\": \"ai4j.agent/v1\""));
        Assert.assertTrue(schema.contains("\"pattern\": \"^[A-Za-z0-9._-]+$\""));
        Assert.assertTrue(schema.contains("\"react\""));
        Assert.assertTrue(schema.contains("\"codeact\""));
        Assert.assertTrue(schema.contains("\"approval\""));
        Assert.assertTrue(schema.contains("\"sandbox\""));
        Assert.assertTrue(schema.contains("Secrets must stay in host configuration"));
    }

    @Test
    public void shouldWriteBundledSchemaToFile() throws Exception {
        Path output = temporaryFolder.newFolder("schema-out").toPath().resolve("agent-blueprint.schema.json");

        AgentBlueprintSchemas.writeV1JsonSchema(output);

        String written = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        Assert.assertEquals(AgentBlueprintSchemas.v1JsonSchema(), written);
    }

    @Test
    public void shouldIgnoreOptionalSchemaFieldAtRuntime() {
        AgentBlueprint blueprint = new AgentBlueprintLoader().load("$schema: https://schemas.ai4j.dev/agent-blueprint.v1.schema.json\n"
                + "version: ai4j.agent/v1\n"
                + "id: schema-hinted-agent\n"
                + "model:\n"
                + "  provider: openai-compatible\n"
                + "  model: gpt-test\n"
                + "workflow:\n"
                + "  mode: react\n");

        AgentBlueprintValidationReport report = new AgentBlueprintValidator().validate(blueprint);

        Assert.assertTrue(report.getIssues().toString(), report.isValid());
        Assert.assertFalse(report.hasIssueCode("blueprint.field.unknown"));
    }
}
