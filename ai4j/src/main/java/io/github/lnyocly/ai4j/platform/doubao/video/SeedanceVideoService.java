package io.github.lnyocly.ai4j.platform.doubao.video;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.DoubaoConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.exception.HttpErrorDecoder;
import io.github.lnyocly.ai4j.network.UrlUtils;
import io.github.lnyocly.ai4j.platform.doubao.video.entity.SeedanceVideoResponse;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoCreateRequest;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IVideoService;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 火山引擎 Seedance（doubao-seedance 系列）视频生成服务。私有任务协议
 * （task_id 在 {@code id} 字段、视频地址嵌套在 {@code content.video_url}），与 OpenAI
 * 响应格式不同，故不继承 {@code AbstractVideoService}。
 *
 * <p>流程：POST {@code api/v3/contents/generations/tasks} 建任务 → 轮询
 * GET 同路径 {@code /{task_id}} 直到 succeeded，取 {@code content.video_url}。
 *
 * <p>模型版本差异（1.5 / 2.0 / 2.5）只是可选参数与 image role 的增减——last_frame、
 * reference_image、generate_audio、seed 由调用方按模型能力放进请求，本服务不做版本分支。
 */
public class SeedanceVideoService implements IVideoService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);
    /** chat 网关 {@link DoubaoConfig#getApiHost()} 默认已含 /api/v3/ 段，直接回退会和路径拼重，故回退到 Ark 根。 */
    private static final String ARK_ROOT = "https://ark.cn-beijing.volces.com/";

    private final DoubaoConfig doubaoConfig;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public SeedanceVideoService(Configuration configuration) {
        this.doubaoConfig = configuration.getDoubaoConfig();
        this.okHttpClient = configuration.getOkHttpClient();
    }

    @Override
    public VideoResponse create(String baseUrl, String apiKey, VideoCreateRequest request) throws Exception {
        Map<String, Object> body = toJsonBody(request);
        Request httpRequest = new Request.Builder()
                .header("Authorization", "Bearer " + resolveApiKey(apiKey))
                .url(UrlUtils.concatUrl(resolveBaseUrl(baseUrl), doubaoConfig.getVideoCreatePath()))
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON_MEDIA_TYPE))
                .build();
        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            return toVideoResponse(parse(response));
        }
    }

    @Override
    public VideoResponse create(VideoCreateRequest request) throws Exception {
        return create(null, null, request);
    }

    @Override
    public VideoResponse retrieve(String baseUrl, String apiKey, String id) throws Exception {
        String url = UrlUtils.concatUrl(resolveBaseUrl(baseUrl), doubaoConfig.getVideoQueryPath())
                + "/" + encode(id);
        Request httpRequest = new Request.Builder()
                .header("Authorization", "Bearer " + resolveApiKey(apiKey))
                .url(url)
                .get()
                .build();
        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            return toVideoResponse(parse(response));
        }
    }

    @Override
    public VideoResponse retrieve(String id) throws Exception {
        return retrieve(null, null, id);
    }

    @Override
    public InputStream content(String baseUrl, String apiKey, String id) throws Exception {
        VideoResponse response = retrieve(baseUrl, apiKey, id);
        if (response.getVideoUrl() == null) {
            throw new CommonException("Seedance 视频 " + id + " 尚无下载地址，当前状态: " + response.getStatus());
        }
        Request request = new Request.Builder().url(response.getVideoUrl()).get().build();
        Response download = okHttpClient.newCall(request).execute();
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
    public InputStream content(String id) throws Exception {
        return content(null, null, id);
    }

    @Override
    public VideoResponse remix(String baseUrl, String apiKey, String id, String prompt) {
        throw new UnsupportedOperationException("Seedance 视频不支持 remix");
    }

    @Override
    public VideoResponse remix(String id, String prompt) {
        throw new UnsupportedOperationException("Seedance 视频不支持 remix");
    }

    private Map<String, Object> toJsonBody(VideoCreateRequest request) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", request.getModel());
        body.put("content", toContent(request));
        if (request.getDurationSeconds() != null) {
            body.put("duration", request.getDurationSeconds());
        } else if (request.getSeconds() instanceof Number) {
            body.put("duration", ((Number) request.getSeconds()).intValue());
        }
        putIfPresent(body, "resolution", request.getResolution());
        putIfPresent(body, "ratio", request.getAspectRatio());
        if (request.getExtraFields() != null) {
            for (Map.Entry<String, Object> entry : request.getExtraFields().entrySet()) {
                // last_frame_url 已在 content 数组里以 role=last_frame 消费，不进顶层
                if (entry.getValue() != null && !"last_frame_url".equals(entry.getKey())) {
                    body.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return body;
    }

    /** content 数组：text 在前，随后 first_frame / reference_image / last_frame 图片项。 */
    private List<Object> toContent(VideoCreateRequest request) {
        List<Object> content = new ArrayList<Object>();
        if (request.getPrompt() != null) {
            Map<String, Object> text = new LinkedHashMap<String, Object>();
            text.put("type", "text");
            text.put("text", request.getPrompt());
            content.add(text);
        }
        if (request.getInputImage() != null) {
            content.add(imageItem(request.getInputImage(), "first_frame"));
        }
        if (request.getReferenceImages() != null) {
            for (String image : request.getReferenceImages()) {
                content.add(imageItem(image, "reference_image"));
            }
        }
        Object lastFrame = request.getExtraFields() == null ? null : request.getExtraFields().get("last_frame_url");
        if (lastFrame instanceof String && !((String) lastFrame).isEmpty()) {
            content.add(imageItem((String) lastFrame, "last_frame"));
        }
        return content;
    }

    private static Map<String, Object> imageItem(String url, String role) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("type", "image_url");
        item.put("image_url", Collections.singletonMap("url", url));
        item.put("role", role);
        return item;
    }

    private VideoResponse toVideoResponse(SeedanceVideoResponse seedance) {
        VideoResponse response = new VideoResponse();
        response.setId(seedance.taskId());
        response.setStatus(mapStatus(seedance.getStatus()));
        response.setModel(seedance.getModel());
        if (seedance.getContent() != null) {
            response.setVideoUrl(seedance.getContent().getVideoUrl());
        }
        response.setRaw(mapper.convertValue(seedance, new TypeReference<Map<String, Object>>() { }));
        if (seedance.getError() != null) {
            VideoResponse.VideoError error = new VideoResponse.VideoError();
            error.setCode(String.valueOf(seedance.getError().getCode()));
            error.setMessage(seedance.getError().getMessage());
            response.setError(error);
        }
        return response;
    }

    /** queued → SUBMITTED，processing → RUNNING，succeeded → SUCCEEDED，failed → FAILED。 */
    private String mapStatus(String status) {
        if (status == null) {
            return null;
        }
        switch (status) {
            case "queued":
                return "SUBMITTED";
            case "processing":
                return "RUNNING";
            case "succeeded":
                return "SUCCEEDED";
            case "failed":
                return "FAILED";
            default:
                return status.toUpperCase(java.util.Locale.ROOT);
        }
    }

    private SeedanceVideoResponse parse(Response response) throws IOException {
        if (response.isSuccessful() && response.body() != null) {
            return mapper.readValue(response.body().string(), SeedanceVideoResponse.class);
        }
        throw HttpErrorDecoder.decode(response);
    }

    private static void putIfPresent(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }

    private String resolveBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return baseUrl;
        }
        if (doubaoConfig.getVideoApiHost() != null && !doubaoConfig.getVideoApiHost().isEmpty()) {
            return doubaoConfig.getVideoApiHost();
        }
        return ARK_ROOT;
    }

    private String resolveApiKey(String apiKey) {
        if (apiKey != null && !apiKey.isEmpty()) {
            return apiKey;
        }
        String key = doubaoConfig.getVideoApiKey();
        return (key == null || key.isEmpty()) ? doubaoConfig.getApiKey() : key;
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
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
