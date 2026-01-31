package io.github.lnyocly.ai4j.platform.doubao.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.DoubaoConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.listener.ImageSseListener;
import io.github.lnyocly.ai4j.platform.doubao.image.entity.DoubaoImageGenerationRequest;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageGeneration;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageGenerationResponse;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageStreamError;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageStreamEvent;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageUsage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IImageService;
import io.github.lnyocly.ai4j.utils.ValidateUtil;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @Author cly
 * @Description 豆包图片生成服务
 * @Date 2026/1/31
 */
public class DoubaoImageService implements IImageService {

    private final DoubaoConfig doubaoConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;

    public DoubaoImageService(Configuration configuration) {
        this.doubaoConfig = configuration.getDoubaoConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = EventSources.createFactory(okHttpClient);
    }

    private DoubaoImageGenerationRequest convert(ImageGeneration imageGeneration) {
        return DoubaoImageGenerationRequest.builder()
                .model(imageGeneration.getModel())
                .prompt(imageGeneration.getPrompt())
                .n(imageGeneration.getN())
                .size(imageGeneration.getSize())
                .responseFormat(imageGeneration.getResponseFormat())
                .stream(imageGeneration.getStream())
                .extraBody(imageGeneration.getExtraBody())
                .build();
    }

    @Override
    public ImageGenerationResponse generate(String baseUrl, String apiKey, ImageGeneration imageGeneration) throws Exception {
        if (baseUrl == null || "".equals(baseUrl)) {
            baseUrl = doubaoConfig.getApiHost();
        }
        if (apiKey == null || "".equals(apiKey)) {
            apiKey = doubaoConfig.getApiKey();
        }

        DoubaoImageGenerationRequest requestBody = convert(imageGeneration);

        ObjectMapper mapper = new ObjectMapper();
        String requestString = mapper.writeValueAsString(requestBody);

        Request request = new Request.Builder()
                .header("Authorization", "Bearer " + apiKey)
                .url(ValidateUtil.concatUrl(baseUrl, doubaoConfig.getImageGenerationUrl()))
                .post(RequestBody.create(MediaType.parse(Constants.JSON_CONTENT_TYPE), requestString))
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return mapper.readValue(response.body().string(), ImageGenerationResponse.class);
            }
        }

        throw new CommonException("豆包图片生成请求失败");
    }

    @Override
    public ImageGenerationResponse generate(ImageGeneration imageGeneration) throws Exception {
        return this.generate(null, null, imageGeneration);
    }

    @Override
    public void generateStream(String baseUrl, String apiKey, ImageGeneration imageGeneration, ImageSseListener listener) throws Exception {
        if (baseUrl == null || "".equals(baseUrl)) {
            baseUrl = doubaoConfig.getApiHost();
        }
        if (apiKey == null || "".equals(apiKey)) {
            apiKey = doubaoConfig.getApiKey();
        }
        if (imageGeneration.getStream() == null || !imageGeneration.getStream()) {
            imageGeneration.setStream(true);
        }

        DoubaoImageGenerationRequest requestBody = convert(imageGeneration);
        ObjectMapper mapper = new ObjectMapper();
        String requestString = mapper.writeValueAsString(requestBody);

        Request request = new Request.Builder()
                .header("Authorization", "Bearer " + apiKey)
                .url(ValidateUtil.concatUrl(baseUrl, doubaoConfig.getImageGenerationUrl()))
                .post(RequestBody.create(MediaType.parse(Constants.JSON_CONTENT_TYPE), requestString))
                .build();

        factory.newEventSource(request, convertEventSource(mapper, listener));
        listener.getCountDownLatch().await();
    }

    @Override
    public void generateStream(ImageGeneration imageGeneration, ImageSseListener listener) throws Exception {
        this.generateStream(null, null, imageGeneration, listener);
    }

    private EventSourceListener convertEventSource(ObjectMapper mapper, ImageSseListener listener) {
        return new EventSourceListener() {
            @Override
            public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
                // no-op
            }

            @Override
            public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                listener.onError(t, response);
                listener.complete();
            }

            @Override
            public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
                if ("[DONE]".equalsIgnoreCase(data)) {
                    listener.complete();
                    return;
                }
                try {
                    ImageStreamEvent event = parseDoubaoEvent(mapper, data);
                    listener.accept(event);

                    if ("image_generation.completed".equals(event.getType())) {
                        listener.complete();
                    }
                } catch (Exception e) {
                    listener.onError(e, null);
                    listener.complete();
                }
            }

            @Override
            public void onClosed(@NotNull EventSource eventSource) {
                listener.complete();
            }
        };
    }

    private ImageStreamEvent parseDoubaoEvent(ObjectMapper mapper, String data) throws Exception {
        JsonNode node = mapper.readTree(data);
        ImageStreamEvent event = new ImageStreamEvent();
        event.setType(asText(node, "type"));
        event.setModel(asText(node, "model"));
        Long createdAt = asLong(node, "created");
        if (createdAt == null) {
            createdAt = asLong(node, "created_at");
        }
        event.setCreatedAt(createdAt);
        event.setImageIndex(asInt(node, "image_index"));
        event.setPartialImageIndex(asInt(node, "image_index"));
        event.setUrl(asText(node, "url"));
        event.setB64Json(asText(node, "b64_json"));
        event.setSize(asText(node, "size"));
        if (node.has("usage")) {
            event.setUsage(mapper.treeToValue(node.get("usage"), ImageUsage.class));
        }
        if (node.has("error")) {
            event.setError(mapper.treeToValue(node.get("error"), ImageStreamError.class));
        }
        return event;
    }

    private String asText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Integer asInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private Long asLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }
}
