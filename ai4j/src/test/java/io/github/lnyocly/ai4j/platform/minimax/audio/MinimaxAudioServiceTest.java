package io.github.lnyocly.ai4j.platform.minimax.audio;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.config.MinimaxConfig;
import io.github.lnyocly.ai4j.exception.AiClientException;
import io.github.lnyocly.ai4j.platform.openai.audio.entity.TextToSpeech;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IAudioService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class MinimaxAudioServiceTest {
    private static TextToSpeech speech() {
        return TextToSpeech.builder().model("speech-2.8-hd").input("Hello")
                .voice("English_expressive_narrator").speed(1.2).responseFormat("wav").build();
    }

    private static IAudioService service(MockWebServer server) {
        Configuration config = new Configuration();
        MinimaxConfig minimax = new MinimaxConfig();
        minimax.setApiHost(server.url("/").toString());
        minimax.setApiKey("test-key");
        config.setMinimaxConfig(minimax);
        config.setOkHttpClient(new OkHttpClient());
        return new AiService(config).getAudioService(PlatformType.MINIMAX);
    }

    private static MockResponse response(String audio) {
        JSONObject data = new JSONObject();
        data.put("audio", audio);
        data.put("status", 2);
        JSONObject root = new JSONObject();
        root.put("data", data);
        root.put("base_resp", JSON.parseObject("{\"status_code\":0}"));
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(root.toJSONString());
    }

    @Test
    public void mapsRequestAndDecodesAudioThroughFactory() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(response("0041ff"));
            server.start();
            try (InputStream audio = service(server).textToSpeech(speech())) {
                assertEquals(0, audio.read());
                assertEquals(65, audio.read());
                assertEquals(255, audio.read());
                assertEquals(-1, audio.read());
            }
            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("POST", request.getMethod());
            assertEquals("/v1/t2a_v2", request.getPath());
            assertEquals("Bearer test-key", request.getHeader("Authorization"));
            JSONObject body = JSON.parseObject(request.getBody().readUtf8());
            assertEquals("speech-2.8-hd", body.getString("model"));
            assertEquals("Hello", body.getString("text"));
            assertFalse(body.getBooleanValue("stream"));
            assertEquals("hex", body.getString("output_format"));
            assertEquals("English_expressive_narrator", body.getJSONObject("voice_setting").getString("voice_id"));
            assertEquals(1.2, body.getJSONObject("voice_setting").getDoubleValue("speed"), 0.001);
            assertEquals("wav", body.getJSONObject("audio_setting").getString("format"));
            assertFalse(body.containsKey("input"));
            assertFalse(body.containsKey("voice"));
        }
    }

    @Test
    public void returnsUrlAndHonorsPerCallHostAndKey() throws Exception {
        try (MockWebServer server = new MockWebServer(); MockWebServer override = new MockWebServer()) {
            server.start();
            override.enqueue(response("https://example.test/audio.wav"));
            override.start();
            assertEquals("https://example.test/audio.wav", service(server).textToSpeechUrl(
                    override.url("/").toString(), "override-key", speech()));
            RecordedRequest request = override.takeRequest(1, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("Bearer override-key", request.getHeader("Authorization"));
            assertEquals("url", JSON.parseObject(request.getBody().readUtf8()).getString("output_format"));
            assertEquals(0, server.getRequestCount());
        }
    }

    @Test
    public void rejectsBusinessErrorsMissingAudioAndInvalidHex() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"base_resp\":{\"status_code\":1004,\"status_msg\":\"invalid key\"}}"));
            server.enqueue(new MockResponse().setBody("{\"base_resp\":{\"status_code\":0},\"data\":null}"));
            server.enqueue(new MockResponse().setBody("{\"data\":{\"audio\":\"00\"}}"));
            server.enqueue(response("abc"));
            server.enqueue(response("zz"));
            server.start();
            IAudioService service = service(server);
            for (String expected : new String[] {"1004", "missing audio", "missing status code", "invalid hexadecimal", "invalid hexadecimal"}) {
                try {
                    service.textToSpeech(speech());
                    fail("Expected speech response failure");
                } catch (AiClientException e) {
                    assertTrue(e.getMessage(), e.getMessage().contains(expected));
                }
            }
        }
    }

    @Test
    public void unsupportedOperationsDoNotSendRequests() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            IAudioService service = service(server);
            try {
                service.transcription(null);
                fail("Expected unsupported transcription");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage().contains("speech synthesis only"));
            }
            try {
                service.translation(null);
                fail("Expected unsupported translation");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage().contains("speech synthesis only"));
            }
            assertEquals(0, server.getRequestCount());
        }
    }
}
