package io.github.lnyocly.ai4j.agent.a2a;

import io.github.lnyocly.ai4j.extension.DiscoveredExtension;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidationReport;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidator;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Tests Cordis discovery, {@link A2AExtension} service provision, tool registration, and cleanup.
 */
public class A2AExtensionTest {

    @Test
    public void serviceLoaderDiscoversA2AExtensionAndManagesClientLifecycle() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.discover();
        DiscoveredExtension discovered = findById(registry.list(), A2AExtension.EXTENSION_ID);
        Assert.assertNotNull("ServiceLoader should discover A2AExtension", discovered);

        registry.enable(A2AExtension.EXTENSION_ID);
        registry.exposeTool(A2AExtension.NAMESPACED_TOOL_NAME);

        A2AClient client = registry.findService(A2AExtension.CLIENT_KEY).orElse(null);
        Assert.assertNotNull("A2AClient service should be registered", client);

        ExtensionRuntimeSnapshot snapshot = registry.snapshot();
        Assert.assertTrue(snapshot.getToolExecutors().containsKey(A2AExtension.NAMESPACED_TOOL_NAME));

        ExtensionValidationReport report = ExtensionValidator.validate(registry, A2AExtension.EXTENSION_ID);
        Assert.assertTrue("Extension validation should pass with zero errors", report.isValid());
        Assert.assertFalse("Tool capability should not be empty", report.hasWarnings());

        A2AExtension standalone = new A2AExtension();
        Assert.assertFalse(standalone.isActive());

        registry.disable(A2AExtension.EXTENSION_ID);
        Assert.assertFalse(registry.findService(A2AExtension.CLIENT_KEY).isPresent());
    }

    @Test
    public void extensionToolRejectsMissingAgentUrl() {
        ExtensionRegistry registry = ExtensionRegistry.discover();
        registry.enable(A2AExtension.EXTENSION_ID);
        registry.exposeTool(A2AExtension.NAMESPACED_TOOL_NAME);

        ExtensionRuntimeSnapshot snapshot = registry.snapshot();
        ExtensionToolExecutor executor = snapshot.getToolExecutors().get(A2AExtension.NAMESPACED_TOOL_NAME);
        Assert.assertNotNull(executor);

        try {
            executor.execute(new ExtensionToolCall(A2AExtension.TOOL_NAME, "{\"message\":\"hello\"}"));
            Assert.fail("expected missing agentUrl to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("agentUrl"));
        } catch (Exception other) {
            Assert.fail("unexpected exception type: " + other);
        }
    }

    private static DiscoveredExtension findById(List<DiscoveredExtension> extensions, String id) {
        for (DiscoveredExtension ext : extensions) {
            if (ext != null && id.equals(ext.getManifest().getId())) {
                return ext;
            }
        }
        return null;
    }
}
