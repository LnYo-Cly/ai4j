package io.github.lnyocly.ai4j.platform.openai.audio;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.platform.openai.audio.entity.TextToSpeech;
import io.github.lnyocly.ai4j.platform.openai.audio.entity.Transcription;
import io.github.lnyocly.ai4j.platform.openai.audio.entity.TranscriptionResponse;
import io.github.lnyocly.ai4j.platform.openai.audio.entity.Translation;
import io.github.lnyocly.ai4j.platform.openai.audio.entity.TranslationResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IAudioService;
import io.github.lnyocly.ai4j.network.UrlUtils;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;

import java.io.FilterInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @Author cly
 * @Description OpenAi音频服务
 * @Date 2024/10/10 23:36
 */
public class OpenAiAudioService implements IAudioService {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);
    private static final MediaType OCTET_STREAM_MEDIA_TYPE = MediaType.get("application/octet-stream");

    private final OpenAiConfig openAiConfig;
    private final OkHttpClient okHttpClient;

    public OpenAiAudioService(Configuration configuration) {
        this.openAiConfig = configuration.getOpenAiConfig();
        this.okHttpClient = configuration.getOkHttpClient();
    }

    public OpenAiAudioService(Configuration configuration, OpenAiConfig openAiConfig) {
        this.openAiConfig = openAiConfig;
        this.okHttpClient = configuration.getOkHttpClient();
    }


    @Override
    public InputStream textToSpeech(String baseUrl, String apiKey, TextToSpeech textToSpeech) {
        String requestString = JSON.toJSONString(textToSpeech);
        Request request = buildAuthorizedRequest(
                baseUrl,
                apiKey,
                openAiConfig.getSpeechUrl(),
                RequestBody.create(requestString, JSON_MEDIA_TYPE)
        );

        Response response = null;
        try {
            response = okHttpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            ResponseBody responseBody = response.body();
            if (responseBody != null) {
                return new ResponseInputStream(response, responseBody.byteStream());
            }
        } catch (IOException e) {
            closeQuietly(response);
            e.printStackTrace();
        }
        closeQuietly(response);
        return null;
    }

    @Override
    public InputStream textToSpeech(TextToSpeech textToSpeech) {
        return this.textToSpeech(null, null, textToSpeech);
    }

    @Override
    public TranscriptionResponse transcription(String baseUrl, String apiKey, Transcription transcription) {
        MultipartBody.Builder builder = newAudioMultipartBuilder(
                transcription.getFile(),
                transcription.getModel(),
                transcription.getTemperature()
        );
        if(StringUtils.isNotBlank(transcription.getLanguage())){
            builder.addFormDataPart("language", transcription.getLanguage());
        }
        if(StringUtils.isNotBlank(transcription.getPrompt())){
            builder.addFormDataPart("prompt", transcription.getPrompt());
        }
        if(StringUtils.isNotBlank(transcription.getResponseFormat())){
            builder.addFormDataPart("response_format", transcription.getResponseFormat());
        }

        return executeJsonRequest(
                buildAuthorizedRequest(baseUrl, apiKey, openAiConfig.getTranscriptionUrl(), builder.build()),
                TranscriptionResponse.class
        );
    }

    @Override
    public TranscriptionResponse transcription(Transcription transcription) {
        return this.transcription(null, null, transcription);
    }

    @Override
    public TranslationResponse translation(String baseUrl, String apiKey, Translation translation) {
        MultipartBody.Builder builder = newAudioMultipartBuilder(
                translation.getFile(),
                translation.getModel(),
                translation.getTemperature()
        );
        if(StringUtils.isNotBlank(translation.getPrompt())){
            builder.addFormDataPart("prompt", translation.getPrompt());
        }
        if(StringUtils.isNotBlank(translation.getResponseFormat())){
            builder.addFormDataPart("response_format", translation.getResponseFormat());
        }

        return executeJsonRequest(
                buildAuthorizedRequest(baseUrl, apiKey, openAiConfig.getTranslationUrl(), builder.build()),
                TranslationResponse.class
        );
    }

    @Override
    public TranslationResponse translation(Translation translation) {
        return this.translation(null, null, translation);
    }

    private Request buildAuthorizedRequest(String baseUrl, String apiKey, String path, RequestBody requestBody) {
        return new Request.Builder()
                .header("Authorization", "Bearer " + resolveApiKey(apiKey))
                .url(UrlUtils.concatUrl(resolveBaseUrl(baseUrl), path))
                .post(requestBody)
                .build();
    }

    private MultipartBody.Builder newAudioMultipartBuilder(File file, String model, Object temperature) {
        return new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), RequestBody.create(file, OCTET_STREAM_MEDIA_TYPE))
                .addFormDataPart("model", model)
                .addFormDataPart("temperature", String.valueOf(temperature));
    }

    private <T> T executeJsonRequest(Request request, Class<T> responseType) {
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return JSON.parseObject(response.body().string(), responseType);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String resolveBaseUrl(String baseUrl) {
        return (baseUrl == null || "".equals(baseUrl)) ? openAiConfig.getApiHost() : baseUrl;
    }

    private String resolveApiKey(String apiKey) {
        return (apiKey == null || "".equals(apiKey)) ? openAiConfig.getApiKey() : apiKey;
    }

    private static void closeQuietly(Response response) {
        if (response != null) {
            response.close();
        }
    }

    /**
     * Keep the HTTP response open until the caller finishes consuming the stream.
     */
    private static final class ResponseInputStream extends FilterInputStream {
        private final Response response;

        private ResponseInputStream(Response response, InputStream delegate) {
            super(delegate);
            this.response = response;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                response.close();
            }
        }
    }
}

