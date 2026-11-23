package io.github.lnyocly.ai4j.plugin.codeact;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutor;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutorProvider;
import io.github.lnyocly.ai4j.agent.dynamicworkflow.DynamicWorkflowAgentBridge;
import io.github.lnyocly.ai4j.agent.dynamicworkflow.DynamicWorkflowAgentCallRequest;
import io.github.lnyocly.ai4j.agent.dynamicworkflow.DynamicWorkflowExecutor;
import io.github.lnyocly.ai4j.agent.dynamicworkflow.DynamicWorkflowExecutorProvider;
import io.github.lnyocly.ai4j.agent.dynamicworkflow.NashornDynamicWorkflowExecutor;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidationReport;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidator;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import javax.script.ScriptEngineManager;
import java.util.ServiceLoader;

public class CodeActExtensionTest {

    @Test
    public void discoversSpiProvidersViaServiceLoader() {
        DefaultCodeExecutorProvider codeProvider = null;
        for (CodeExecutorProvider provider : ServiceLoader.load(CodeExecutorProvider.class)) {
            if (provider instanceof DefaultCodeExecutorProvider) {
                codeProvider = (DefaultCodeExecutorProvider) provider;
                break;
            }
        }
        Assert.assertNotNull(codeProvider);
        Assert.assertTrue(codeProvider.isAvailable());
        CodeExecutor defaultExecutor = codeProvider.create();
        Assert.assertNotNull(defaultExecutor);

        NashornDynamicWorkflowExecutorProvider workflowProvider = null;
        for (DynamicWorkflowExecutorProvider provider : ServiceLoader.load(DynamicWorkflowExecutorProvider.class)) {
            if (provider instanceof NashornDynamicWorkflowExecutorProvider) {
                workflowProvider = (NashornDynamicWorkflowExecutorProvider) provider;
                break;
            }
        }
        Assert.assertNotNull(workflowProvider);
        DynamicWorkflowExecutor workflowExecutor = workflowProvider.create(
                new DynamicWorkflowAgentBridge() {
                    @Override
                    public String runAgent(DynamicWorkflowAgentCallRequest request) {
                        return request.getPrompt();
                    }
                },
                null
        );
        Assert.assertTrue(workflowExecutor instanceof NashornDynamicWorkflowExecutor);
    }

    @Test
    public void discoversAndRegistersCordisServicesAndTool() throws Exception {
        CodeActExtension discovered = null;
        for (Ai4jExtension ext : ServiceLoader.load(Ai4jExtension.class)) {
            if (ext instanceof CodeActExtension) {
                discovered = (CodeActExtension) ext;
                break;
            }
        }
        Assert.assertNotNull(discovered);
        Assert.assertEquals(CodeActExtension.EXTENSION_ID, discovered.manifest().getId());
        Assert.assertTrue(discovered.manifest().hasCapability(ExtensionCapability.TOOL));

        ExtensionRegistry registry = ExtensionRegistry.discover()
                .enable(CodeActExtension.EXTENSION_ID)
                .exposeTool(CodeActExtension.NAMESPACED_TOOL_NAME);

        Assert.assertTrue(registry.findService(CodeActExtension.CODE_EXECUTOR_PROVIDER_KEY).isPresent());
        Assert.assertTrue(registry.findService(CodeActExtension.DYNAMIC_WORKFLOW_PROVIDER_KEY).isPresent());
        Assert.assertTrue(registry.findService(CodeActExtension.DEFAULT_CODE_EXECUTOR_KEY).isPresent());
        Assert.assertTrue(registry.findService(CodeActExtension.NASHORN_CODE_EXECUTOR_KEY).isPresent());
        Assert.assertTrue(registry.findService(CodeActExtension.GRAALVM_CODE_EXECUTOR_KEY).isPresent());

        ExtensionValidationReport report = ExtensionValidator.validate(registry, CodeActExtension.EXTENSION_ID);
        Assert.assertTrue("Expected zero validation errors", report.isValid());

        if (new ScriptEngineManager().getEngineByName("nashorn") != null) {
            ExtensionRuntimeSnapshot snapshot = registry.snapshot();
            ExtensionToolExecutor executor = snapshot.getToolExecutors().get(CodeActExtension.NAMESPACED_TOOL_NAME);
            Assert.assertNotNull(executor);
            String resultJson = executor.execute(new ExtensionToolCall(
                    CodeActExtension.NAMESPACED_TOOL_NAME,
                    "{\"language\":\"javascript\",\"code\":\"return 6 * 7;\"}"
            ));
            JsonNode node = JsonCodec.readTree(resultJson);
            Assert.assertTrue(node.path("success").asBoolean());
            Assert.assertEquals("42", node.path("result").asText());
        }

        registry.disable(CodeActExtension.EXTENSION_ID);
        Assert.assertFalse(registry.findService(CodeActExtension.DEFAULT_CODE_EXECUTOR_KEY).isPresent());
        Assert.assertFalse(registry.findService(CodeActExtension.CODE_EXECUTOR_PROVIDER_KEY).isPresent());
    }
}
