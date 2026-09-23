package io.github.lnyocly.ai4j.document.mineru;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerUApiResponse;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerUBatchRequest;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerUBatchStatus;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerUFileSpec;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerULiteRequest;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerULiteStatus;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerUTaskRequest;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerUTaskStatus;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerUUploadBatch;
import io.github.lnyocly.ai4j.exception.Ai4jException;
import io.github.lnyocly.ai4j.exception.AiRateLimitException;
import io.github.lnyocly.ai4j.exception.AiTimeoutException;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.exception.HttpErrorDecoder;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Function;

/**
 * MinerU 云端文档解析服务客户端（https://mineru.net）。
 *
 * <p>覆盖两套公开 API：</p>
 * <ul>
 *   <li>v4 精准解析（需 token，{@code Authorization: Bearer}）：
 *       URL 单任务 {@code POST /extract/task}、URL 批量 {@code POST /extract/task/batch}、
 *       本地文件签名上传 {@code POST /file-urls/batch} + PUT 上传、
 *       单任务查询 {@code GET /extract/task/{id}}、批量查询 {@code GET /extract-results/batch/{id}}，
 *       结果 zip 下载与解包（full.md / content_list.json / images）。</li>
 *   <li>v1 Agent 轻量解析（免 token，IP 限频，单文件 ≤10MB/≤20 页，仅输出 Markdown）：
 *       {@code POST /parse/url}、{@code POST /parse/file}（返回 OSS 签名上传地址）、
 *       {@code GET /parse/{taskId}}。</li>
 * </ul>
 *
 * <p>所有任务型接口均为异步：提交后通过 {@code waitForTask} / {@code waitForBatch} /
 * {@code liteWaitTask} 轮询，或由 {@code submitTaskAndWait} / {@code uploadAndWait} /
 * {@code liteParseByUrl} / {@code liteParseByFile} 一站式完成。</p>
 */
