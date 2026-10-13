package io.github.lnyocly.ai4j.platform.minimax.audio;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.config.MinimaxConfig;
import io.github.lnyocly.ai4j.exception.Ai4jException;
import io.github.lnyocly.ai4j.exception.AiClientException;
import io.github.lnyocly.ai4j.exception.HttpErrorDecoder;
import io.github.lnyocly.ai4j.network.UrlUtils;
import io.github.lnyocly.ai4j.platform.openai.audio.entity.TextToSpeech;
import io.github.lnyocly.ai4j.platform.openai.audio.entity.Transcription;
import io.github.lnyocly.ai4j.platform.openai.audio.entity.TranscriptionResponse;
import io.github.lnyocly.ai4j.platform.openai.audio.entity.Translation;
import io.github.lnyocly.ai4j.platform.openai.audio.entity.TranslationResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IAudioService;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Synchronous MiniMax speech synthesis ({@code POST v1/t2a_v2}). Set a MiniMax speech
 * model (e.g. {@code speech-2.8-hd}) and voice ID explicitly on the request.
 * The input stream contains decoded audio bytes.
 *
 * <p>MiniMax keys are region-bound: use the global gateway
 * ({@code https://api.minimax.io/}) with global keys and the CN gateway
 * ({@code https://api.minimax.cn/}) with CN keys, otherwise the API answers
 * {@code 1004 login fail}. The gateway follows {@code MinimaxConfig.getApiHost()}
 * unless a per-call {@code baseUrl} overrides it.
 *
 * <p>Supported audio {@code format} values are {@code mp3}, {@code pcm},
 * {@code flac}, {@code wav}, {@code pcmu_raw}, {@code pcmu_wav} and {@code opus};
 * {@code aac} is not accepted by MiniMax.
 */
public class MinimaxAudioService implements IAudioService {
    private static final MediaType JSON_TYPE = MediaType.get("application/json");
    private final MinimaxConfig config;
    private final OkHttpClient client;

    public MinimaxAudioService(Configuration configuration) {
        this.config = configuration.getMinimaxConfig();
        this.client = configuration.getOkHttpClient();
    }

    @Override
    public InputStream textToSpeech(TextToSpeech speech) {
        return textToSpeech(null, null, speech);
    }

    @Override
    public InputStream textToSpeech(String baseUrl, String apiKey, TextToSpeech speech) {
        String hex = synthesize(baseUrl, apiKey, speech, "hex");
        if (hex.length() % 2 != 0) {
            throw new AiClientException(200, "MiniMax speech response contains invalid hexadecimal audio");
        }
        byte[] audio = new byte[hex.length() / 2];
        for (int i = 0; i < audio.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new AiClientException(200, "MiniMax speech response contains invalid hexadecimal audio");
            }
            audio[i] = (byte) ((high << 4) | low);
        }
        return new ByteArrayInputStream(audio);
    }

    @Override
    public String textToSpeechUrl(TextToSpeech speech) {
        return textToSpeechUrl(null, null, speech);
    }

    @Override
    public String textToSpeechUrl(String baseUrl, String apiKey, TextToSpeech speech) {
        return synthesize(baseUrl, apiKey, speech, "url");
    }

    private String synthesize(String baseUrl, String apiKey, TextToSpeech speech, String outputFormat) {
        JSONObject body = new JSONObject();
        body.put("model", speech.getModel());
        body.put("text", speech.getInput());
        body.put("stream", false);
        body.put("output_format", outputFormat);
        JSONObject voice = new JSONObject();
        voice.put("voice_id", speech.getVoice());
        voice.put("speed", speech.getSpeed());
        body.put("voice_setting", voice);
        JSONObject audio = new JSONObject();
        audio.put("format", speech.getResponseFormat());
        body.put("audio_setting", audio);
        String host = baseUrl == null || baseUrl.isEmpty() ? config.getApiHost() : baseUrl;
        String key = apiKey == null || apiKey.isEmpty() ? config.getApiKey() : apiKey;
        Request request = new Request.Builder()
                .url(UrlUtils.concatUrl(host, "v1/t2a_v2"))
                .header("Authorization", "Bearer " + key)
                .post(RequestBody.create(body.toJSONString(), JSON_TYPE))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw HttpErrorDecoder.decode(response);
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new AiClientException(response.code(), "MiniMax speech response had no body");
            }
            JSONObject root = JSON.parseObject(responseBody.string());
            if (root == null) {
                throw new AiClientException(response.code(), "MiniMax speech response missing status code");
            }
            JSONObject status = root.getJSONObject("base_resp");
            if (status == null || status.getInteger("status_code") == null) {
                throw new AiClientException(response.code(), "MiniMax speech response missing status code");
            }
            if (status.getIntValue("status_code") != 0) {
                throw new AiClientException(response.code(), "MiniMax speech error "
                        + status.getInteger("status_code") + ": " + status.getString("status_msg"));
            }
            JSONObject data = root.getJSONObject("data");
            String result = data == null ? null : data.getString("audio");
            if (result == null || result.isEmpty()) {
                throw new AiClientException(response.code(), "MiniMax speech response missing audio");
            }
            return result;
        } catch (IOException e) {
            throw new Ai4jException("MiniMax speech request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public TranscriptionResponse transcription(String baseUrl, String apiKey, Transcription transcription) {
        throw new UnsupportedOperationException("MiniMax audio service supports speech synthesis only");
    }

    @Override
    public TranscriptionResponse transcription(Transcription transcription) {
        return transcription(null, null, transcription);
    }

    @Override
    public TranslationResponse translation(String baseUrl, String apiKey, Translation translation) {
        throw new UnsupportedOperationException("MiniMax audio service supports speech synthesis only");
    }

    @Override
    public TranslationResponse translation(Translation translation) {
        return translation(null, null, translation);
    }
}
