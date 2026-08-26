package io.github.lnyocly.ai4j.platform.minimax.video;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.MinimaxConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.exception.HttpErrorDecoder;
import io.github.lnyocly.ai4j.network.UrlUtils;
import io.github.lnyocly.ai4j.platform.minimax.video.entity.MinimaxFileResponse;
import io.github.lnyocly.ai4j.platform.minimax.video.entity.MinimaxVideoRequest;
import io.github.lnyocly.ai4j.platform.minimax.video.entity.MinimaxVideoResponse;
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
import java.util.Locale;
import java.util.Map;

/**
 * MiniMax Hailuo 视频生成服务（私有协议，非 OpenAI 兼容，故不继承
 * {@code AbstractVideoService}）。
 *
 * <p>流程：POST video_generation 拿 task_id → 轮询 query/video_generation →
 * Success 后经 files/retrieve 换取 download_url。MiniMax 失败也返回 HTTP 200，
 * 错误藏在 base_resp.status_code != 0，需映射进 {@code VideoResponse.error}。
 */
public class MinimaxVideoService implements IVideoService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.APPLICATION_JSON);
    private static final String STATUS_SUCCESS = "Success";
    private static final String STATUS_FAIL = "Fail";

    private final MinimaxConfig minimaxConfig;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public MinimaxVideoService(Configuration configuration) {
        this.minimaxConfig = configuration.getMinimaxConfig();
        this.okHttpClient = configuration.getOkHttpClient();
    }

    @Override
    public VideoResponse create(String baseUrl, String apiKey, VideoCreateRequest request) throws Exception {
        Map<String, Object> body = mapper.convertValue(toMinimaxRequest(request),
                new TypeReference<Map<String, Object>>() { });
        if (request.getExtraFields() != null) {
            for (Map.Entry<String, Object> entry : request.getExtraFields().entrySet()) {
                if (entry.getValue() != null) {
                    body.put(entry.getKey(), entry.getValue());
                }
            }
        }
        Request httpRequest = authorizedRequest(baseUrl, apiKey, minimaxConfig.getVideoCreateUrl())
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON_MEDIA_TYPE))
                .build();
        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            MinimaxVideoResponse minimaxResponse = parse(response, MinimaxVideoResponse.class);
            return toVideoResponse(minimaxResponse, null);
        }
    }

    @Override
    public VideoResponse create(VideoCreateRequest request) throws Exception {
        return create(null, null, request);
    }

    @Override
    public VideoResponse retrieve(String baseUrl, String apiKey, String id) throws Exception {
        String url = UrlUtils.concatUrl(resolveBaseUrl(baseUrl), minimaxConfig.getVideoQueryUrl())
                + "?task_id=" + encode(id);
        Request httpRequest = authorizedUrlRequest(baseUrl, apiKey, url).get().build();
        MinimaxVideoResponse minimaxResponse;
        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            minimaxResponse = parse(response, MinimaxVideoResponse.class);
        }

        String downloadUrl = null;
        if (STATUS_SUCCESS.equals(minimaxResponse.getStatus()) && minimaxResponse.getFileId() != null) {
            downloadUrl = retrieveDownloadUrl(baseUrl, apiKey, minimaxResponse.getFileId());
        }
        return toVideoResponse(minimaxResponse, downloadUrl);
    }

    @Override
    public VideoResponse retrieve(String id) throws Exception {
        return retrieve(null, null, id);
    }

    @Override
    public InputStream content(String baseUrl, String apiKey, String id) throws Exception {
        VideoResponse response = retrieve(baseUrl, apiKey, id);
        if (response.getVideoUrl() == null) {
            throw new CommonException("Minimax 视频 " + id + " 尚无下载地址，当前状态: " + response.getStatus());
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
        throw new UnsupportedOperationException("MiniMax Hailuo 视频不支持 remix");
    }

    @Override
    public VideoResponse remix(String id, String prompt) {
        throw new UnsupportedOperationException("MiniMax Hailuo 视频不支持 remix");
    }

    private MinimaxVideoRequest toMinimaxRequest(VideoCreateRequest request) {
        return MinimaxVideoRequest.builder()
                .model(request.getModel())
                .prompt(request.getPrompt())
                .firstFrameImage(request.getInputImage())
                // 实测协议默认关掉提示词增强，避免平台改写用户提示词
                .promptEnhance(Boolean.FALSE)
                .build();
    }

    private String retrieveDownloadUrl(String baseUrl, String apiKey, String fileId) throws Exception {
        String url = UrlUtils.concatUrl(resolveBaseUrl(baseUrl), minimaxConfig.getFileRetrieveUrl())
                + "?file_id=" + encode(fileId);
        Request httpRequest = authorizedUrlRequest(baseUrl, apiKey, url).get().build();
        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            MinimaxFileResponse fileResponse = parse(response, MinimaxFileResponse.class);
            if (fileResponse.getFile() == null || fileResponse.getFile().getDownloadUrl() == null) {
                throw new CommonException("Minimax files/retrieve 未返回 download_url，file_id=" + fileId);
            }
            return fileResponse.getFile().getDownloadUrl();
        }
    }

    private VideoResponse toVideoResponse(MinimaxVideoResponse minimaxResponse, String downloadUrl) {
        VideoResponse response = new VideoResponse();
        response.setId(minimaxResponse.getTaskId());
        response.setStatus(mapStatus(minimaxResponse.getStatus()));
        if (minimaxResponse.getVideoWidth() != null && minimaxResponse.getVideoHeight() != null) {
            response.setSize(minimaxResponse.getVideoWidth() + "x" + minimaxResponse.getVideoHeight());
        }
        response.setVideoUrl(downloadUrl);
        response.setRaw(mapper.convertValue(minimaxResponse, new TypeReference<Map<String, Object>>() { }));

        MinimaxVideoResponse.BaseResp baseResp = minimaxResponse.getBaseResp();
        if (baseResp != null && baseResp.getStatusCode() != null && baseResp.getStatusCode() != 0) {
            VideoResponse.VideoError error = new VideoResponse.VideoError();
            error.setCode(String.valueOf(baseResp.getStatusCode()));
            error.setMessage(baseResp.getStatusMsg());
            response.setError(error);
            response.setStatus("failed");
        }
        return response;
    }

    /** Preparing/Processing → processing，Success → succeeded，Fail → failed，其余原样小写。 */
    private String mapStatus(String status) {
        if (status == null) {
            return null;
        }
        if (STATUS_SUCCESS.equals(status)) {
            return "succeeded";
        }
        if (STATUS_FAIL.equals(status)) {
            return "failed";
        }
        return status.toLowerCase(Locale.ROOT);
    }

    private <T> T parse(Response response, Class<T> type) throws IOException {
        if (response.isSuccessful() && response.body() != null) {
            return mapper.readValue(response.body().string(), type);
        }
        throw HttpErrorDecoder.decode(response);
    }

    private Request.Builder authorizedRequest(String baseUrl, String apiKey, String path) {
        return authorizedUrlRequest(baseUrl, apiKey,
                UrlUtils.concatUrl(resolveBaseUrl(baseUrl), path));
    }

    private Request.Builder authorizedUrlRequest(String baseUrl, String apiKey, String url) {
        return new Request.Builder()
                .header("Authorization", "Bearer " + resolveApiKey(apiKey))
                .url(url);
    }

    private String resolveBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return baseUrl;
        }
        String host = minimaxConfig.getVideoApiHost();
        return (host == null || host.isEmpty()) ? minimaxConfig.getApiHost() : host;
    }

    private String resolveApiKey(String apiKey) {
        if (apiKey != null && !apiKey.isEmpty()) {
            return apiKey;
        }
        String key = minimaxConfig.getVideoApiKey();
        return (key == null || key.isEmpty()) ? minimaxConfig.getApiKey() : key;
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
