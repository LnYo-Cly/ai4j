package io.github.lnyocly.ai4j.service;

import io.github.lnyocly.ai4j.listener.RealtimeListener;
import okhttp3.WebSocket;

/**
 * @Author cly
 * @Description realtime服务接口
 * @Date 2024/10/12 16:30
 */
public interface IRealtimeService {
    WebSocket createRealtimeClient(String baseUrl, String apiKey, String model, RealtimeListener realtimeListener);
    WebSocket createRealtimeClient(String model, RealtimeListener realtimeListener);
}
