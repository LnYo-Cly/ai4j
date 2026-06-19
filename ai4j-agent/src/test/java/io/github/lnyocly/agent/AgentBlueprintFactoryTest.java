package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprint;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintLoader;
import io.github.lnyocly.ai4j.agent.blueprint.AgentFactory;
import io.github.lnyocly.ai4j.agent.blueprint.AgentFactoryContext;
import io.github.lnyocly.ai4j.agent.blueprint.AgentFactoryException;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

public class AgentBlueprintFactoryTest {

    private final AgentBlueprintLoader loader = new AgentBlueprintLoader();
    private final AgentFactory factory = new AgentFactory();

    @Test
    public void shouldCreateReactAgentFromBlueprintWithHostModelClient() throws Exception {
        RecordingModelClient modelClient = new RecordingModelClient("ok");
        AgentBlueprint blueprint = loader.load("version: ai4j.agent/v1\n"
                + "id: factory-agent\n"
                + "model:\n"
                + "  provider: openai-compatible\n"
                + "  model: gpt-test\n"
                + "  options:\n"
                + "    temperature: 0.2\n"
                + "    topP: '0.8'\n"
                + "    maxOutputTokens: 128\n"
                + "instructions:\n"
                + "  system: System text\n"
                + "  developer: Developer text\n"
                + "workflow:\n"
                + "  mode: react\n"
                + "  maxTurns: 3\n");

        Agent agent = factory.create(blueprint, AgentFactoryContext.builder()
                .modelClient(modelClient)
                .baseOptions(AgentOptions.builder().stream(true).build())
                .build());

        AgentResult result = agent.run(AgentRequest.builder().input("hello").build());

        Assert.assertEquals("ok", result.getOutputText());
        Assert.assertEquals("gpt-test", modelClient.lastPrompt.getModel());
        Assert.assertTrue(modelClient.lastPrompt.getSystemPrompt().contains("System text"));
        Assert.assertEquals("Developer text", modelClient.lastPrompt.getInstructions());
        Assert.assertEquals(Double.valueOf(0.2d), modelClient.lastPrompt.getTemperature());
        Assert.assertEquals(Double.valueOf(0.8d), modelClient.lastPrompt.getTopP());
        Assert.assertEquals(Integer.valueOf(128), modelClient.lastPrompt.getMaxOutputTokens());
        Assert.assertFalse(Boolean.TRUE.equals(modelClient.lastPrompt.getStream()));
    }

    @Test
    public void shouldMapCodeActWorkflowToCodeActRuntime() throws Exception {
        RecordingModelClient modelClient = new RecordingModelClient("{\"type\":\"final\",\"output\":\"codeact-ok\"}");
        AgentBlueprint blueprint = loader.load("version: ai4j.agent/v1\n"
                + "id: codeact-agent\n"
                + "model:\n"
                + "  provider: openai-compatible\n"
                + "  model: gpt-test\n"
                + "instructions:\n"
                + "  system: Base system\n"
                + "workflow:\n"
                + "  mode: codeact\n"
                + "  maxTurns: 2\n");

        Agent agent = factory.create(blueprint, AgentFactoryContext.builder().modelClient(modelClient).build());

        AgentResult result = agent.run(AgentRequest.builder().input("hello").build());

        Assert.assertEquals("codeact-ok", result.getOutputText());
        Assert.assertTrue(modelClient.lastPrompt.getSystemPrompt().contains("Base system"));
        Assert.assertTrue(modelClient.lastPrompt.getSystemPrompt().contains("You are a CodeAct agent"));
    }

    @Test
    public void shouldFailForInvalidBlueprintBeforeModelClientUse() {
        RecordingModelClient modelClient = new RecordingModelClient("ok");
        AgentBlueprint blueprint = loader.load("version: ai4j.agent/v1\n"
                + "id: bad id\n"
                + "model:\n"
                + "  provider: openai-compatible\n"
                + "  model: gpt-test\n");

        try {
            factory.create(blueprint, AgentFactoryContext.builder().modelClient(modelClient).build());
            Assert.fail("expected factory exception");
        } catch (AgentFactoryException expected) {
            Assert.assertEquals("blueprint.validation.failed", expected.getCode());
            Assert.assertNotNull(expected.getValidationReport());
            Assert.assertTrue(expected.getValidationReport().hasIssueCode("blueprint.id.invalid"));
        }
        Assert.assertNull(modelClient.lastPrompt);
    }

