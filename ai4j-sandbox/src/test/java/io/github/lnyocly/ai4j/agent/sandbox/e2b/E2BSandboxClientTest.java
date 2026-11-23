package io.github.lnyocly.ai4j.agent.sandbox.e2b;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure unit tests for the E2B Connect wire helpers {@link E2BSandboxClient#buildProcessFrame} and
 * {@link E2BSandboxClient#parseConnectStream}. These assert against the exact frame format
 * confirmed against the live E2B envd host (1 byte flags + big-endian uint32 length + JSON).
 */
public class E2BSandboxClientTest {

    @Test
    public void buildProcessFrameShouldProduceConnectEnvelopeWithStdinFalse() throws Exception {
        Map<String, String> envs = new LinkedHashMap<String, String>();
        envs.put("FOO", "bar");
        List<String> args = Arrays.asList("-c", "echo hi");
        byte[] frame = E2BSandboxClient.buildProcessFrame("sh", args, envs, "/code");

        // flag byte = 0x00
        Assert.assertEquals(0x00, frame[0] & 0xFF);
        // big-endian uint32 length == remaining bytes
        int declared = ((frame[1] & 0xFF) << 24) | ((frame[2] & 0xFF) << 16)
                | ((frame[3] & 0xFF) << 8) | (frame[4] & 0xFF);
        Assert.assertEquals(frame.length - 5, declared);

        String json = new String(frame, 5, declared, StandardCharsets.UTF_8);
        Assert.assertTrue("payload must wrap cmd under process", json.contains("\"process\""));
        Assert.assertTrue(json.contains("\"cmd\":\"sh\""));
        Assert.assertTrue(json.contains("\"cwd\":\"/code\""));
        Assert.assertTrue(json.contains("\"envs\":{\"FOO\":\"bar\"}"));
        Assert.assertTrue("stdin must be false", json.contains("\"stdin\":false"));
    }

    @Test
    public void parseConnectStreamShouldAggregateStartDataEndAndExitCode() throws Exception {
        String stdoutB64 = Base64.getEncoder().encodeToString("out-line\n".getBytes(StandardCharsets.UTF_8));
        String stderrB64 = Base64.getEncoder().encodeToString("err-line\n".getBytes(StandardCharsets.UTF_8));
        byte[] stream = concat(
                frame(0x00, "{\"event\":{\"start\":{\"pid\":1274}}}"),
                frame(0x00, "{\"event\":{\"data\":{\"stdout\":\"" + stdoutB64 + "\"}}}"),
                frame(0x00, "{\"event\":{\"data\":{\"stderr\":\"" + stderrB64 + "\"}}}"),
                frame(0x00, "{\"event\":{\"end\":{\"exitCode\":7,\"exited\":true,\"status\":\"exit status 7\"}}}"),
                frame(0x02, "{}"));

        E2BProcessResult result = E2BSandboxClient.parseConnectStream(new ByteArrayInputStream(stream));

        Assert.assertEquals(Long.valueOf(1274L), result.getPid());
        Assert.assertEquals(Integer.valueOf(7), result.getExitCode());
        Assert.assertTrue("exited should be true", result.isExited());
        Assert.assertEquals("out-line\n", result.getStdout());
        Assert.assertEquals("err-line\n", result.getStderr());
        Assert.assertNull("no protocol error on a normal command exit", result.getError());
    }

    @Test
    public void parseConnectStreamShouldHandleZeroExitAndChunkedOutput() throws Exception {
        String part1 = Base64.getEncoder().encodeToString("hello ".getBytes(StandardCharsets.UTF_8));
        String part2 = Base64.getEncoder().encodeToString("e2b".getBytes(StandardCharsets.UTF_8));
        byte[] stream = concat(
                frame(0x00, "{\"event\":{\"start\":{\"pid\":3}}}"),
                frame(0x00, "{\"event\":{\"data\":{\"stdout\":\"" + part1 + "\"}}}"),
                frame(0x00, "{\"event\":{\"data\":{\"stdout\":\"" + part2 + "\"}}}"),
                frame(0x00, "{\"event\":{\"end\":{\"exitCode\":0,\"exited\":true}}}"),
                frame(0x02, "{}"));

        E2BProcessResult result = E2BSandboxClient.parseConnectStream(new ByteArrayInputStream(stream));

        Assert.assertEquals("hello e2b", result.getStdout());
        Assert.assertEquals(Integer.valueOf(0), result.getExitCode());
        Assert.assertTrue(result.isExited());
    }

    @Test
    public void parseConnectStreamShouldSurfaceConnectErrorTrailer() throws Exception {
        // The end-of-stream trailer (flags 0x02) carries a Connect protocol error.
        byte[] stream = frame(0x02,
                "{\"error\":{\"code\":\"unimplemented\",\"message\":\"unary request has zero messages\"}}");

        E2BProcessResult result = E2BSandboxClient.parseConnectStream(new ByteArrayInputStream(stream));

        Assert.assertEquals("unary request has zero messages", result.getError());
        Assert.assertFalse("protocol error must not be reported as a clean exit", result.isExited());
        Assert.assertNull(result.getExitCode());
    }

    @Test
    public void parseConnectStreamShouldDeriveZeroExitCodeFromStatusWhenExitCodeAbsent() throws Exception {
        // Real E2B envd omits the numeric exitCode on a clean exit and only sends
        // "status":"exit status 0"; non-zero exits include the exitCode field instead.
        byte[] stream = concat(
                frame(0x00, "{\"event\":{\"start\":{\"pid\":5}}}"),
                frame(0x00, "{\"event\":{\"data\":{\"stdout\":\"" + Base64.getEncoder().encodeToString("ok".getBytes(StandardCharsets.UTF_8)) + "\"}}}"),
                frame(0x00, "{\"event\":{\"end\":{\"exited\":true,\"status\":\"exit status 0\"}}}"),
                frame(0x02, "{}"));

        E2BProcessResult result = E2BSandboxClient.parseConnectStream(new ByteArrayInputStream(stream));

        Assert.assertEquals("clean exit must map to exitCode 0", Integer.valueOf(0), result.getExitCode());
        Assert.assertTrue(result.isExited());
        Assert.assertEquals("ok", result.getStdout());
        Assert.assertNull(result.getError());
    }

    @Test
    public void parseConnectStreamShouldTolerateEmptyTrailerAndPlainTermination() throws Exception {
        byte[] stream = concat(
                frame(0x00, "{\"event\":{\"end\":{\"exitCode\":0,\"exited\":true}}}"),
                frame(0x02, "{}"));
        E2BProcessResult result = E2BSandboxClient.parseConnectStream(new ByteArrayInputStream(stream));
        Assert.assertEquals(Integer.valueOf(0), result.getExitCode());
        Assert.assertTrue(result.isExited());
        Assert.assertNull(result.getError());
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

    private static byte[] concat(byte[]... parts) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part);
        }
        return out.toByteArray();
    }
}
