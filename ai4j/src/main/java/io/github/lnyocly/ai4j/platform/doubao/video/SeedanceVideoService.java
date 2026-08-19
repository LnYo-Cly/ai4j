package io.github.lnyocly.ai4j.platform.doubao.video;

import io.github.lnyocly.ai4j.platform.openai.video.AbstractVideoService;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoCreateRequest;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoReference;
import io.github.lnyocly.ai4j.service.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seedance (Volcengine Ark) video service.
 *
 * <p>Dialect shape, per the Ark content-generation contract:
 * <pre>
 *   POST {base}api/v3/contents/generations/tasks
 *   {
 *     "model": "doubao-seedance-...",
 *     "content": [
 *       {"type":"text","text":"..."},
 *       {"type":"image_url","image_url":{"url":"..."},"role":"first_frame"},
 *       {"type":"video_url","video_url":{"url":"..."},"role":"reference_video"},
 *       {"type":"audio_url","audio_url":{"url":"..."},"role":"reference_audio"}
 *     ],
 *     "duration": 4, "ratio": "16:9", "resolution": "480p",
 *     "generate_audio": true, "watermark": false
 *   }
 *   GET  {base}api/v3/contents/generations/tasks/{id}
 *       -> { "status": "...", "content": { "video_url": "..." }, "usage": {...} }
 * </pre>
 *
 * <p>{@code apiHost} carries the base URL, so the same dialect serves the official Ark
 * endpoint and any faithful relay (which may prefix the path, e.g.
 * {@code /volcengine/api/v3/...}) — set {@code videoTaskUrl} for that.
 *
 * <p>Which reference roles a model accepts differs by generation (2.x supports omni-modal
 * references, 1.x only frames). That is a catalog concern: this service forwards whatever
 * roles the caller supplies rather than filtering them.
 */
public class SeedanceVideoService extends AbstractVideoService {

    public static final String DEFAULT_TASK_PATH = "api/v3/contents/generations/tasks";

    private final String taskPath;

    public SeedanceVideoService(Configuration configuration) {
        this(configuration, DEFAULT_TASK_PATH);
    }

    /**
     * @param taskPath path of the task collection, e.g. {@code api/v3/contents/generations/tasks}
     *                 or a relay prefix such as {@code volcengine/api/v3/contents/generations/tasks}
     */
    public SeedanceVideoService(Configuration configuration, String taskPath) {
        super(configuration);
        this.taskPath = taskPath == null || taskPath.isEmpty() ? DEFAULT_TASK_PATH : taskPath;
    }

    @Override
    protected String defaultCreatePath() {
        return taskPath;
    }

    /** Ark polls and fetches content under the same task collection as create. */
    @Override
    protected String taskResourcePath() {
        return taskPath;
    }

    @Override
    protected Map<String, Object> toJsonBody(VideoCreateRequest request) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", request.getModel());
        body.put("content", contentParts(request));
        Object duration = request.getDurationSeconds() != null ? request.getDurationSeconds() : request.getSeconds();
        putIfPresent(body, "duration", duration);
        putIfPresent(body, "ratio", request.getAspectRatio());
        putIfPresent(body, "resolution", request.getResolution());
        putIfPresent(body, "generate_audio", request.getGenerateAudio());
        putIfPresent(body, "watermark", request.getWatermark());
        return body;
    }

    /**
     * Build the {@code content[]} array: the prompt first, then typed references. The simple
     * {@code inputImage} / {@code referenceImages} fields map onto {@code first_frame} and
     * {@code reference_image} so callers that do not know this dialect still work.
     */
    private List<Map<String, Object>> contentParts(VideoCreateRequest request) {
        List<Map<String, Object>> parts = new ArrayList<Map<String, Object>>();
        String prompt = request.getPrompt();
        if (prompt != null && !prompt.isEmpty()) {
            Map<String, Object> text = new LinkedHashMap<String, Object>();
            text.put("type", "text");
            text.put("text", prompt);
            parts.add(text);
        }
        if (request.getInputImage() != null && !request.getInputImage().isEmpty()) {
            parts.add(referencePart(VideoReference.firstFrame(request.getInputImage())));
        }
        if (request.getReferenceImages() != null) {
            for (String url : request.getReferenceImages()) {
                if (url != null && !url.isEmpty()) {
                    parts.add(referencePart(VideoReference.image(url)));
                }
            }
        }
        if (request.getReferences() != null) {
            for (VideoReference reference : request.getReferences()) {
                if (reference == null || reference.getUrl() == null || reference.getUrl().isEmpty()) {
                    continue;
                }
                parts.add(referencePart(reference));
            }
        }
        return parts;
    }

    private Map<String, Object> referencePart(VideoReference reference) {
        VideoReference.Kind kind = reference.getKind() == null ? VideoReference.Kind.IMAGE : reference.getKind();
        Map<String, Object> url = new LinkedHashMap<String, Object>();
        url.put("url", reference.getUrl());
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("type", kind.contentType());
        part.put(kind.contentType(), url);
        if (reference.getRole() != null && !reference.getRole().isEmpty()) {
            part.put("role", reference.getRole());
        }
        return part;
    }
}
