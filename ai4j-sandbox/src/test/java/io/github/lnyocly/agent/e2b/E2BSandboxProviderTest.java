package io.github.lnyocly.agent.e2b;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import io.github.lnyocly.ai4j.agent.sandbox.e2b.E2BSandboxConfig;
import io.github.lnyocly.ai4j.agent.sandbox.e2b.E2BSandboxProvider;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Offline integration test for {@link E2BSandboxProvider} against an in-process HTTP harness,
 * covering the create -> execute (Connect) -> delete lifecycle and the request frames the client
 * emits.
 */
public class E2BSandboxProviderTest {

    @Test
    public void createExecuteDeleteShouldSpeakConnectAgainstLocalHost() throws Exception {
        E2BLocalHttpServer server = E2BLocalHttpServer.start();
        try {
            String stdoutB64 = Base64.getEncoder().encodeToString("ai4j-e2b-ok\n".getBytes(StandardCharsets.UTF_8));
            server.enqueueJson(201, "{\"sandboxID\":\"e2b-unit-1\",\"clientID\":\"c1\","
                    + "\"envdVersion\":\"0.6.4\",\"templateID\":\"base\",\"alias\":\"base\"}")
                    .enqueue(200, "application/connect+json", connectFrames(
                            frame(0x00, "{\"event\":{\"start\":{\"pid\":11}}}"),
                            frame(0x00, "{\"event\":{\"data\":{\"stdout\":\"" + stdoutB64 + "\"}}}"),
                            frame(0x00, "{\"event\":{\"end\":{\"exitCode\":0,\"exited\":true}}}"),
                            frame(0x02, "{}")))
                    .enqueue(204, null, new byte[0]);

            E2BSandboxProvider provider = new E2BSandboxProvider(config(server, true));
            SandboxSession session = provider.createSession(SandboxSpec.builder()
                    .providerId("e2b")
                    .image("base")
                    .build());

            Assert.assertEquals("e2b", session.getProviderId());
            Assert.assertEquals("e2b-unit-1", session.getSessionId());
            Assert.assertEquals(SandboxStatus.RUNNING, session.getStatus());

            SandboxResult result = session.execute(SandboxCommand.builder()
                    .commandId("cmd-e2b-1")
                    .command("echo ai4j-e2b-ok")
                    .workingDirectory("/code")
                    .timeoutMillis(2000L)
                    .environment("LANG", "C")
                    .build());

            Assert.assertEquals("cmd-e2b-1", result.getCommandId());
            Assert.assertEquals(Integer.valueOf(0), result.getExitCode());
            Assert.assertEquals("ai4j-e2b-ok\n", result.getStdout());
            Assert.assertEquals(2, result.getEvents().size());

            session.close();
            Assert.assertEquals(SandboxStatus.CLOSED, session.getStatus());

            // create
            Assert.assertEquals("POST", server.record(0).method);
            Assert.assertEquals("/sandboxes", server.record(0).path);
            Assert.assertEquals("unit-key", server.record(0).apiKey);
            Assert.assertTrue(server.record(0).bodyUtf8().contains("\"templateID\":\"base\""));
            Assert.assertTrue(server.record(0).bodyUtf8().contains("\"timeout\""));

            // execute (Connect framed request)
            E2BLocalHttpServer.RequestRecord exec = server.record(1);
            Assert.assertEquals("POST", exec.method);
            Assert.assertEquals("/process.Process/Start", exec.path);
            Assert.assertEquals("application/connect+json", exec.contentType);
            Assert.assertEquals("Bearer unit-key", exec.authorization);
            Assert.assertEquals("request must be a single Connect envelope (flags 0x00)", 0x00, exec.body[0] & 0xFF);
            String execJson = envelopePayload(exec.body);
            Assert.assertTrue("shell-wrap should run via sh -c", execJson.contains("\"cmd\":\"sh\""));
            Assert.assertTrue(execJson.contains("\"args\":[\"-c\",\"echo ai4j-e2b-ok\"]"));
            Assert.assertTrue(execJson.contains("\"cwd\":\"/code\""));
            Assert.assertTrue(execJson.contains("\"envs\":{\"LANG\":\"C\"}"));
            Assert.assertTrue(execJson.contains("\"stdin\":false"));

            // delete
            Assert.assertEquals("DELETE", server.record(2).method);
            Assert.assertEquals("/sandboxes/e2b-unit-1", server.record(2).path);
            Assert.assertEquals("unit-key", server.record(2).apiKey);
        } finally {
            server.close();
        }
    }

