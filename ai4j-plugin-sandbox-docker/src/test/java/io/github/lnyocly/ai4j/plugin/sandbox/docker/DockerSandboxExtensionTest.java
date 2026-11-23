package io.github.lnyocly.ai4j.plugin.sandbox.docker;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class DockerSandboxExtensionTest {

    @Test
    public void discoversExtensionExecutesToolAndCleansUpContainerOnDisable() throws Exception {
        DockerSandboxExtension discovered = null;
        for (Ai4jExtension ext : ServiceLoader.load(Ai4jExtension.class)) {
            if (ext instanceof DockerSandboxExtension) {
                discovered = (DockerSandboxExtension) ext;
                break;
            }
        }
        Assert.assertNotNull(discovered);
        Assert.assertEquals(DockerSandboxExtension.EXTENSION_ID, discovered.manifest().getId());
        Assert.assertTrue(discovered.manifest().hasCapability(ExtensionCapability.TOOL));

        final List<List<String>> commands = new ArrayList<List<String>>();
        DockerProcessRunner fakeRunner = new DockerProcessRunner() {
            @Override
            public DockerProcessOutput run(List<String> command, String stdin, long timeoutMillis) {
                commands.add(new ArrayList<String>(command));
                if ("run".equals(command.get(1))) {
                    return DockerProcessOutput.builder().exitCode(0).stdout("cid-ext\n").build();
                }
                if ("exec".equals(command.get(1))) {
                    return DockerProcessOutput.builder().exitCode(0).stdout("docker-ok\n").durationMillis(3L).build();
                }
                return DockerProcessOutput.builder().exitCode(0).stdout("cid-ext\n").build();
            }
        };

        DockerSandboxExtension extension = new DockerSandboxExtension(
                DockerSandboxConfig.builder().image("alpine:3.20").build(),
                fakeRunner
        );
        ExtensionRegistry registry = ExtensionRegistry.of(extension)
                .enable(DockerSandboxExtension.EXTENSION_ID)
                .exposeTool(DockerSandboxExtension.NAMESPACED_TOOL_NAME);

        Assert.assertTrue(registry.findService(DockerSandboxExtension.CONFIG_SERVICE_KEY).isPresent());
        Assert.assertTrue(registry.findService(DockerSandboxExtension.PROCESS_RUNNER_SERVICE_KEY).isPresent());
        Assert.assertTrue(registry.findService(DockerSandboxExtension.SANDBOX_PROVIDER_SERVICE_KEY).isPresent());

        ExtensionValidationReport report = ExtensionValidator.validate(registry, DockerSandboxExtension.EXTENSION_ID);
        Assert.assertTrue(report.isValid());

        ExtensionRuntimeSnapshot snapshot = registry.snapshot();
        ExtensionToolExecutor executor = snapshot.getToolExecutors().get(DockerSandboxExtension.NAMESPACED_TOOL_NAME);
        Assert.assertNotNull(executor);

        String json = executor.execute(new ExtensionToolCall(
                DockerSandboxExtension.NAMESPACED_TOOL_NAME,
                "{\"command\":\"echo docker-ok\"}"
        ));
        JsonNode node = JsonCodec.readTree(json);
        Assert.assertEquals(0, node.path("exitCode").asInt());
        Assert.assertEquals("docker-ok\n", node.path("stdout").asText());

        registry.disable(DockerSandboxExtension.EXTENSION_ID);
        Assert.assertTrue(extension.isStopped());
        Assert.assertFalse(registry.findService(DockerSandboxExtension.SANDBOX_PROVIDER_SERVICE_KEY).isPresent());
        Assert.assertEquals(3, commands.size());
        Assert.assertEquals("rm", commands.get(2).get(1));
        Assert.assertEquals("-f", commands.get(2).get(2));
        Assert.assertEquals("cid-ext", commands.get(2).get(3));
    }
}
