package io.github.lnyocly.ai4j.plugin.colab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

public class ColabSandboxProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void discoversProviderViaServiceLoaderAndRunsSessionLifecycle() throws Exception {
        ColabSandboxProvider discovered = null;
        for (SandboxProvider provider : ServiceLoader.load(SandboxProvider.class)) {
            if (provider instanceof ColabSandboxProvider) {
                discovered = (ColabSandboxProvider) provider;
                break;
            }
        }
        Assert.assertNotNull(discovered);
        Assert.assertEquals("colab", discovered.getProviderId());
        Assert.assertTrue(discovered.supports(SandboxSpec.builder().providerId("colab").build()));
        Assert.assertFalse(discovered.supports(SandboxSpec.builder().providerId("daytona").build()));

        final AtomicBoolean interrupted = new AtomicBoolean(false);
        final AtomicBoolean sessionDeleted = new AtomicBoolean(false);

        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @NotNull
                @Override
                public MockResponse dispatch(@NotNull RecordedRequest request) {
                    String path = request.getPath();
                    if ("POST".equals(request.getMethod()) && "/api/sessions".equals(path)) {
                        return new MockResponse()
                                .setResponseCode(201)
                                .setBody("{\"id\":\"sess-9\",\"name\":\"nb\",\"path\":\"/nb.ipynb\",\"type\":\"notebook\",\"kernel\":{\"id\":\"k-9\",\"name\":\"python3\"}}");
                    }
                    if ("POST".equals(request.getMethod()) && "/api/kernels/k-9/interrupt".equals(path)) {
                        interrupted.set(true);
                        return new MockResponse().setResponseCode(204);
                    }
                    if ("DELETE".equals(request.getMethod()) && "/api/sessions/sess-9".equals(path)) {
                        sessionDeleted.set(true);
                        return new MockResponse().setResponseCode(204);
                    }
                    if (path != null && path.startsWith("/api/kernels/k-9/channels")) {
                        return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                            @Override
                            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                                try {
                                    JsonNode frame = MAPPER.readTree(text);
                                    String msgId = frame.path("header").path("msg_id").asText();
                                    webSocket.send("{\"msg_type\":\"stream\",\"parent_header\":{\"msg_id\":\"" + msgId + "\"},\"content\":{\"name\":\"stdout\",\"text\":\"ok\\n\"}}");
                                    webSocket.send("{\"msg_type\":\"display_data\",\"parent_header\":{\"msg_id\":\"" + msgId + "\"},\"content\":{\"data\":{\"text/html\":\"<b>hi</b>\"}}}");
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

            Map<String, Object> specConfig = new LinkedHashMap<String, Object>();
            specConfig.put("baseUrl", server.url("/").toString());
            specConfig.put("apiToken", "tok-abc");

            SandboxSpec spec = SandboxSpec.builder()
                    .providerId("colab")
                    .workspaceId("ws-colab")
                    .config(specConfig)
                    .build();

            SandboxSession session = discovered.createSession(spec);
            Assert.assertEquals("sess-9", session.getSessionId());
            Assert.assertEquals("colab", session.getProviderId());
            Assert.assertEquals(SandboxStatus.RUNNING, session.getStatus());

            SandboxResult result = session.execute(SandboxCommand.builder()
                    .commandId("cmd-1")
                    .command("print('ok')")
                    .build());
            Assert.assertEquals(Integer.valueOf(0), result.getExitCode());
            Assert.assertEquals("ok\n", result.getStdout());
            Assert.assertEquals(1, session.listArtifacts().size());
            Assert.assertEquals("text/html", session.listArtifacts().get(0).getMimeType());
            Assert.assertFalse(result.getEvents().isEmpty());

            Assert.assertTrue(session.cancel("cmd-1"));
            Assert.assertTrue(interrupted.get());

            session.close();
            Assert.assertEquals(SandboxStatus.CLOSED, session.getStatus());
            Assert.assertTrue(sessionDeleted.get());
            Assert.assertFalse(session.cancel("cmd-1"));

            try {
                session.execute(SandboxCommand.builder().command("print(1)").build());
                Assert.fail("Expected SandboxException after close");
            } catch (SandboxException ex) {
                Assert.assertTrue(ex.getMessage().contains("closed"));
            }
        }
    }

    @Test
    public void fallsBackToKernelsApiWhenSessionsEndpointUnavailable() throws Exception {
        final AtomicBoolean kernelDeleted = new AtomicBoolean(false);
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @NotNull
                @Override
                public MockResponse dispatch(@NotNull RecordedRequest request) {
                    String path = request.getPath();
                    if ("POST".equals(request.getMethod()) && "/api/sessions".equals(path)) {
                        return new MockResponse().setResponseCode(404);
                    }
                    if ("POST".equals(request.getMethod()) && "/api/kernels".equals(path)) {
                        return new MockResponse().setResponseCode(201).setBody("{\"id\":\"k-fallback\",\"name\":\"python3\"}");
                    }
                    if ("DELETE".equals(request.getMethod()) && "/api/kernels/k-fallback".equals(path)) {
                        kernelDeleted.set(true);
                        return new MockResponse().setResponseCode(204);
                    }
                    return new MockResponse().setResponseCode(404);
                }
            });
            server.start();

            ColabSandboxProvider provider = new ColabSandboxProvider(null, null,
                    Collections.singletonMap(ColabSandboxConfig.ENV_COLAB_API_URL, server.url("/").toString()));
            SandboxSession session = provider.createSession(SandboxSpec.builder().providerId("google-colab").build());
            Assert.assertEquals("colab-k-fallback", session.getSessionId());
            session.close();
            Assert.assertTrue(kernelDeleted.get());
        }
    }
}
