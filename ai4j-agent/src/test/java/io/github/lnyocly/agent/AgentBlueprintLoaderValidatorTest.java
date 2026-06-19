package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprint;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintLoadException;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintLoader;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintValidationIssue;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintValidationReport;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintValidator;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;

public class AgentBlueprintLoaderValidatorTest {

    private final AgentBlueprintLoader loader = new AgentBlueprintLoader();
    private final AgentBlueprintValidator validator = new AgentBlueprintValidator();

    @Test
    public void shouldLoadValidMinimalBlueprint() {
        AgentBlueprint blueprint = loadFixture("valid-minimal.yaml");

        Assert.assertEquals("ai4j.agent/v1", blueprint.getVersion());
        Assert.assertEquals("minimal-agent", blueprint.getId());
        Assert.assertEquals("openai-compatible", blueprint.getModel().getProvider());
        Assert.assertEquals("gpt-4.1-mini", blueprint.getModel().getModel());
        Assert.assertEquals("react", blueprint.getWorkflow().getMode());

        AgentBlueprintValidationReport report = validator.validate(blueprint);
        Assert.assertTrue(report.getIssues().toString(), report.isValid());
        Assert.assertTrue(report.getWarnings().isEmpty());
    }

    @Test
    public void shouldLoadRoadmapStyleBlueprint() {
        AgentBlueprint blueprint = loadFixture("valid-roadmap-style.yaml");

        Assert.assertEquals("coding-assistant", blueprint.getId());
        Assert.assertEquals("Coding Assistant", blueprint.getName());
        Assert.assertEquals("default", blueprint.getModel().getProfile());
        Assert.assertEquals("You are a careful coding agent.\n", blueprint.getInstructions().getSystem());
        Assert.assertEquals(2, blueprint.getPlugins().size());
        Assert.assertEquals("coding.shell", blueprint.getTools().get(1).getRef());
        Assert.assertEquals(Double.valueOf(0.75d), blueprint.getSession().getCompact().getTrigger().getContextRatio());
        Assert.assertFalse(blueprint.getSandbox().getEnabled());

        AgentBlueprintValidationReport report = validator.validate(blueprint);
        Assert.assertTrue(report.getIssues().toString(), report.isValid());
    }

    @Test
    public void shouldRejectMissingRequiredFields() {
        AgentBlueprint blueprint = loader.load("id: bad agent id\nmodel:\n  model: gpt-4.1-mini\ntools:\n  - approval: safe\nplugins:\n  - enabled: true\n");

        AgentBlueprintValidationReport report = validator.validate(blueprint);

        Assert.assertFalse(report.isValid());
        assertHasCode(report, "blueprint.version.required");
        assertHasCode(report, "blueprint.id.invalid");
        assertHasCode(report, "blueprint.model.provider.required");
        assertHasCode(report, "blueprint.tool.ref.required");
        assertHasCode(report, "blueprint.plugin.id.required");
    }

    @Test
    public void shouldRejectInvalidCompactRatio() {
        AgentBlueprintValidationReport report = validator.validate(loadFixture("invalid-compact-ratio.yaml"));

        Assert.assertFalse(report.isValid());
        assertHasCode(report, "blueprint.compact.contextRatio.invalid");
        Assert.assertEquals("$.session.compact.trigger.contextRatio", issueByCode(report, "blueprint.compact.contextRatio.invalid").getPath());
    }

    @Test
    public void shouldRejectInvalidWorkflowModeAndMaxTurns() {
        AgentBlueprintValidationReport report = validator.validate(loadFixture("invalid-workflow.yaml"));

        Assert.assertFalse(report.isValid());
        assertHasCode(report, "blueprint.workflow.mode.invalid");
        assertHasCode(report, "blueprint.workflow.maxTurns.invalid");
    }

    @Test
    public void shouldRejectEnabledSandboxWithoutProviderOrProfile() {
        AgentBlueprintValidationReport report = validator.validate(loadFixture("invalid-sandbox.yaml"));

        Assert.assertFalse(report.isValid());
        assertHasCode(report, "blueprint.sandbox.selector.required");
    }

    @Test
    public void shouldWarnForUnknownTopLevelFieldAndIncompleteMemory() {
        AgentBlueprintValidationReport report = validator.validate(loadFixture("warning-unknown-field.yaml"));

        Assert.assertTrue(report.getErrors().toString(), report.isValid());
        assertHasCode(report, "blueprint.field.unknown");
        assertHasCode(report, "blueprint.memory.scope.warning");
        Assert.assertEquals(2, report.getWarnings().size());
    }

    @Test
    public void shouldReportInvalidYamlAsStableLoadError() {
        try {
            loadFixture("invalid-yaml.yaml");
            Assert.fail("expected load exception");
        } catch (AgentBlueprintLoadException expected) {
            Assert.assertEquals("blueprint.yaml.invalid", expected.getCode());
            Assert.assertTrue(expected.getMessage().contains("Invalid Agent Blueprint YAML"));
            Assert.assertFalse("message must not include local workspace path", expected.getMessage().contains("G:"));
        }
    }

    @Test
    public void shouldLoadFromPathWithoutProviderCredentials() {
        AgentBlueprint blueprint = loader.load(Paths.get("src", "test", "resources", "agent-blueprint", "valid-minimal.yaml"));

        Assert.assertEquals("minimal-agent", blueprint.getId());
        Assert.assertTrue(validator.validate(blueprint).isValid());
    }

    private AgentBlueprint loadFixture(String name) {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("agent-blueprint/" + name);
        Assert.assertNotNull("missing fixture " + name, inputStream);
        return loader.load(inputStream);
    }

    private void assertHasCode(AgentBlueprintValidationReport report, String code) {
        Assert.assertTrue("missing issue code " + code + " in " + report.getIssues(), report.hasIssueCode(code));
    }

    private AgentBlueprintValidationIssue issueByCode(AgentBlueprintValidationReport report, String code) {
        List<AgentBlueprintValidationIssue> issues = report.getIssues();
        for (AgentBlueprintValidationIssue issue : issues) {
            if (code.equals(issue.getCode())) {
                return issue;
            }
        }
        Assert.fail("missing issue code " + code);
        return null;
    }
}
