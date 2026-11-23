package io.github.lnyocly.ai4j.plugin.colab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

public class ColabExtensionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void discoversAndRegistersServicesToolsSkillsPromptsAndCleanup() throws Exception {
        ColabExtension discovered = null;
        for (Ai4jExtension ext : ServiceLoader.load(Ai4jExtension.class)) {
            if (ext instanceof ColabExtension) {
                discovered = (ColabExtension) ext;
                break;
            }
        }
        Assert.assertNotNull(discovered);
        Assert.assertEquals(ColabExtension.EXTENSION_ID, discovered.manifest().getId());
        Assert.assertTrue(discovered.manifest().hasCapability(ExtensionCapability.TOOL));
        Assert.assertTrue(discovered.manifest().hasCapability(ExtensionCapability.SKILL));
        Assert.assertTrue(discovered.manifest().hasCapability(ExtensionCapability.PROMPT));

        final AtomicBoolean kernelDeleted = new AtomicBoolean(false);
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @NotNull
                @Override
                public MockResponse dispatch(@NotNull RecordedRequest request) {
                    String path = request.getPath();
                    if ("POST".equals(request.getMethod()) && "/api/kernels".equals(path)) {
                        return new MockResponse().setResponseCode(201).setBody("{\"id\":\"k-ext\",\"name\":\"python3\"}");
                    }
                    if ("DELETE".equals(request.getMethod()) && "/api/kernels/k-ext".equals(path)) {
                        kernelDeleted.set(true);
                        return new MockResponse().setResponseCode(204);
                    }
                    if (path != null && path.startsWith("/api/kernels/k-ext/channels")) {
                        return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                            @Override
                            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                                try {
                                    JsonNode frame = MAPPER.readTree(text);
                                    String msgId = frame.path("header").path("msg_id").asText();
                                    webSocket.send("{\"msg_type\":\"stream\",\"parent_header\":{\"msg_id\":\"" + msgId + "\"},\"content\":{\"name\":\"stdout\",\"text\":\"4\\n\"}}");
                                    webSocket.send("{\"msg_type\":\"status\",\"parent_header\":{\"msg_id\":\"" + msgId + "\"},\"content\":{\"execution_state\":\"idle\"}}");
                                    webSocket.send("{\"msg_type\":\"execute_reply\",\"parent_header\":{\"msg_id\":\"" + msgId + "\"},\"content\":{\"status\":\"ok\"}}");
                                    webSocket.close(1000, "done");
                                } catch (Exception ex) {
                                    webSocket.close(1011, ex.getMessage());
                                }
                            }

                            @Override
                            public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                                webSocket.close(code, reason);
                            }
                        });
                    }
                    return new MockResponse().setResponseCode(404);
                }
            });
            server.start();

            ColabSandboxConfig config = ColabSandboxConfig.builder()
                    .baseUrl(server.url("/").toString())
                    .apiToken("ext-token")
                    .build();
            ColabExtension extension = new ColabExtension(config);

            ExtensionRegistry registry = ExtensionRegistry.of(extension)
                    .enable(ColabExtension.EXTENSION_ID)
                    .exposeTool(ColabExtension.NAMESPACED_TOOL_NAME)
                    .requireExplicitResourceActivation()
                    .allowSkill(ColabExtension.SKILL_NAME)
                    .allowPrompt(ColabExtension.PROMPT_NAME);

            Assert.assertTrue(registry.findService(ColabExtension.KERNEL_CLIENT_SERVICE_KEY).isPresent());
            Assert.assertTrue(registry.findService(ColabExtension.SANDBOX_PROVIDER_SERVICE_KEY).isPresent());

            ExtensionRuntimeSnapshot snapshot = registry.snapshot();
            Assert.assertEquals(1, snapshot.getTools().size());
            Assert.assertEquals(1, snapshot.getSkills().size());
            Assert.assertEquals(1, snapshot.getPrompts().size());

            ExtensionToolExecutor executor = snapshot.getToolExecutors().get(ColabExtension.NAMESPACED_TOOL_NAME);
            Assert.assertNotNull(executor);
            String resultJson = executor.execute(new ExtensionToolCall(
                    ColabExtension.NAMESPACED_TOOL_NAME,
                    "{\"code\":\"print(2 + 2)\"}"
            ));
            JsonNode resultNode = MAPPER.readTree(resultJson);
            Assert.assertEquals(0, resultNode.path("exitCode").asInt());
            Assert.assertEquals("4\n", resultNode.path("stdout").asText());
            Assert.assertEquals("k-ext", resultNode.path("kernelId").asText());

            registry.disable(ColabExtension.EXTENSION_ID);
            Assert.assertTrue(extension.isStopped());
            Assert.assertTrue(kernelDeleted.get());
            Assert.assertFalse(registry.findService(ColabExtension.KERNEL_CLIENT_SERVICE_KEY).isPresent());
        }
    }
}
