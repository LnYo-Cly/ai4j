package io.github.lnyocly.ai4j.plugin.askuser;

import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.extension.ServiceLoaderExtensionLoader;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandHandler;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandRequest;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidationReport;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidator;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class AskUserExtensionTest {

    @Test
    public void manifestDeclaresOfficialAskUserCapabilities() {
        ExtensionManifest manifest = new AskUserExtension().manifest();

        Assert.assertEquals("ask-user", manifest.getId());
        Assert.assertEquals("Ask User", manifest.getName());
        Assert.assertEquals("2.4.2", manifest.getVersion());
        Assert.assertEquals("ai4j", manifest.getVendor());
        Assert.assertTrue(manifest.hasCapability(ExtensionCapability.TOOL));
        Assert.assertTrue(manifest.hasCapability(ExtensionCapability.COMMAND));
        Assert.assertTrue(manifest.hasCapability(ExtensionCapability.SKILL));
        Assert.assertTrue(manifest.hasCapability(ExtensionCapability.PROMPT));
        Assert.assertFalse(manifest.hasCapability(ExtensionCapability.GUARDRAIL));
        Assert.assertEquals("ai4j.extensions.ask-user", manifest.getConfigPrefix());
    }

    @Test
    public void extensionContractIsValid() {
        ExtensionRegistry registry = ExtensionRegistry.of(new AskUserExtension());

        ExtensionValidationReport report = ExtensionValidator.validate(registry, "ask-user");

        Assert.assertTrue(report.getIssues().toString(), report.isValid());
        Assert.assertEquals("pass", report.getStatus());
    }

    @Test
    public void toolReturnsHostMediatedRequestWithoutBlocking() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new AskUserExtension())
                .enable("ask-user")
                .exposeTool("ask_user");

        ExtensionRuntimeSnapshot snapshot = registry.snapshot();
        ExtensionToolExecutor executor = snapshot.getToolExecutors().get("ask_user");

        String result = executor.execute(new ExtensionToolCall("ask_user",
                "{\"question\":\"Which database should I use?\",\"choices\":[\"H2\",\"PostgreSQL\"],\"blocking\":true}"));

        Assert.assertTrue(result.contains("\"type\":\"ai4j.ask_user.request\""));
        Assert.assertTrue(result.contains("\"source\":\"tool\""));
        Assert.assertTrue(result.contains("\"hostAction\":\"render_question_to_user\""));
        Assert.assertTrue(result.contains("\"status\":\"pending_user_input\""));
        Assert.assertTrue(result.contains("\"argumentsRaw\""));
        Assert.assertTrue(result.contains("Which database should I use?"));
    }

    @Test
    public void commandReturnsHostMediatedRequest() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new AskUserExtension()).enable("ask-user");
        ExtensionRuntimeSnapshot snapshot = registry.snapshot();
        ExtensionCommandHandler handler = snapshot.getCommandHandlers().get("ask-user");

        String result = handler.handle(new ExtensionCommandRequest("ask-user", "Should I create a migration file?"));

        Assert.assertTrue(result.contains("\"source\":\"command\""));
        Assert.assertTrue(result.contains("\"command\":\"ask-user\""));
        Assert.assertTrue(result.contains("\"question\":\"Should I create a migration file?\""));
        Assert.assertTrue(result.contains("\"argumentsRaw\":\"Should I create a migration file?\""));
    }

    @Test
    public void toolEscapesMalformedArgumentsIntoStableEnvelope() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new AskUserExtension())
                .enable("ask-user")
                .exposeTool("ask_user");
        ExtensionToolExecutor executor = registry.snapshot().getToolExecutors().get("ask_user");

        String result = executor.execute(new ExtensionToolCall("ask_user", "{bad\njson"));

        Assert.assertTrue(result.contains("\"argumentsRaw\":\"{bad\\njson\""));
        Assert.assertTrue(result.contains("\"status\":\"pending_user_input\""));
    }

    @Test
    public void toolCapsOversizedArgumentsRaw() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.of(new AskUserExtension())
                .enable("ask-user")
                .exposeTool("ask_user");
        ExtensionToolExecutor executor = registry.snapshot().getToolExecutors().get("ask_user");

        String result = executor.execute(new ExtensionToolCall("ask_user", repeat('x', 20000)));

        Assert.assertTrue(result.contains("\"argumentsTruncated\":true"));
        Assert.assertTrue(result.length() < 17000);
    }

    @Test
    public void serviceLoaderDiscoversAskUserExtension() {
        ServiceLoaderExtensionLoader loader = new ServiceLoaderExtensionLoader(AskUserExtension.class.getClassLoader());

        List<Ai4jExtension> extensions = loader.load();

        boolean found = false;
        for (Ai4jExtension extension : extensions) {
            if ("ask-user".equals(extension.manifest().getId())) {
                found = true;
            }
        }
        Assert.assertTrue("ask-user extension should be discoverable by ServiceLoader", found);
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