    @Test
    public void shouldRequireHostProvidedModelClient() {
        AgentBlueprint blueprint = loader.load("version: ai4j.agent/v1\n"
                + "id: missing-client\n"
                + "model:\n"
                + "  provider: openai-compatible\n"
                + "  model: gpt-test\n");

        try {
            factory.create(blueprint, AgentFactoryContext.builder().build());
            Assert.fail("expected model client exception");
        } catch (AgentFactoryException expected) {
            Assert.assertEquals("blueprint.modelClient.required", expected.getCode());
            Assert.assertTrue(expected.getMessage().contains("does not read provider tokens"));
        }
    }

    @Test
    public void shouldNotResolveProfileAsSecretOrModelClient() {
        AgentBlueprint blueprint = loader.load("version: ai4j.agent/v1\n"
                + "id: profile-only\n"
                + "model:\n"
                + "  provider: openai-compatible\n"
                + "  profile: default\n");

        try {
            factory.create(blueprint, AgentFactoryContext.builder().modelClient(new RecordingModelClient("ok")).build());
            Assert.fail("expected factory model exception");
        } catch (AgentFactoryException expected) {
            Assert.assertEquals("blueprint.model.model.requiredForFactory", expected.getCode());
            Assert.assertTrue(expected.getMessage().contains("profile is host metadata only"));
        }
    }

    @Test
    public void shouldRejectSandboxDeclarationUntilSandboxSpiExists() {
        AgentBlueprint blueprint = loader.load("version: ai4j.agent/v1\n"
                + "id: sandbox-agent\n"
                + "model:\n"
                + "  provider: openai-compatible\n"
                + "  model: gpt-test\n"
                + "sandbox:\n"
                + "  enabled: true\n"
                + "  provider: remote-vm\n");

        try {
            factory.create(blueprint, AgentFactoryContext.builder().modelClient(new RecordingModelClient("ok")).build());
            Assert.fail("expected sandbox unsupported exception");
        } catch (AgentFactoryException expected) {
            Assert.assertEquals("blueprint.sandbox.unsupported", expected.getCode());
            Assert.assertTrue(expected.getMessage().contains("does not create sandbox"));
        }
    }

    @Test
    public void shouldAllowSandboxDeclarationOnlyWhenHostExplicitlyAcceptsIt() throws Exception {
        RecordingModelClient modelClient = new RecordingModelClient("ok");
        AgentBlueprint blueprint = loader.load("version: ai4j.agent/v1\n"
                + "id: sandbox-agent\n"
                + "model:\n"
                + "  provider: openai-compatible\n"
                + "  model: gpt-test\n"
                + "sandbox:\n"
                + "  enabled: true\n"
                + "  provider: remote-vm\n");

        Agent agent = factory.create(blueprint, AgentFactoryContext.builder()
                .modelClient(modelClient)
                .allowSandboxDeclaration(true)
                .build());

        Assert.assertEquals("ok", agent.run(AgentRequest.builder().input("hi").build()).getOutputText());
    }

    @Test
    public void shouldRejectInvalidModelOptionTypes() {
        AgentBlueprint blueprint = loader.load("version: ai4j.agent/v1\n"
                + "id: bad-options\n"
                + "model:\n"
                + "  provider: openai-compatible\n"
                + "  model: gpt-test\n"
                + "  options:\n"
                + "    temperature: hot\n");

        try {
            factory.create(blueprint, AgentFactoryContext.builder().modelClient(new RecordingModelClient("ok")).build());
            Assert.fail("expected option type exception");
        } catch (AgentFactoryException expected) {
            Assert.assertEquals("blueprint.option.number.invalid", expected.getCode());
            Assert.assertTrue(expected.getMessage().contains("$.model.options.temperature"));
        }
    }

    private static class RecordingModelClient implements AgentModelClient {
        private final String text;
        private AgentPrompt lastPrompt;

        private RecordingModelClient(String text) {
            this.text = text;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            this.lastPrompt = prompt;
            return AgentModelResult.builder()
                    .outputText(text)
                    .memoryItems(new ArrayList<Object>())
                    .build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            this.lastPrompt = prompt;
            return create(prompt);
        }
    }
}
