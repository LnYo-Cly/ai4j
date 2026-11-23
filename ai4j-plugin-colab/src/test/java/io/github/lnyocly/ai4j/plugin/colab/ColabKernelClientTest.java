package io.github.lnyocly.ai4j.plugin.colab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxArtifact;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ColabKernelClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void managesKernelLifecycleAndExecutesPythonWithStructuredOutputs() throws Exception {
        final List<String> seenFrames = new CopyOnWriteArrayList<String>();
        final List<String> authHeaders = new CopyOnWriteArrayList<String>();
        final byte[] pngBytes = new byte[]{1, 2, 3, 4, 5};
        final String pngBase64 = Base64.getEncoder().encodeToString(pngBytes);

        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @NotNull
                @Override
                public MockResponse dispatch(@NotNull RecordedRequest request) {
                    String path = request.getPath();
                    String auth = request.getHeader("Authorization");
                    if (auth != null) {
                        authHeaders.add(auth);
                    }
                    if ("POST".equals(request.getMethod()) && "/api/kernels".equals(path)) {
                        return jsonResponse(201, "{\"id\":\"kernel-1\",\"name\":\"python3\",\"execution_state\":\"idle\"}");
                    }
                    if ("GET".equals(request.getMethod()) && "/api/kernels".equals(path)) {
                        return jsonResponse(200, "[{\"id\":\"kernel-1\",\"name\":\"python3\",\"execution_state\":\"idle\"}]");
                    }
                    if ("GET".equals(request.getMethod()) && "/api/kernels/kernel-1".equals(path)) {
                        return jsonResponse(200, "{\"id\":\"kernel-1\",\"name\":\"python3\",\"execution_state\":\"idle\"}");
                    }
                    if ("POST".equals(request.getMethod()) && "/api/kernels/kernel-1/interrupt".equals(path)) {
                        return jsonResponse(204, "");
                    }
                    if ("POST".equals(request.getMethod()) && "/api/kernels/kernel-1/restart".equals(path)) {
                        return jsonResponse(200, "{\"id\":\"kernel-1\",\"name\":\"python3\",\"execution_state\":\"starting\"}");
                    }
                    if ("DELETE".equals(request.getMethod()) && "/api/kernels/kernel-1".equals(path)) {
                        return jsonResponse(204, "");
                    }
                    if (path != null && path.startsWith("/api/kernels/kernel-1/channels")) {
                        return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                            @Override
                            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                                seenFrames.add(text);
                                try {
                                    JsonNode frame = MAPPER.readTree(text);
                                    String msgId = frame.path("header").path("msg_id").asText();
                                    webSocket.send(iopubMessage(msgId, "stream",
                                            "{\"name\":\"stdout\",\"text\":\"hello colab\\n\"}"));
                                    webSocket.send(iopubMessage(msgId, "stream",
                                            "{\"name\":\"stderr\",\"text\":\"warn line\\n\"}"));
                                    webSocket.send(iopubMessage(msgId, "execute_result",
                                            "{\"data\":{\"text/plain\":\"42\"}}"));
                                    webSocket.send(iopubMessage(msgId, "display_data",
                                            "{\"data\":{\"image/png\":\"" + pngBase64 + "\",\"application/json\":{\"loss\":0.12}}}"));
                                    webSocket.send(iopubMessage(msgId, "status",
                                            "{\"execution_state\":\"idle\"}"));
                                    webSocket.send(shellReply(msgId, "ok"));
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
                    return jsonResponse(404, "{\"message\":\"not found\"}");
                }
            });
            server.start();

            ColabSandboxConfig config = ColabSandboxConfig.builder()
                    .baseUrl(server.url("/").toString())
                    .apiToken("secret-token")
                    .kernelName("python3")
                    .build();
            try (ColabKernelClient client = new ColabKernelClient(config)) {
                ColabKernelClient.KernelInfo created = client.createKernel("python3");
                Assert.assertEquals("kernel-1", created.getId());
                Assert.assertEquals(1, client.listKernels().size());
                Assert.assertEquals("idle", client.getKernel("kernel-1").getExecutionState());

                ColabKernelClient.ExecutionResult exec = client.executePython(
                        "kernel-1",
                        "print('hello colab')\n42",
                        10_000L,
                        Collections.singletonMap("COLAB_ENV", "unit"),
                        "/content/work"
                );
                Assert.assertEquals(0, exec.getExitCode());
                Assert.assertTrue(exec.getStdout().contains("hello colab"));
                Assert.assertTrue(exec.getStdout().contains("42"));
                Assert.assertTrue(exec.getStderr().contains("warn line"));
                Assert.assertEquals(3, exec.getOutputs().size());
                Assert.assertEquals(2, exec.getArtifacts().size());

                SandboxArtifact pngArtifact = exec.getArtifacts().get(0);
                Assert.assertEquals("image/png", pngArtifact.getMimeType());
                Assert.assertEquals(Long.valueOf(pngBytes.length), pngArtifact.getSizeBytes());
                Assert.assertNotNull(pngArtifact.getSha256());

                SandboxArtifact jsonArtifact = exec.getArtifacts().get(1);
                Assert.assertEquals("application/json", jsonArtifact.getMimeType());

                JsonNode sent = MAPPER.readTree(seenFrames.get(0));
                String sentCode = sent.path("content").path("code").asText();
                Assert.assertTrue(sentCode.contains("os.chdir(\"/content/work\")"));
                Assert.assertTrue(sentCode.contains("os.environ[\"COLAB_ENV\"] = \"unit\""));

                client.interruptKernel("kernel-1");
                client.restartKernel("kernel-1");
                client.deleteKernel("kernel-1");
            }
            Assert.assertTrue(authHeaders.contains("token secret-token"));
        }
    }

    @Test
    public void capturesKernelErrorTracebackAndHttpFailures() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @NotNull
                @Override
                public MockResponse dispatch(@NotNull RecordedRequest request) {
                    String path = request.getPath();
                    if (path != null && path.startsWith("/api/kernels/k-err/channels")) {
                        return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                            @Override
                            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                                try {
                                    JsonNode frame = MAPPER.readTree(text);
                                    String msgId = frame.path("header").path("msg_id").asText();
                                    webSocket.send(iopubMessage(msgId, "error",
                                            "{\"ename\":\"ZeroDivisionError\",\"evalue\":\"division by zero\",\"traceback\":[\"ZeroDivisionError: division by zero\"]}"));
                                    webSocket.send(shellReply(msgId, "error"));
                                    webSocket.send(iopubMessage(msgId, "status", "{\"execution_state\":\"idle\"}"));
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
                    return jsonResponse(500, "{\"error\":\"boom\"}");
                }
            });
            server.start();

            ColabSandboxConfig config = ColabSandboxConfig.builder()
                    .baseUrl(server.url("/").toString())
                    .apiToken("Bearer custom-bearer")
                    .build();
            Assert.assertEquals("Bearer custom-bearer", config.resolveAuthorizationHeader());

            try (ColabKernelClient client = new ColabKernelClient(config)) {
                ColabKernelClient.ExecutionResult result = client.executePython("k-err", "1 / 0");
                Assert.assertEquals(1, result.getExitCode());
                Assert.assertTrue(result.getStderr().contains("ZeroDivisionError: division by zero"));

                try {
                    client.createKernel("python3");
                    Assert.fail("Expected SandboxException on HTTP 500");
                } catch (SandboxException ex) {
                    Assert.assertTrue(ex.getMessage().contains("HTTP 500"));
                }
            }
        }
    }

    private static MockResponse jsonResponse(int code, String body) {
        return new MockResponse()
                .setResponseCode(code)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static String iopubMessage(String parentMsgId, String msgType, String contentJson) {
        return "{"
                + "\"msg_type\":\"" + msgType + "\","
                + "\"parent_header\":{\"msg_id\":\"" + parentMsgId + "\"},"
                + "\"content\":" + contentJson
                + "}";
    }

    private static String shellReply(String parentMsgId, String status) {
        return "{"
                + "\"msg_type\":\"execute_reply\","
                + "\"parent_header\":{\"msg_id\":\"" + parentMsgId + "\"},"
                + "\"content\":{\"status\":\"" + status + "\"}"
                + "}";
    }
}
