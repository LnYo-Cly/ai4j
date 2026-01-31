package io.github.lnyocly.ai4j.platform.openai.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.listener.ImageSseListener;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageGeneration;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageGenerationResponse;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageStreamEvent;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageStreamError;
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
 * @Description OpenAI 图片生成服务
 * @Date 2026/1/31
 */
public class OpenAiImageService implements IImageService {

    private final OpenAiConfig openAiConfig;
    private final OkHttpClient okHttpClient;
    private final EventSource.Factory factory;

    public OpenAiImageService(Configuration configuration) {
        this.openAiConfig = configuration.getOpenAiConfig();
        this.okHttpClient = configuration.getOkHttpClient();
        this.factory = EventSources.createFactory(okHttpClient);
    }

    @Override
    public ImageGenerationResponse generate(String baseUrl, String apiKey, ImageGeneration imageGeneration) throws Exception {
        if (baseUrl == null || "".equals(baseUrl)) {
            baseUrl = openAiConfig.getApiHost();
        }
        if (apiKey == null || "".equals(apiKey)) {
            apiKey = openAiConfig.getApiKey();
        }

        ObjectMapper mapper = new ObjectMapper();
        String requestString = mapper.writeValueAsString(imageGeneration);

        Request request = new Request.Builder()
                .header("Authorization", "Bearer " + apiKey)
                .url(ValidateUtil.concatUrl(baseUrl, openAiConfig.getImageGenerationUrl()))
                .post(RequestBody.create(MediaType.parse(Constants.JSON_CONTENT_TYPE), requestString))
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return mapper.readValue(response.body().string(), ImageGenerationResponse.class);
            }
        }

        throw new CommonException("OpenAI 图片生成请求失败");
    }

    @Override
    public ImageGenerationResponse generate(ImageGeneration imageGeneration) throws Exception {
        return this.generate(null, null, imageGeneration);
    }

    @Override
    public void generateStream(String baseUrl, String apiKey, ImageGeneration imageGeneration, ImageSseListener listener) throws Exception {
        if (baseUrl == null || "".equals(baseUrl)) {
            baseUrl = openAiConfig.getApiHost();
        }
        if (apiKey == null || "".equals(apiKey)) {
            apiKey = openAiConfig.getApiKey();
        }
        if (imageGeneration.getStream() == null || !imageGeneration.getStream()) {
            imageGeneration.setStream(true);
        }

        ObjectMapper mapper = new ObjectMapper();
        String requestString = mapper.writeValueAsString(imageGeneration);

        Request request = new Request.Builder()
                .header("Authorization", "Bearer " + apiKey)
                .url(ValidateUtil.concatUrl(baseUrl, openAiConfig.getImageGenerationUrl()))
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
                    ImageStreamEvent event = parseOpenAiEvent(mapper, data);
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

    private ImageStreamEvent parseOpenAiEvent(ObjectMapper mapper, String data) throws Exception {
        JsonNode node = mapper.readTree(data);
        ImageStreamEvent event = new ImageStreamEvent();
        event.setType(asText(node, "type"));
        event.setModel(asText(node, "model"));
        Long createdAt = asLong(node, "created_at");
        if (createdAt == null) {
            createdAt = asLong(node, "created");
        }
        event.setCreatedAt(createdAt);
        event.setPartialImageIndex(asInt(node, "partial_image_index"));
        event.setImageIndex(asInt(node, "image_index"));
        event.setUrl(asText(node, "url"));
        String b64 = asText(node, "b64_json");
        if (b64 == null) {
            b64 = asText(node, "partial_image_b64");
        }
        event.setB64Json(b64);
        event.setSize(asText(node, "size"));
        event.setQuality(asText(node, "quality"));
        event.setBackground(asText(node, "background"));
        event.setOutputFormat(asText(node, "output_format"));
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