public class MinerUService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(Constants.JSON_CONTENT_TYPE);
    private static final long MAX_BATCH_FILES = 50;
    private static final int MAX_POLL_RETRIES = 3;

    private final MinerUConfig config;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public MinerUService(Configuration configuration) {
        this.config = configuration.getMineruConfig() == null ? new MinerUConfig() : configuration.getMineruConfig();
        this.okHttpClient = configuration.getOkHttpClient() == null ? new OkHttpClient() : configuration.getOkHttpClient();
    }

    public MinerUService(MinerUConfig config) {
        this(config, new OkHttpClient());
    }

    public MinerUService(MinerUConfig config, OkHttpClient okHttpClient) {
        this.config = config == null ? new MinerUConfig() : config;
        this.okHttpClient = okHttpClient == null ? new OkHttpClient() : okHttpClient;
    }

    public MinerUConfig getConfig() {
        return config;
    }

    /** 是否配置了 v4 精准解析所需的 token。 */
    public boolean hasApiKey() {
        return StringUtils.isNotBlank(config.getApiKey());
    }

    // ------------------------------------------------------------------
    // v4 精准解析 API（需 token）
    // ------------------------------------------------------------------

    /**
     * 创建单文件 URL 解析任务：{@code POST /extract/task}。
     *
     * @return task_id
     */
    public String createExtractTask(MinerUTaskRequest request) throws IOException {
        requireApiKey();
        applyTaskDefaults(request);
        JsonNode data = executeData(authedPost(endpoint(config.getBaseUrl(), "/extract/task"), request));
        return requireText(data, "task_id");
    }

    /**
     * 查询单任务结果：{@code GET /extract/task/{taskId}}。
     */
    public MinerUTaskStatus getExtractTask(String taskId) throws IOException {
        requireApiKey();
        JsonNode data = executeData(authedGet(endpoint(config.getBaseUrl(), "/extract/task/" + encode(taskId))));
        return dataAs(data, MinerUTaskStatus.class);
    }

    /**
     * 批量创建 URL 解析任务：{@code POST /extract/task/batch}（files 填 url，≤50 个）。
     *
     * @return batch_id
     */
    public String createBatchExtractTask(MinerUBatchRequest request) throws IOException {
        requireApiKey();
        applyBatchDefaults(request);
        checkBatchSize(request);
        JsonNode data = executeData(authedPost(endpoint(config.getBaseUrl(), "/extract/task/batch"), request));
        return requireText(data, "batch_id");
    }

    /**
     * 申请本地文件上传链接：{@code POST /file-urls/batch}（files 填 name，≤50 个）。
     * 返回的 fileUrls 与请求 files 顺序对应，24 小时内有效；
     * 对每个 url 调用 {@link #uploadFile} 完成后系统自动提交解析任务。
     */
    public MinerUUploadBatch applyUploadUrls(MinerUBatchRequest request) throws IOException {
        requireApiKey();
        applyBatchDefaults(request);
        checkBatchSize(request);
        JsonNode data = executeData(authedPost(endpoint(config.getBaseUrl(), "/file-urls/batch"), request));
        return dataAs(data, MinerUUploadBatch.class);
    }

    /**
     * PUT 上传文件到签名上传链接。按 MinerU 要求不设置 Content-Type。
     */
    public void uploadFile(String uploadUrl, byte[] content) throws IOException {
        RequestBody body = RequestBody.create(content, null);
        Request request = new Request.Builder().url(uploadUrl).put(body).build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw HttpErrorDecoder.decode(response);
            }
        }
    }

    /**
     * 批量任务查询：{@code GET /extract-results/batch/{batchId}}。
     */
    public MinerUBatchStatus getBatchResult(String batchId) throws IOException {
        requireApiKey();
        JsonNode data = executeData(authedGet(endpoint(config.getBaseUrl(), "/extract-results/batch/" + encode(batchId))));
        return dataAs(data, MinerUBatchStatus.class);
    }

    /**
     * 轮询单任务直到 done/failed/超时。
     */
    public MinerUTaskStatus waitForTask(String taskId) throws IOException {
        MinerUTaskStatus status = pollUntil("task " + taskId,
                () -> getExtractTask(taskId),
                MinerUTaskStatus::getState);
        if (status.isFailed()) {
            throw new CommonException("MinerU task " + taskId + " failed: " + status.getErrMsg());
        }
        return status;
    }

    /**
     * 创建 URL 任务并轮询至完成，返回终态任务（含 fullZipUrl）。
     */
    public MinerUTaskStatus submitTaskAndWait(MinerUTaskRequest request) throws IOException {
        String taskId = createExtractTask(request);
        return waitForTask(taskId);
    }

    /**
     * 轮询批量任务直到全部文件进入终态（done/failed）。
     * 返回完整批量状态，各文件的成败由调用方逐项检查。
     */
    public MinerUBatchStatus waitForBatch(String batchId) throws IOException {
        MinerUBatchStatus status = pollUntil("batch " + batchId,
                () -> getBatchResult(batchId),
                s -> s.isFinished() ? MinerUTaskStatus.STATE_DONE : "running");
        return status;
    }

    /**
     * 本地文件一站式解析：申请上传链接 → PUT 上传 → 轮询批量结果 → 返回该文件的终态项。
     * 文件解析失败时抛出含 err_msg 的 {@link CommonException}。
     */
    public MinerUTaskStatus uploadAndWait(String fileName, byte[] content) throws IOException {
        MinerUBatchRequest request = MinerUBatchRequest.builder()
                .file(MinerUFileSpec.ofName(fileName))
                .build();
        MinerUUploadBatch batch = applyUploadUrls(request);
        List<String> urls = batch.getFileUrls();
        if (urls == null || urls.size() != 1) {
            throw new CommonException("MinerU did not return an upload url for file: " + fileName);
        }
        uploadFile(urls.get(0), content);
        MinerUBatchStatus status = waitForBatch(batch.getBatchId());
        MinerUTaskStatus item = findBatchItem(status, fileName);
        if (item.isFailed()) {
            throw new CommonException("MinerU parse failed for file " + fileName + ": " + item.getErrMsg());
        }
        return item;
    }

    /**
     * 本地文件一站式解析（File 便捷重载）。
     */
    public MinerUTaskStatus uploadAndWait(File file) throws IOException {
        return uploadAndWait(file.getName(), Files.readAllBytes(file.toPath()));
    }

    /**
     * 下载结果 zip 并解包为 {@link MinerUExtractResult}。
     */
    public MinerUExtractResult downloadAndExtract(String zipUrl) throws IOException {
        return MinerUExtractResult.fromZip(download(zipUrl));
    }

    /**
     * 原始字节下载（zip/markdown CDN 链接，无需鉴权）。
     */
    public byte[] download(String url) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw HttpErrorDecoder.decode(response);
            }
            ResponseBody body = response.body();
            return body == null ? new byte[0] : body.bytes();
        }
    }

    // ------------------------------------------------------------------
    // v1 Agent 轻量解析 API（免 token，IP 限频）
    // ------------------------------------------------------------------

    /**
     * 提交远程文件 URL 解析：{@code POST /parse/url}。
     *
     * @return task_id
     */
    public String liteSubmitUrl(MinerULiteRequest request) throws IOException {
        applyLiteDefaults(request);
        JsonNode data = executeData(jsonPost(endpoint(config.getLiteBaseUrl(), "/parse/url"), request));
        return requireText(data, "task_id");
    }

    /**
     * 申请本地文件签名上传任务：{@code POST /parse/file}。
     * 返回含 taskId 与 OSS 上传地址 fileUrl，对其调用 {@link #uploadFile} 后开始解析。
     */
    public MinerULiteStatus liteSubmitFile(MinerULiteRequest request) throws IOException {
        if (request == null || StringUtils.isBlank(request.getFileName())) {
            throw new CommonException("MinerU liteSubmitFile requires fileName");
        }
        applyLiteDefaults(request);
        JsonNode data = executeData(jsonPost(endpoint(config.getLiteBaseUrl(), "/parse/file"), request));
        return dataAs(data, MinerULiteStatus.class);
    }

    /**
     * 查询轻量任务结果：{@code GET /parse/{taskId}}。
     */
    public MinerULiteStatus liteGetTask(String taskId) throws IOException {
        Request request = new Request.Builder()
                .url(endpoint(config.getLiteBaseUrl(), "/parse/" + encode(taskId)))
                .get()
                .build();
        JsonNode data = executeData(request);
        return dataAs(data, MinerULiteStatus.class);
    }

    /**
     * 轮询轻量任务直到 done/failed/超时。
     */
    public MinerULiteStatus liteWaitTask(final String taskId) throws IOException {
        MinerULiteStatus status = pollUntil("lite task " + taskId,
                () -> liteGetTask(taskId),
                MinerULiteStatus::getState);
        if (status.isFailed()) {
            throw new CommonException("MinerU lite task " + taskId + " failed: " + status.getErrMsg()
                    + (status.getErrCode() != null ? " (errCode=" + status.getErrCode() + ")" : ""));
        }
        return status;
    }

    /**
     * URL 一站式轻量解析：提交 → 轮询 → 下载 markdown 文本。
     */
    public String liteParseByUrl(String url) throws IOException {
        return liteParseByUrl(MinerULiteRequest.builder().url(url).build());
    }

    public String liteParseByUrl(MinerULiteRequest request) throws IOException {
        String taskId = liteSubmitUrl(request);
        MinerULiteStatus status = liteWaitTask(taskId);
        return liteFetchMarkdown(status.getMarkdownUrl());
    }

    /**
     * 本地文件一站式轻量解析：申请签名上传 → PUT 上传 → 轮询 → 下载 markdown 文本。
     */
    public String liteParseByFile(String fileName, byte[] content) throws IOException {
        MinerULiteStatus submitted = liteSubmitFile(MinerULiteRequest.builder().fileName(fileName).build());
        if (StringUtils.isBlank(submitted.getFileUrl())) {
            throw new CommonException("MinerU lite submit did not return file_url for: " + fileName);
        }
        uploadFile(submitted.getFileUrl(), content);
        MinerULiteStatus status = liteWaitTask(submitted.getTaskId());
        return liteFetchMarkdown(status.getMarkdownUrl());
    }

    /**
     * 下载 markdown 文本。
     */
    public String liteFetchMarkdown(String markdownUrl) throws IOException {
        if (StringUtils.isBlank(markdownUrl)) {
            throw new CommonException("MinerU lite task done but markdown_url is empty");
        }
        return new String(download(markdownUrl), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private Request authedPost(String url, Object body) throws IOException {
        return basePost(url, body)
                .header("Authorization", "Bearer " + config.getApiKey())
                .build();
    }

    private Request authedGet(String url) {
        return new Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer " + config.getApiKey())
                .build();
    }

    private Request jsonPost(String url, Object body) throws IOException {
        return basePost(url, body).build();
    }

    private Request.Builder basePost(String url, Object body) throws IOException {
        return new Request.Builder()
                .url(url)
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON_MEDIA_TYPE));
    }

    private JsonNode executeData(Request request) throws IOException {
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw HttpErrorDecoder.decode(response);
            }
            ResponseBody responseBody = response.body();
            String body = responseBody == null ? "" : responseBody.string();
            MinerUApiResponse envelope = mapper.readValue(body, MinerUApiResponse.class);
            if (envelope.getCode() != 0) {
                throw new CommonException("MinerU api error: code=" + envelope.getCode()
                        + ", msg=" + envelope.getMsg()
                        + (envelope.getTraceId() != null ? ", traceId=" + envelope.getTraceId() : ""));
            }
            return envelope.getData();
        }
    }

    private String requireText(JsonNode data, String field) {
        if (data == null || data.get(field) == null || !data.get(field).isTextual()) {
            throw new CommonException("MinerU api returned no data." + field);
        }
        return data.get(field).asText();
    }

    private <T> T dataAs(JsonNode data, Class<T> type) {
        if (data == null || data.isNull()) {
            throw new CommonException("MinerU api returned empty data");
        }
        try {
            return mapper.treeToValue(data, type);
        } catch (Exception e) {
            throw new Ai4jException("MinerU api data parse failed: " + e.getMessage(), e);
        }
    }

    private interface PollFetch<T> {
        T get() throws IOException;
    }

    private <T> T pollUntil(String label, PollFetch<T> fetch, Function<T, String> stateFn) throws IOException {
        long deadline = System.currentTimeMillis() + config.getPollTimeoutMs();
        int retries = 0;
        while (true) {
            T status = null;
            try {
                status = fetch.get();
                retries = 0;
            } catch (IOException | AiRateLimitException e) {
                // 轮询周期内允许少量瞬时网络错误/限频，连续超限才视为失败
                if (++retries > MAX_POLL_RETRIES) {
                    throw e;
                }
            }
            if (status != null) {
                String state = stateFn.apply(status);
                if (MinerUTaskStatus.STATE_DONE.equals(state) || MinerUTaskStatus.STATE_FAILED.equals(state)) {
                    return status;
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new AiTimeoutException(408, "MinerU " + label + " polling timeout after "
                        + config.getPollTimeoutMs() + "ms");
            }
            try {
                Thread.sleep(config.getPollIntervalMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new Ai4jException("MinerU " + label + " polling interrupted", e);
            }
        }
    }

    private MinerUTaskStatus findBatchItem(MinerUBatchStatus status, String fileName) {
        List<MinerUTaskStatus> items = status.getExtractResult();
        if (items != null) {
            for (MinerUTaskStatus item : items) {
                if (fileName.equals(item.getFileName())) {
                    return item;
                }
            }
            if (items.size() == 1) {
                return items.get(0);
            }
        }
        throw new CommonException("MinerU batch " + status.getBatchId() + " has no result for file: " + fileName);
    }

    private void requireApiKey() {
        if (!hasApiKey()) {
            throw new CommonException("MinerU apiKey is not configured. "
                    + "Set MinerUConfig.apiKey for the v4 API, or use the token-free lite API.");
        }
    }

    private void checkBatchSize(MinerUBatchRequest request) {
        if (request.getFiles() == null || request.getFiles().isEmpty()) {
            throw new CommonException("MinerU batch request requires at least one file");
        }
        if (request.getFiles().size() > MAX_BATCH_FILES) {
            throw new CommonException("MinerU batch request supports at most " + MAX_BATCH_FILES + " files");
        }
    }

    private void applyTaskDefaults(MinerUTaskRequest request) {
        if (request.getIsOcr() == null) {
            request.setIsOcr(config.getIsOcr());
        }
        if (request.getEnableFormula() == null) {
            request.setEnableFormula(config.getEnableFormula());
        }
        if (request.getEnableTable() == null) {
            request.setEnableTable(config.getEnableTable());
        }
        if (request.getLanguage() == null) {
            request.setLanguage(config.getLanguage());
        }
        if (request.getModelVersion() == null) {
            request.setModelVersion(config.getModelVersion());
        }
        if (request.getExtraFormats() == null) {
            request.setExtraFormats(config.getExtraFormats());
        }
        if (request.getDataId() == null) {
            request.setDataId(config.getDataId());
        }
        if (request.getPageRanges() == null) {
            request.setPageRanges(config.getPageRanges());
        }
    }

    private void applyBatchDefaults(MinerUBatchRequest request) {
        if (request.getEnableFormula() == null) {
            request.setEnableFormula(config.getEnableFormula());
        }
        if (request.getEnableTable() == null) {
            request.setEnableTable(config.getEnableTable());
        }
        if (request.getLanguage() == null) {
            request.setLanguage(config.getLanguage());
        }
        if (request.getModelVersion() == null) {
            request.setModelVersion(config.getModelVersion());
        }
        if (request.getExtraFormats() == null) {
            request.setExtraFormats(config.getExtraFormats());
        }
    }

    private void applyLiteDefaults(MinerULiteRequest request) {
        if (request.getLanguage() == null) {
            request.setLanguage(config.getLanguage());
        }
        if (request.getEnableTable() == null) {
            request.setEnableTable(config.getEnableTable());
        }
        if (request.getIsOcr() == null) {
            request.setIsOcr(config.getIsOcr());
        }
        if (request.getEnableFormula() == null) {
            request.setEnableFormula(config.getEnableFormula());
        }
        if (request.getPageRange() == null) {
            request.setPageRange(config.getPageRanges());
        }
    }

    private String endpoint(String base, String path) {
        if (StringUtils.isBlank(base)) {
            throw new CommonException("MinerU base url is not configured");
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }
}
