package io.github.lnyocly.ai4j.platform.typesafe.systemone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.TypeSafeConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.exception.HttpErrorDecoder;
import io.github.lnyocly.ai4j.network.UrlUtils;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.ISystemOneService;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneModelCard;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneRequest;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneResponse;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * TypeSafe AI System One service (Jev). Evaluates a state against typed
 * Choice/Score/Noul questions via {@code POST {apiHost}/{systemOneUrl}}.
 */
public class TypeSafeSystemOneService implements ISystemOneService {

    private final OkHttpClient okHttpClient;
    private final String apiHost;
    private final String apiKey;
    private final String systemOneUrl;
    private final String modelsUrl;
    private final String defaultModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TypeSafeSystemOneService(Configuration configuration) {
        this(configuration, configuration == null ? null : configuration.getTypeSafeConfig());
    }

    public TypeSafeSystemOneService(Configuration configuration, TypeSafeConfig config) {
        this(configuration == null ? null : configuration.getOkHttpClient(),
                config == null ? null : config.getApiHost(),
                config == null ? null : config.getApiKey(),
                config == null ? null : config.getSystemOneUrl(),
                config == null ? null : config.getModelsUrl(),
                config == null ? null : config.getDefaultModel());
    }

    public TypeSafeSystemOneService(OkHttpClient okHttpClient, String apiHost, String apiKey,
                                    String systemOneUrl, String modelsUrl, String defaultModel) {
        this.okHttpClient = okHttpClient;
        this.apiHost = apiHost;
        this.apiKey = apiKey;
        this.systemOneUrl = systemOneUrl;
        this.modelsUrl = modelsUrl;
        this.defaultModel = defaultModel;
    }

    @Override
    public SystemOneResponse evaluate(String baseUrl, String apiKey, SystemOneRequest request) throws Exception {
        if (request == null || request.getQuestions() == null || request.getQuestions().isEmpty()) {
            throw new IllegalArgumentException("SystemOne request requires at least one question");
        }
        if (StringUtils.isBlank(request.getModel())) {
            request.setModel(defaultModel);
        }
        Request.Builder builder = new Request.Builder()
                .url(UrlUtils.concatUrl(resolveApiHost(baseUrl), systemOneUrl))
                .post(RequestBody.create(objectMapper.writeValueAsString(request), MediaType.get(Constants.JSON_CONTENT_TYPE)));
        String key = resolveApiKey(apiKey);
        if (StringUtils.isNotBlank(key)) {
            builder.header("Authorization", "Bearer " + key);
        }
        try (okhttp3.Response response = okHttpClient.newCall(builder.build()).execute()) {
            okhttp3.ResponseBody body = response.body();
            if (response.isSuccessful() && body != null) {
                return objectMapper.readValue(body.string(), SystemOneResponse.class);
            }
            throw HttpErrorDecoder.decode(response);
        }
    }

    @Override
    public SystemOneResponse evaluate(SystemOneRequest request) throws Exception {
        return evaluate(null, null, request);
    }

    @Override
    public List<SystemOneModelCard> listModels() throws Exception {
        Request.Builder builder = new Request.Builder()
                .url(UrlUtils.concatUrl(apiHost, modelsUrl))
                .get();
        if (StringUtils.isNotBlank(apiKey)) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        try (okhttp3.Response response = okHttpClient.newCall(builder.build()).execute()) {
            okhttp3.ResponseBody body = response.body();
            if (response.isSuccessful() && body != null) {
                JsonNode root = objectMapper.readTree(body.string());
                JsonNode models = root.isArray() ? root : root.get("models");
                if (models == null || !models.isArray()) {
                    return Collections.emptyList();
                }
                return Arrays.asList(objectMapper.treeToValue(models, SystemOneModelCard[].class));
            }
            throw HttpErrorDecoder.decode(response);
        }
    }

    private String resolveApiHost(String baseUrl) {
        return StringUtils.isNotBlank(baseUrl) ? baseUrl : apiHost;
    }

    private String resolveApiKey(String override) {
        return StringUtils.isNotBlank(override) ? override : apiKey;
    }
}
