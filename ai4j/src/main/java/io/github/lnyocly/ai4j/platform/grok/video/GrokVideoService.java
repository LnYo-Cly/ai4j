package io.github.lnyocly.ai4j.platform.grok.video;

import io.github.lnyocly.ai4j.platform.openai.video.AbstractVideoService;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoCreateRequest;
import io.github.lnyocly.ai4j.service.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Video service for the Grok / xAI gateway.
 *
 * <p>Dialect differences from {@link io.github.lnyocly.ai4j.platform.openai.video.OpenAiVideoService}:
 * tasks are created at {@code v1/videos/generations} (while polling stays on
 * {@code v1/videos/{request_id}}) and the duration / ratio / reference parameters use
 * {@code duration}, {@code aspect_ratio}, {@code resolution}, {@code image} and
 * {@code reference_images}.
 */
public class GrokVideoService extends AbstractVideoService {

    public static final String DEFAULT_CREATE_PATH = "v1/videos/generations";

    public GrokVideoService(Configuration configuration) {
        super(configuration);
    }

    @Override
    protected String defaultCreatePath() {
        return DEFAULT_CREATE_PATH;
    }

    @Override
    protected Map<String, Object> toJsonBody(VideoCreateRequest request) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", request.getModel());
        putIfPresent(body, "prompt", request.getPrompt());
        Object duration = request.getDurationSeconds() != null ? request.getDurationSeconds() : request.getSeconds();
        putIfPresent(body, "duration", duration);
        putIfPresent(body, "aspect_ratio", request.getAspectRatio());
        putIfPresent(body, "resolution", request.getResolution());
        putIfPresent(body, "image", request.getInputImage());
        if (request.getReferenceImages() != null && !request.getReferenceImages().isEmpty()) {
            body.put("reference_images", request.getReferenceImages());
        }
        return body;
    }
}
