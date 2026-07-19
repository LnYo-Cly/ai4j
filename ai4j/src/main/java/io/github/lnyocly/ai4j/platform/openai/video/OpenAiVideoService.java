package io.github.lnyocly.ai4j.platform.openai.video;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.network.UrlUtils;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoCreateRequest;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IVideoService;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI-compatible video service, including ChatFire's /v1/videos gateway.
 */
public class OpenAiVideoService implements IVideoService {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);
    private static final MediaType OCTET_STREAM_MEDIA_TYPE = MediaType.get("application/octet-stream");

    private final OpenAiConfig openAiConfig;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiVideoService(Configuration configuration) {
        this.openAiConfig = configuration.getOpenAiConfig();
        this.okHttpClient = configuration.getOkHttpClient();
    }

    @Override
    public VideoResponse create(String baseUrl, String apiKey, VideoCreateRequest request) throws Exception {
        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
        addFormDataPart(builder, "model", request.getModel());
        addFormDataPart(builder, "prompt", request.getPrompt());
        addFormDataPart(builder, "seconds", request.getSeconds());
        addFormDataPart(builder, "size", request.getSize());

        if (request.getExtraFields() != null) {
            for (Map.Entry<String, Object> entry : request.getExtraFields().entrySet()) {
                addFormDataPart(builder, entry.getKey(), entry.getValue());
            }
        }
        if (request.getFileFields() != null) {
            for (Map.Entry<String, File> entry : request.getFileFields().entrySet()) {
                File file = entry.getValue();
                if (file != null) {
                    builder.addFormDataPart(entry.getKey(), file.getName(), RequestBody.create(file, OCTET_STREAM_MEDIA_TYPE));
                }
            }
        }

        Request.Builder requestBuilder = authorizedRequest(baseUrl, apiKey, openAiConfig.getVideoUrl())
                .post(builder.build());
        if (request.getHeaders() != null) {
            for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                if (entry.getValue() != null) {
                    requestBuilder.header(entry.getKey(), entry.getValue());
                }
            }
        }
        return executeJson(requestBuilder.build());
    }

    @Override
    public VideoResponse create(VideoCreateRequest request) throws Exception {
        return create(null, null, request);
    }

    @Override
    public VideoResponse retrieve(String baseUrl, String apiKey, String id) throws Exception {
        Request request = authorizedRequest(baseUrl, apiKey, openAiConfig.getVideoUrl(), encodePathSegment(id))
                .get()
                .build();
        return executeJson(request);
    }

    @Override
    public VideoResponse retrieve(String id) throws Exception {
        return retrieve(null, null, id);
    }

    @Override
    public InputStream content(String baseUrl, String apiKey, String id) throws Exception {
        Request request = authorizedRequest(baseUrl, apiKey, openAiConfig.getVideoUrl(), encodePathSegment(id), "content")
                .get()
                .build();
        Response response = okHttpClient.newCall(request).execute();
        if (!response.isSuccessful() || response.body() == null) {
            String message = errorMessage(response);
            response.close();
            throw new CommonException(message);
        }
        return new ResponseInputStream(response, response.body().byteStream());
    }

    @Override
    public InputStream content(String id) throws Exception {
        return content(null, null, id);
    }

    @Override
    public VideoResponse remix(String baseUrl, String apiKey, String id, String prompt) throws Exception {
        Map<String, String> body = new LinkedHashMap<String, String>();
        body.put("prompt", prompt);
        Request request = authorizedRequest(baseUrl, apiKey, openAiConfig.getVideoUrl(), encodePathSegment(id), "remix")
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON_MEDIA_TYPE))
                .build();
        return executeJson(request);
    }

    @Override
    public VideoResponse remix(String id, String prompt) throws Exception {
        return remix(null, null, id, prompt);
    }

    private Request.Builder authorizedRequest(String baseUrl, String apiKey, String... pathParts) {
        String[] parts = new String[pathParts.length + 1];
        parts[0] = resolveBaseUrl(baseUrl);
        System.arraycopy(pathParts, 0, parts, 1, pathParts.length);
        return new Request.Builder()
                .header("Authorization", "Bearer " + resolveApiKey(apiKey))
                .url(UrlUtils.concatUrl(parts));
    }

    private VideoResponse executeJson(Request request) throws Exception {
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                VideoResponse videoResponse = mapper.readValue(body, VideoResponse.class);
                videoResponse.setRaw(mapper.readValue(body, new TypeReference<Map<String, Object>>() { }));
                return videoResponse;
            }
            throw new CommonException(errorMessage(response));
        }
    }

    private void addFormDataPart(MultipartBody.Builder builder, String name, Object value) {
        if (name != null && value != null) {
            builder.addFormDataPart(name, String.valueOf(value));
        }
    }

    private String resolveBaseUrl(String baseUrl) {
        return (baseUrl == null || "".equals(baseUrl)) ? openAiConfig.getApiHost() : baseUrl;
    }

    private String resolveApiKey(String apiKey) {
        return (apiKey == null || "".equals(apiKey)) ? openAiConfig.getApiKey() : apiKey;
    }

    private String encodePathSegment(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }

    private String errorMessage(Response response) throws IOException {
        ResponseBody body = response.body();
        String detail = body == null ? "" : body.string();
        return "OpenAI video request failed: HTTP " + response.code() + (detail.length() == 0 ? "" : " - " + detail);
    }

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
