package io.github.lnyocly.ai4j.platform.openai.realtime;

import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.listener.RealtimeListener;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IRealtimeService;
import io.github.lnyocly.ai4j.utils.ValidateUtil;
import okhttp3.*;
import okhttp3.sse.EventSource;

/**
 * @Author cly
 * @Description OpenAiRealtimeService
 * @Date 2024/10/12 16:39
 */
public class OpenAiRealtimeService implements IRealtimeService {
    private final OpenAiConfig openAiConfig;
    private final OkHttpClient okHttpClient;

    public OpenAiRealtimeService(Configuration configuration) {
        this.openAiConfig = configuration.getOpenAiConfig();
        this.okHttpClient = configuration.getOkHttpClient();
    }


    @Override
    public WebSocket createRealtimeClient(String baseUrl, String apiKey, String model, RealtimeListener realtimeListener) {
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = openAiConfig.getWssHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = openAiConfig.getApiKey();

        Request request = new Request.Builder()
                .header("Authorization", "Bearer " + apiKey)
                .header("OpenAI-Beta", "realtime=v1")
                .url(ValidateUtil.concatUrl(baseUrl, openAiConfig.getRealtimeUrl(), "?model=" + model))
                .build();

        return okHttpClient.newWebSocket(request, realtimeListener);

    }

    @Override
    public WebSocket createRealtimeClient(String model, RealtimeListener realtimeListener) {
        return this.createRealtimeClient(null, null, model, realtimeListener);
    }
}
