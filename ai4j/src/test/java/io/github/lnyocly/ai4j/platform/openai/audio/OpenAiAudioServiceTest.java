package io.github.lnyocly.ai4j.platform.openai.audio;

import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.platform.openai.audio.entity.TextToSpeech;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public class OpenAiAudioServiceTest {

    @Test
    public void test_text_to_speech_stream_remains_readable_after_method_returns() throws Exception {
        final byte[] expectedAudio = "fake-mp3-audio".getBytes(StandardCharsets.UTF_8);
        final AtomicReference<Request> recordedRequest = new AtomicReference<Request>();

        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiHost("https://unit.test/");
        openAiConfig.setApiKey("config-api-key");

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    recordedRequest.set(chain.request());
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create(expectedAudio, MediaType.get("audio/mpeg")))
                            .build();
                })
                .build();

        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(openAiConfig);
        configuration.setOkHttpClient(okHttpClient);

        OpenAiAudioService service = new OpenAiAudioService(configuration);

        try (InputStream stream = service.textToSpeech(TextToSpeech.builder()
                .input("hello")
                .build())) {
            Assert.assertNotNull(stream);
            Assert.assertArrayEquals(expectedAudio, readAll(stream));
        }

        Assert.assertNotNull(recordedRequest.get());
        Assert.assertEquals("Bearer config-api-key", recordedRequest.get().header("Authorization"));
        Assert.assertEquals("https://unit.test/v1/audio/speech", recordedRequest.get().url().toString());
    }

    @Test
    public void test_text_to_speech_serializes_prompt_audio_url_for_voice_clone() throws Exception {
        final AtomicReference<Request> recordedRequest = new AtomicReference<Request>();

        OpenAiAudioService service = serviceWithInterceptor(chain -> {
            recordedRequest.set(chain.request());
            return jsonResponse("{\"url\":\"https://cdn.unit.test/a.mp3\"}");
        });

        service.textToSpeechUrl(TextToSpeech.builder()
                .model("IndexTTS-1.5")
                .input("克隆测试")
                .promptAudioUrl("https://unit.test/prompt.wav")
                .responseFormat("url")
                .build());

        okio.Buffer buffer = new okio.Buffer();
        recordedRequest.get().body().writeTo(buffer);
        String payload = buffer.readUtf8();
        Assert.assertTrue("prompt_audio_url must be serialized: " + payload,
                payload.contains("\"prompt_audio_url\":\"https://unit.test/prompt.wav\""));
        Assert.assertTrue("response_format must stay snake_case: " + payload,
                payload.contains("\"response_format\":\"url\""));
        Assert.assertTrue("model must serialize: " + payload,
                payload.contains("\"model\":\"IndexTTS-1.5\""));
    }

    @Test
    public void test_text_to_speech_url_extracts_flat_and_nested_forms() throws Exception {
        OpenAiAudioService service = serviceWithInterceptor(chain ->
                jsonResponse("{\"audio_url\":\"https://cdn.unit.test/flat.mp3\"}"));
        Assert.assertEquals("https://cdn.unit.test/flat.mp3",
                service.textToSpeechUrl(TextToSpeech.builder().input("a").responseFormat("url").build()));

        OpenAiAudioService nestedService = serviceWithInterceptor(chain ->
                jsonResponse("{\"data\":{\"url\":\"https://cdn.unit.test/nested.mp3\"}}"));
        Assert.assertEquals("https://cdn.unit.test/nested.mp3",
                nestedService.textToSpeechUrl(TextToSpeech.builder().input("a").responseFormat("url").build()));
    }

    @Test
    public void test_text_to_speech_url_fails_with_response_snippet_when_url_missing() {
        OpenAiAudioService service = serviceWithInterceptor(chain ->
                jsonResponse("{\"foo\":\"bar\"}"));
        try {
            service.textToSpeechUrl(TextToSpeech.builder().input("a").responseFormat("url").build());
            Assert.fail("expected AiClientException");
        } catch (io.github.lnyocly.ai4j.exception.AiClientException expected) {
            Assert.assertTrue(expected.getMessage().contains("foo"));
        }
    }

    private OpenAiAudioService serviceWithInterceptor(
            okhttp3.Interceptor interceptor) {
        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiHost("https://unit.test/");
        openAiConfig.setApiKey("config-api-key");

        OkHttpClient okHttpClient = new OkHttpClient.Builder().addInterceptor(interceptor).build();

        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(openAiConfig);
        configuration.setOkHttpClient(okHttpClient);

        return new OpenAiAudioService(configuration);
    }

    private static Response jsonResponse(String body) {
        return new Response.Builder()
                .request(new Request.Builder().url("https://unit.test/v1/audio/speech").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(body, MediaType.get("application/json")))
                .build();
    }

    private static byte[] readAll(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }
}
