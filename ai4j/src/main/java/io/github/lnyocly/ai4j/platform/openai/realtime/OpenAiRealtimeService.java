package io.github.lnyocly.ai4j.platform.openai.realtime;

import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.listener.RealtimeListener;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IRealtimeService;
import io.github.lnyocly.ai4j.utils.ValidateUtil;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;

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
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = openAiConfig.getApiHost(); // url为HTTPS不影响
        if(apiKey == null || "".equals(apiKey)) apiKey = openAiConfig.getApiKey();

        String url = ValidateUtil.concatUrl(baseUrl, openAiConfig.getRealtimeUrl(), "?model=" + model);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("OpenAI-Beta", "realtime=v1")
                .build();
        return okHttpClient.newWebSocket(request, realtimeListener);

    }

    @Override
    public WebSocket createRealtimeClient(String model, RealtimeListener realtimeListener) {
        return this.createRealtimeClient(null, null, model, realtimeListener);
    }
}
