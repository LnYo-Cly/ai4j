package io.github.lnyocly.ai4j.platform.openai.video;

import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoCreateRequest;
import io.github.lnyocly.ai4j.service.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI-compatible video service ({@code POST v1/videos}, {@code GET v1/videos/{id}}).
 *
 * <p>The create call sends {@code application/json}. Gateways that still require
 * {@code multipart/form-data} must opt in explicitly via
 * {@link io.github.lnyocly.ai4j.platform.openai.video.entity.VideoBodyMode#MULTIPART}.
 */
public class OpenAiVideoService extends AbstractVideoService {

    public OpenAiVideoService(Configuration configuration) {
        super(configuration);
    }

    @Override
    protected String defaultCreatePath() {
        return openAiConfig.getVideoCreateUrl();
    }

    @Override
    protected Map<String, Object> toJsonBody(VideoCreateRequest request) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", request.getModel());
        putIfPresent(body, "prompt", request.getPrompt());
        Object seconds = request.getDurationSeconds() != null ? request.getDurationSeconds() : request.getSeconds();
        putIfPresent(body, "seconds", seconds);
        putIfPresent(body, "size", request.getSize());
        putIfPresent(body, "input_reference", request.getInputImage());
        return body;
    }
}
