package io.github.lnyocly.ai4j.listener;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @Author cly
 * @Description RealtimeListener, 用于统一处理WebSocket的事件（Realtime客户端专用）
 * @Date 2024/10/12 16:33
 */
public class RealtimeListener extends WebSocketListener {
    private static final Logger log = Logger.getLogger(RealtimeListener.class);

    @Override
    public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
        log.info("WebSocket Opened: " + response.message());

    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
        log.info("Receive Byte Message: " + bytes.toString());
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
        log.info("Receive String Message: " + text);
    }

    @Override
    public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
        log.error("WebSocket Error: ", t);
    }

    @Override
    public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        super.onClosing(webSocket, code, reason);
    }

    @Override
    public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        super.onClosed(webSocket, code, reason);
    }
}
