package io.github.lnyocly.ai4j.platform.agnes.video;

import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.exception.HttpErrorDecoder;
import io.github.lnyocly.ai4j.network.UrlUtils;
import io.github.lnyocly.ai4j.platform.openai.video.AbstractVideoService;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoCreateRequest;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.Request;
import okhttp3.Response;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Video service for Agnes AI ({@code agnes-video-*}).
 *
 * <p>Creation is OpenAI Videos-compatible ({@code POST v1/videos}), but polling uses a
 * dedicated query endpoint instead of {@code v1/videos/{id}}:
 * {@code GET agnesapi?video_id=<id>&model_name=<model>}. {@code model_name} is required
 * for {@code keyframe} and {@code reference} tasks; text-mode tasks also accept a bare
 * {@code video_id} query.
 *
 * <p>Request dialect: {@code seconds} is a string ("4"-"12"), {@code mode} is required and
 * derived from the neutral references (first/last frame → {@code keyframe}, images/audios →
 * {@code reference}, otherwise {@code text}), and {@code agnes-video-2.5-flash} only accepts
 * {@code size="720P"} with at most 5 images / 3 audios and no videos. The response is
 * OpenAI-shaped plus {@code video_id} (the polling key) and {@code metadata.url} (the
 * finished file), both handled by {@link VideoResponse}.
 */
public class AgnesVideoService extends AbstractVideoService {

    private static final String RETRIEVE_PATH = "agnesapi";

    public AgnesVideoService(Configuration configuration) {
        super(configuration);
    }

    @Override
    protected String defaultCreatePath() {
        return "v1/videos";
    }

    @Override
    protected Map<String, Object> toJsonBody(VideoCreateRequest request) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", request.getModel());
        putIfPresent(body, "prompt", request.getPrompt());
        putIfPresent(body, "seconds", seconds(request));
        putIfPresent(body, "size", request.getSize() == null || request.getSize().isEmpty()
                ? "720P" : request.getSize());
        putIfPresent(body, "aspect_ratio", request.getAspectRatio());
        body.put("mode", mode(request));
        body.put("n", 1);
        putIfPresent(body, "first_frame", request.getInputImage());
        putIfPresent(body, "last_frame", request.getLastFrame());
        if (request.getReferenceImages() != null && !request.getReferenceImages().isEmpty()) {
            body.put("images", request.getReferenceImages());
        }
        if (request.getReferenceAudios() != null && !request.getReferenceAudios().isEmpty()) {
            body.put("audios", request.getReferenceAudios());
        }
        return body;
    }

    /** Agnes documents {@code seconds} as a string ("4"-"12"); numbers are normalized here. */
    private static Object seconds(VideoCreateRequest request) {
        Object seconds = request.getDurationSeconds() != null ? request.getDurationSeconds() : request.getSeconds();
        return seconds instanceof Number ? String.valueOf(seconds) : seconds;
    }

    private static String mode(VideoCreateRequest request) {
        if (present(request.getInputImage()) || present(request.getLastFrame())) {
            return "keyframe";
        }
        if (nonEmpty(request.getReferenceImages()) || nonEmpty(request.getReferenceAudios())) {
            return "reference";
        }
        return "text";
    }

    private static boolean present(String value) {
        return value != null && !value.isEmpty();
    }

    private static boolean nonEmpty(List<?> values) {
        return values != null && !values.isEmpty();
    }

    @Override
    protected Request.Builder retrieveRequest(String baseUrl, String apiKey, String id, String model)
            throws IOException {
        StringBuilder url = new StringBuilder(UrlUtils.concatUrl(resolveBaseUrl(baseUrl), RETRIEVE_PATH))
                .append("?video_id=").append(encodePathSegment(id));
        if (present(model)) {
            url.append("&model_name=").append(encodePathSegment(model));
        }
        return new Request.Builder()
                .header("Authorization", "Bearer " + resolveApiKey(apiKey))
                .url(url.toString());
    }

    /**
     * Agnes has no {@code v1/videos/{id}/content} endpoint: retrieve the task and download
     * {@code metadata.url} once it is ready. Keyframe/reference tasks need a model-aware
     * retrieve; {@link #retrieve(String, String, String, String)} supplies it.
     */
    @Override
    public InputStream content(String baseUrl, String apiKey, String id) throws Exception {
        VideoResponse task = retrieve(baseUrl, apiKey, id);
        if (task.getVideoUrl() == null) {
            throw new CommonException("Agnes 视频 " + id + " 尚未返回地址，当前状态: " + task.getStatus());
        }
        Response download = okHttpClient.newCall(
                new Request.Builder().url(task.getVideoUrl()).get().build()).execute();
        if (!download.isSuccessful() || download.body() == null) {
            try {
                throw HttpErrorDecoder.decode(download);
            } finally {
                download.close();
            }
        }
        return new ResponseInputStream(download, download.body().byteStream());
    }

    @Override
    public VideoResponse remix(String baseUrl, String apiKey, String id, String prompt) {
        throw new UnsupportedOperationException("Agnes 视频不支持 remix");
    }

    @Override
    public VideoResponse remix(String id, String prompt) {
        throw new UnsupportedOperationException("Agnes 视频不支持 remix");
    }

    private static final class ResponseInputStream extends FilterInputStream {
        private final Response response;

        private ResponseInputStream(Response response, InputStream delegate) {
            super(delegate);
            this.response = response;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                response.close();
            }
        }
    }
}
