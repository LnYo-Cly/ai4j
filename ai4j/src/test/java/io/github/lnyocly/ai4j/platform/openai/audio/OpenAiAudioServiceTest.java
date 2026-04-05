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
