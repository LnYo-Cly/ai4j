package io.github.lnyocly.ai4j.platform.suno.music;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.SunoConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.network.UrlUtils;
import io.github.lnyocly.ai4j.platform.suno.music.entity.SunoFetchResponse;
import io.github.lnyocly.ai4j.platform.suno.music.entity.SunoLyricsRequest;
import io.github.lnyocly.ai4j.platform.suno.music.entity.SunoMusicRequest;
import io.github.lnyocly.ai4j.platform.suno.music.entity.SunoSubmitResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IMusicService;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;

/**
 * ChatFire Suno native music generation service.
 */
public class SunoMusicService implements IMusicService {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);

    private final SunoConfig sunoConfig;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public SunoMusicService(Configuration configuration) {
        this.sunoConfig = configuration.getSunoConfig() == null ? new SunoConfig() : configuration.getSunoConfig();
        this.okHttpClient = configuration.getOkHttpClient() == null ? new OkHttpClient() : configuration.getOkHttpClient();
    }

    @Override
    public SunoSubmitResponse submitMusic(String baseUrl, String apiKey, SunoMusicRequest request) throws Exception {
        Request httpRequest = authorizedRequest(baseUrl, apiKey, sunoConfig.getMusicUrl())
                .post(jsonBody(request))
                .build();
        return executeJson(httpRequest, SunoSubmitResponse.class);
    }

    @Override
    public SunoSubmitResponse submitMusic(SunoMusicRequest request) throws Exception {
        return submitMusic(null, null, request);
    }

    @Override
    public SunoSubmitResponse submitLyrics(String baseUrl, String apiKey, SunoLyricsRequest request) throws Exception {
        Request httpRequest = authorizedRequest(baseUrl, apiKey, sunoConfig.getLyricsUrl())
                .post(jsonBody(request))
                .build();
        return executeJson(httpRequest, SunoSubmitResponse.class);
    }

    @Override
    public SunoSubmitResponse submitLyrics(SunoLyricsRequest request) throws Exception {
        return submitLyrics(null, null, request);
    }

    @Override
    public SunoFetchResponse fetch(String baseUrl, String apiKey, String taskId) throws Exception {
        Request httpRequest = authorizedRequest(baseUrl, apiKey, fetchEndpoint(taskId))
                .get()
                .build();
        return executeJson(httpRequest, SunoFetchResponse.class);
    }

    @Override
    public SunoFetchResponse fetch(String taskId) throws Exception {
        return fetch(null, null, taskId);
    }

    private RequestBody jsonBody(Object body) throws IOException {
        return RequestBody.create(mapper.writeValueAsString(body), JSON_MEDIA_TYPE);
    }

    private Request.Builder authorizedRequest(String baseUrl, String apiKey, String endpoint) {
        return new Request.Builder()
                .header("Authorization", "Bearer " + resolveApiKey(apiKey))
                .url(resolveUrl(baseUrl, endpoint));
    }

    private String fetchEndpoint(String taskId) throws IOException {
        String fetchUrl = sunoConfig.getFetchUrl();
        String encodedTaskId = encodePathSegment(taskId);
        if (fetchUrl != null && fetchUrl.contains("{task_id}")) {
            return fetchUrl.replace("{task_id}", encodedTaskId);
        }
        return UrlUtils.concatUrl(fetchUrl, encodedTaskId);
    }

    private String resolveUrl(String baseUrl, String endpoint) {
        if (endpoint != null && (endpoint.startsWith("http://") || endpoint.startsWith("https://"))) {
            return endpoint;
        }
        return UrlUtils.concatUrl(resolveBaseUrl(baseUrl), endpoint);
    }

    private String resolveBaseUrl(String baseUrl) {
        return (baseUrl == null || "".equals(baseUrl)) ? sunoConfig.getApiHost() : baseUrl;
    }

    private String resolveApiKey(String apiKey) {
        return (apiKey == null || "".equals(apiKey)) ? sunoConfig.getApiKey() : apiKey;
    }

    private String encodePathSegment(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }

    private <T> T executeJson(Request request, Class<T> type) throws Exception {
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                T parsed = mapper.readValue(body, type);
                Map<String, Object> raw = mapper.readValue(body, new TypeReference<Map<String, Object>>() { });
                if (parsed instanceof SunoSubmitResponse) {
                    ((SunoSubmitResponse) parsed).setRaw(raw);
                } else if (parsed instanceof SunoFetchResponse) {
                    ((SunoFetchResponse) parsed).setRaw(raw);
                }
                return parsed;
            }
            throw new CommonException(errorMessage(response));
        }
    }

    private String errorMessage(Response response) throws IOException {
        ResponseBody body = response.body();
        String detail = body == null ? "" : body.string();
        return "Suno request failed: HTTP " + response.code() + (detail.length() == 0 ? "" : " - " + detail);
    }
}
