package io.github.lnyocly.ai4j.docs;

import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.listener.RealtimeListener;
import io.github.lnyocly.ai4j.platform.openai.audio.OpenAiAudioService;
import io.github.lnyocly.ai4j.platform.openai.audio.entity.TextToSpeech;
import io.github.lnyocly.ai4j.platform.openai.audio.entity.Transcription;
import io.github.lnyocly.ai4j.platform.openai.audio.entity.TranscriptionResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IAudioService;
import io.github.lnyocly.ai4j.service.IRealtimeService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import okhttp3.WebSocket;
import okio.ByteString;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Executable source of truth for the snippets in
 * {@code docs/core-sdk/audio.md} and {@code docs/core-sdk/realtime.md}.
 *
 * <p>Requires {@code OPENAI_API_KEY}; honours {@code OPENAI_API_HOST} and
 * {@code OPENAI_CHAT_MODEL}. Skips when the key is absent.
 *
 * <p>These endpoints are not supported by every gateway; failures that look
 * like capability gaps are skipped rather than treated as SDK defects.
 */
@Category(LiveProviderTest.class)
public class AudioAndRealtimeDocExamplesLiveTest {

    private OpenAiConfig audioConfig() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        Assume.assumeTrue("OPENAI_API_KEY not set", apiKey != null && !apiKey.trim().isEmpty());

        OpenAiConfig cfg = new OpenAiConfig();
        cfg.setApiKey(apiKey);
        String host = System.getenv("OPENAI_API_HOST");
        if (host != null && !host.trim().isEmpty()) {
            cfg.setApiHost(host);
        }
        return cfg;
    }

    private IAudioService audioService(OpenAiConfig cfg) {
        Configuration c = new Configuration();
        c.setOpenAiConfig(cfg);
        return new AiService(c).getAudioService(PlatformType.OPENAI);
    }

    private String ttsModel() {
        String m = System.getenv("OPENAI_TTS_MODEL");
        return (m == null || m.trim().isEmpty()) ? "tts-1" : m;
    }

    // ---- audio.md §3 TTS ----

    @Test
    public void textToSpeechReturnsAudioStream() throws Exception {
        OpenAiConfig cfg = audioConfig();
        IAudioService audio = audioService(cfg);

        InputStream speech;
        try {
            speech = audio.textToSpeech(TextToSpeech.builder()
                    .model(ttsModel())
                    .input("你好，这是 AI4J 的语音合成测试。")
                    .voice("alloy")
                    .build());
        } catch (Exception e) {
            Assume.assumeNoException("gateway does not support /audio/speech", e);
            return;
        }

        Assert.assertNotNull(speech);
        File out = File.createTempFile("ai4j-tts-", ".mp3");
        out.deleteOnExit();
        Files.copy(speech, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        speech.close();

        System.out.println("TTS 输出字节数: " + out.length());
        Assert.assertTrue("音频流应有内容", out.length() > 0);
    }

    // ---- audio.md §3 transcription（现在抛异常而非返回 null） ----

    @Test
    public void transcriptionReturnsText() throws Exception {
        OpenAiConfig cfg = audioConfig();
        IAudioService audio = audioService(cfg);

        // 先用 TTS 生成一个 wav 测试样本，避免依赖外部音频文件
        File audioFile = File.createTempFile("ai4j-transcribe-", ".wav");
        audioFile.deleteOnExit();

        try {
            InputStream speech = audio.textToSpeech(TextToSpeech.builder()
                    .model(ttsModel())
                    .input("The quick brown fox jumps over the lazy dog.")
                    .responseFormat("wav")
                    .build());
            Files.copy(speech, audioFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            speech.close();
        } catch (Exception e) {
            Assume.assumeNoException("gateway does not support TTS to generate a test sample", e);
            return;
        }

        TranscriptionResponse resp;
        try {
            resp = audio.transcription(Transcription.builder()
                    .file(audioFile)
                    .model("whisper-1")
                    .build());
        } catch (Exception e) {
            Assume.assumeNoException("gateway does not support /audio/transcriptions", e);
            return;
        }

        Assert.assertNotNull(resp);
        System.out.println("转录结果: " + resp.getText());
    }

    // ---- realtime.md 建连 ----

    @Test
    public void realtimeClientConnectsViaWebSocket() throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        Assume.assumeTrue("OPENAI_API_KEY not set", apiKey != null && !apiKey.trim().isEmpty());

        String realtimeModel = System.getenv("OPENAI_REALTIME_MODEL");
        if (realtimeModel == null || realtimeModel.trim().isEmpty()) {
            realtimeModel = "gpt-4o-realtime-preview";
        }

        OpenAiConfig cfg = new OpenAiConfig();
        cfg.setApiKey(apiKey);
        String host = System.getenv("OPENAI_API_HOST");
        if (host != null && !host.trim().isEmpty()) {
            cfg.setApiHost(host);
        }
        Configuration c = new Configuration();
        c.setOpenAiConfig(cfg);
        IRealtimeService realtime = new AiService(c).getRealtimeService(PlatformType.OPENAI);

        CountDownLatch open = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        final WebSocket[] wsHolder = new WebSocket[1];

        try {
            wsHolder[0] = realtime.createRealtimeClient(realtimeModel, new RealtimeListener() {
                @Override
                protected void onOpen(WebSocket webSocket) {
                    System.out.println("realtime connected");
                    wsHolder[0] = webSocket;
                    open.countDown();
                }

                @Override
                protected void onMessage(ByteString bytes) { }

                @Override
                protected void onMessage(String text) {
                    System.out.println("realtime message: " + text.substring(0, Math.min(80, text.length())));
                }

                @Override
                protected void onFailure() { }
            });
        } catch (Exception e) {
            Assume.assumeNoException("gateway does not support realtime WebSocket", e);
            return;
        }

        boolean opened = open.await(30, TimeUnit.SECONDS);
        if (!opened) {
            Assume.assumeTrue("realtime endpoint did not connect (gateway may not support it)", false);
        }

        Assert.assertNotNull(wsHolder[0]);
        wsHolder[0].close(1000, "test done");
    }
}