    @Test
    public void stdinShouldBePipedThroughShellWrap() throws Exception {
        E2BLocalHttpServer server = E2BLocalHttpServer.start();
        try {
            server.enqueueJson(201, "{\"sandboxID\":\"e2b-unit-2\",\"clientID\":\"c2\","
                    + "\"envdVersion\":\"0.6.4\",\"templateID\":\"base\"}")
                    .enqueue(200, "application/connect+json", connectFrames(
                            frame(0x00, "{\"event\":{\"end\":{\"exitCode\":0,\"exited\":true}}}"),
                            frame(0x02, "{}")));

            E2BSandboxProvider provider = new E2BSandboxProvider(config(server, true));
            SandboxSession session = provider.createSession(SandboxSpec.builder().providerId("e2b").build());
            session.execute(SandboxCommand.builder()
                    .commandId("cmd-stdin")
                    .command("cat")
                    .stdin("hello stdin")
                    .build());

            String execJson = envelopePayload(server.record(1).body);
            Assert.assertTrue("stdin must be piped via printf", execJson.contains("printf '%s' 'hello stdin'"));
            Assert.assertTrue(execJson.contains("| ( cat )"));
        } finally {
            server.close();
        }
    }

    @Test
    public void deleteOnCloseFalseShouldKeepSandbox() throws Exception {
        E2BLocalHttpServer server = E2BLocalHttpServer.start();
        try {
            server.enqueueJson(201, "{\"sandboxID\":\"e2b-unit-3\",\"clientID\":\"c3\","
                    + "\"envdVersion\":\"0.6.4\",\"templateID\":\"base\"}");
            E2BSandboxProvider provider = new E2BSandboxProvider(config(server, false));
            SandboxSession session = provider.createSession(SandboxSpec.builder().providerId("e2b").build());
            session.close();
            Assert.assertEquals(SandboxStatus.CLOSED, session.getStatus());
            Assert.assertEquals("only the create call should have happened", 1, server.records().size());
        } finally {
            server.close();
        }
    }

    @Test
    public void createSessionShouldFailWhenApiKeyMissing() {
        E2BSandboxConfig config = E2BSandboxConfig.builder()
                .apiUrl("http://127.0.0.1:1")
                .sandboxUrl("http://127.0.0.1:1")
                .build();
        E2BSandboxProvider provider = new E2BSandboxProvider(config);
        try {
            provider.createSession(SandboxSpec.builder().providerId("e2b").build());
            Assert.fail("expected missing API key to fail");
        } catch (Exception expected) {
            Assert.assertTrue(expected.getMessage().contains("E2B API key is required"));
        }
    }

    private static E2BSandboxConfig config(E2BLocalHttpServer server, boolean deleteOnClose) {
        return E2BSandboxConfig.builder()
                .apiKey("unit-key")
                .apiUrl(server.baseUrl())
                .sandboxUrl(server.baseUrl())
                .templateId("base")
                .timeoutSeconds(Integer.valueOf(60))
                .deleteOnClose(Boolean.valueOf(deleteOnClose))
                .connectTimeoutMillis(Long.valueOf(3000L))
                .readTimeoutMillis(Long.valueOf(3000L))
                .startTimeoutMillis(Long.valueOf(3000L))
                .pollIntervalMillis(Long.valueOf(1L))
                .build();
    }

    private static byte[] frame(int flags, String json) throws IOException {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 5);
        out.write(flags & 0xFF);
        out.write((payload.length >>> 24) & 0xFF);
        out.write((payload.length >>> 16) & 0xFF);
        out.write((payload.length >>> 8) & 0xFF);
        out.write(payload.length & 0xFF);
        out.write(payload, 0, payload.length);
        return out.toByteArray();
    }

    private static byte[] connectFrames(byte[]... frames) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] f : frames) {
            out.write(f);
        }
        return out.toByteArray();
    }

    private static String envelopePayload(byte[] frame) {
        int length = ((frame[1] & 0xFF) << 24) | ((frame[2] & 0xFF) << 16)
                | ((frame[3] & 0xFF) << 8) | (frame[4] & 0xFF);
        return new String(frame, 5, length, StandardCharsets.UTF_8);
    }
}
