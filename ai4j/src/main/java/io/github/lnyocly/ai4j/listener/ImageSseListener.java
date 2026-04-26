package io.github.lnyocly.ai4j.listener;

import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageData;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageGenerationResponse;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageStreamEvent;
import lombok.Getter;
import okhttp3.Response;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * @Author cly
 * @Description 图片生成流式监听器
 * @Date 2026/1/31
 */
public abstract class ImageSseListener extends EventSourceListener {

    /**
     * 异常回调
     */
    protected void error(Throwable t, Response response) {}

    /**
     * 事件回调
     */
    protected abstract void onEvent();

    @Getter
    private final List<ImageStreamEvent> events = new ArrayList<>();

    @Getter
    private ImageStreamEvent currEvent;

    @Getter
    private final ImageGenerationResponse response = new ImageGenerationResponse();

    @Getter
    private CountDownLatch countDownLatch = new CountDownLatch(1);

    public void accept(ImageStreamEvent event) {
        this.currEvent = event;
        this.events.add(event);
        appendResponse(event);
        this.onEvent();
    }

    private void appendResponse(ImageStreamEvent event) {
        if (event == null) {
            return;
        }
        if (event.getUsage() != null) {
            response.setUsage(event.getUsage());
        }
        if (event.getCreatedAt() != null && response.getCreated() == null) {
            response.setCreated(event.getCreatedAt());
        }
        if (shouldAppendImage(event)) {
            if (response.getData() == null) {
                response.setData(new ArrayList<>());
            }
            ImageData imageData = new ImageData();
            imageData.setUrl(event.getUrl());
            imageData.setB64Json(event.getB64Json());
            imageData.setSize(event.getSize());
            response.getData().add(imageData);
        }
    }

    private boolean shouldAppendImage(ImageStreamEvent event) {
        if (event == null) {
            return false;
        }
        if (event.getUrl() == null && event.getB64Json() == null) {
            return false;
        }
        String type = event.getType();
        return type == null || !type.contains("partial_image");
    }

    public void complete() {
        countDownLatch.countDown();
        countDownLatch = new CountDownLatch(1);
    }

    public void onError(Throwable t, Response response) {
        this.error(t, response);
    }

    @Override
    public void onFailure(@NotNull okhttp3.sse.EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
        this.error(t, response);
        complete();
    }
}
