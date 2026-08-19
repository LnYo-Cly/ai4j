package io.github.lnyocly.ai4j.platform.openai.video;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.exception.HttpErrorDecoder;
import io.github.lnyocly.ai4j.network.UrlUtils;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoCreateRequest;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IVideoService;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared HTTP plumbing for video services.
 *
 * <p>Every first-party video API we target creates tasks with {@code application/json} and
 * polls a task id, but they disagree on paths and parameter names. Subclasses therefore
 * only declare their dialect: which create path to use and how to name the neutral fields
 * of {@link VideoCreateRequest}.
 */
public abstract class AbstractVideoService implements IVideoService {

    protected static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);

    protected final OpenAiConfig openAiConfig;
    protected final OkHttpClient okHttpClient;
    protected final ObjectMapper mapper = new ObjectMapper();

    protected AbstractVideoService(Configuration configuration) {
        this.openAiConfig = configuration.getOpenAiConfig();
        this.okHttpClient = configuration.getOkHttpClient();
    }

    /** Default create path for this dialect, e.g. {@code v1/videos}. */
    protected abstract String defaultCreatePath();

    /** Maps the neutral request onto this dialect's JSON body. */
    protected abstract Map<String, Object> toJsonBody(VideoCreateRequest request);

    @Override
    public VideoResponse create(String baseUrl, String apiKey, VideoCreateRequest request) throws Exception {
        String path = request.getCreatePath() != null && !request.getCreatePath().isEmpty()
                ? request.getCreatePath()
                : defaultCreatePath();
        Request.Builder requestBuilder = authorizedRequest(baseUrl, apiKey, path).post(jsonBody(request));
        applyHeaders(requestBuilder, request);
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
            try {
                throw HttpErrorDecoder.decode(response);
            } finally {
                response.close();
            }
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

    private RequestBody jsonBody(VideoCreateRequest request) throws Exception {
        Map<String, Object> body = toJsonBody(request);
        if (request.getExtraFields() != null) {
            for (Map.Entry<String, Object> entry : request.getExtraFields().entrySet()) {
                if (entry.getValue() != null) {
                    body.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return RequestBody.create(mapper.writeValueAsString(body), JSON_MEDIA_TYPE);
    }

    protected static void putIfPresent(Map<String, Object> body, String name, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String && ((String) value).isEmpty()) {
            return;
        }
        body.put(name, value);
    }

    private void applyHeaders(Request.Builder requestBuilder, VideoCreateRequest request) {
        if (request.getHeaders() == null) {
            return;
        }
        for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
            if (entry.getValue() != null) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }
        }
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
            throw HttpErrorDecoder.decode(response);
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
