package io.github.lnyocly.ai4j.document.mineru;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerUBatchRequest;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerUBatchStatus;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerUFileSpec;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerUTaskRequest;
import io.github.lnyocly.ai4j.document.mineru.entity.MinerUTaskStatus;
import io.github.lnyocly.ai4j.exception.AiRateLimitException;
import io.github.lnyocly.ai4j.exception.AiTimeoutException;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.interceptor.ErrorInterceptor;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MinerUServiceTest {

    // ---------------------------------------------------------------
    // v4 精准解析 API
    // ---------------------------------------------------------------

    @Test
    public void createExtractTaskPostsSnakeCaseJsonWithBearer() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(json("{\"code\":0,\"data\":{\"task_id\":\"t-1\"},\"msg\":\"ok\",\"trace_id\":\"tr-1\"}"));
        try {
            MinerUService service = service(server, true);
            String taskId = service.createExtractTask(MinerUTaskRequest.builder()
                    .url("https://example.com/a.pdf")
                    .build());

            Assert.assertEquals("t-1", taskId);

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertNotNull(request);
            Assert.assertEquals("/api/v4/extract/task", request.getPath());
            Assert.assertEquals("Bearer test-key", request.getHeader("Authorization"));
            JSONObject body = JSON.parseObject(request.getBody().readUtf8());
            Assert.assertEquals("https://example.com/a.pdf", body.getString("url"));
            Assert.assertEquals("vlm", body.getString("model_version"));
            Assert.assertEquals(Boolean.TRUE, body.getBoolean("enable_table"));
            Assert.assertEquals("ch", body.getString("language"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void createExtractTaskRequiresApiKey() {
        MinerUService service = new MinerUService(new MinerUConfig(), new OkHttpClient());
        try {
            service.createExtractTask(MinerUTaskRequest.builder().url("https://x/a.pdf").build());
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertTrue(e.getMessage().contains("apiKey"));
        } catch (Exception e) {
            Assert.fail("unexpected " + e);
        }
    }

    @Test
    public void getExtractTaskParsesDoneState() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(json("{\"code\":0,\"data\":{\"task_id\":\"t-1\",\"state\":\"done\","
                + "\"full_zip_url\":\"https://cdn.example/r.zip\",\"err_msg\":\"\"},\"msg\":\"ok\"}"));
        try {
            MinerUService service = service(server, true);
            MinerUTaskStatus status = service.getExtractTask("t-1");

            Assert.assertTrue(status.isDone());
            Assert.assertEquals("https://cdn.example/r.zip", status.getFullZipUrl());

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/api/v4/extract/task/t-1", request.getPath());
            Assert.assertEquals("Bearer test-key", request.getHeader("Authorization"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void uploadFilePutsBytesWithoutAuthOrContentType() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setResponseCode(200));
        try {
            MinerUService service = service(server, true);
            service.uploadFile(server.url("/oss/u1").toString(), "abc".getBytes(StandardCharsets.UTF_8));

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("PUT", request.getMethod());
            Assert.assertEquals("/oss/u1", request.getPath());
            Assert.assertNull(request.getHeader("Authorization"));
            Assert.assertNull(request.getHeader("Content-Type"));
            Assert.assertEquals("abc", request.getBody().readUtf8());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void waitForTaskPollsUntilDone() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(json(taskJson("pending")));
        server.enqueue(json(taskJson("running")));
        server.enqueue(json(taskJson("done")));
        try {
            MinerUService service = service(server, true);
            MinerUTaskStatus status = service.waitForTask("t-1");

            Assert.assertTrue(status.isDone());
            Assert.assertEquals(3, server.getRequestCount());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void waitForTaskThrowsOnFailed() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(json("{\"code\":0,\"data\":{\"task_id\":\"t-1\",\"state\":\"failed\","
                + "\"err_msg\":\"file format not supported\"},\"msg\":\"ok\"}"));
        try {
            MinerUService service = service(server, true);
            service.waitForTask("t-1");
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertTrue(e.getMessage().contains("file format not supported"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void waitForTaskRetriesTransientNetworkError() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        // 第一次轮询服务端不响应 → 读超时；重试后拿到 done
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        server.enqueue(json(taskJson("done")));
        try {
            MinerUConfig config = new MinerUConfig();
            config.setBaseUrl(server.url("/api/v4").toString());
            config.setApiKey("test-key");
            config.setPollIntervalMs(10);
            config.setPollTimeoutMs(10000);
            OkHttpClient client = new OkHttpClient.Builder()
                    .readTimeout(200, TimeUnit.MILLISECONDS).build();
            MinerUService service = new MinerUService(config, client);
            MinerUTaskStatus status = service.waitForTask("t-1");

            Assert.assertTrue(status.isDone());
            Assert.assertEquals(2, server.getRequestCount());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void waitForTaskRetriesRateLimitThenSucceeds() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setResponseCode(429).setBody("too many requests"));
        server.enqueue(json(taskJson("done")));
        try {
            MinerUService service = service(server, true);
            MinerUTaskStatus status = service.waitForTask("t-1");

            Assert.assertTrue(status.isDone());
            Assert.assertEquals(2, server.getRequestCount());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void waitForTaskTolerates429WithErrorInterceptorClient() throws Exception {
        // starter 路径注入的共享 client 带 ErrorInterceptor——MinerUService 必须剔除它，
        // 否则 429 会被拍平成 CommonException 使轮询容错失效
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setResponseCode(429).setBody("too many requests"));
        server.enqueue(json(taskJson("done")));
        try {
            MinerUConfig config = new MinerUConfig();
            config.setBaseUrl(server.url("/api/v4").toString());
            config.setApiKey("test-key");
            config.setPollIntervalMs(10);
            config.setPollTimeoutMs(5000);
            Configuration configuration = new Configuration();
            configuration.setMineruConfig(config);
            configuration.setOkHttpClient(new OkHttpClient.Builder()
                    .addInterceptor(new ErrorInterceptor()).build());
            MinerUService service = new MinerUService(configuration);
            MinerUTaskStatus status = service.waitForTask("t-1");

            Assert.assertTrue(status.isDone());
            Assert.assertEquals(2, server.getRequestCount());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void waitForTaskThrowsTimeoutException() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        for (int i = 0; i < 20; i++) {
            server.enqueue(json(taskJson("running")));
        }
        try {
            MinerUConfig config = new MinerUConfig();
            config.setBaseUrl(server.url("/api/v4").toString());
            config.setApiKey("test-key");
            config.setPollIntervalMs(10);
            config.setPollTimeoutMs(50);
            MinerUService service = new MinerUService(config, new OkHttpClient());
            service.waitForTask("t-1");
            Assert.fail("expected AiTimeoutException");
        } catch (AiTimeoutException e) {
            Assert.assertTrue(e.getMessage().contains("t-1"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void uploadAndWaitRunsFullChain() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        String uploadUrl = server.url("/oss/f1").toString();
        String zipUrl = server.url("/result.zip").toString();
        server.enqueue(json("{\"code\":0,\"data\":{\"batch_id\":\"b-1\",\"file_urls\":[\""
                + uploadUrl + "\"]},\"msg\":\"ok\"}"));
        server.enqueue(new MockResponse().setResponseCode(200));
        server.enqueue(json("{\"code\":0,\"data\":{\"batch_id\":\"b-1\",\"extract_result\":["
                + "{\"file_name\":\"a.pdf\",\"state\":\"running\"}]},\"msg\":\"ok\"}"));
        server.enqueue(json("{\"code\":0,\"data\":{\"batch_id\":\"b-1\",\"extract_result\":["
                + "{\"file_name\":\"a.pdf\",\"state\":\"done\",\"full_zip_url\":\"" + zipUrl + "\"}]},\"msg\":\"ok\"}"));
        try {
            MinerUService service = service(server, true);
            MinerUTaskStatus status = service.uploadAndWait("a.pdf", "pdf-bytes".getBytes(StandardCharsets.UTF_8));

            Assert.assertTrue(status.isDone());
            Assert.assertEquals(zipUrl, status.getFullZipUrl());

            Assert.assertEquals("POST", server.takeRequest(1, TimeUnit.SECONDS).getMethod());
            RecordedRequest put = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("PUT", put.getMethod());
            Assert.assertEquals("/oss/f1", put.getPath());
            Assert.assertEquals("/api/v4/extract-results/batch/b-1",
                    server.takeRequest(1, TimeUnit.SECONDS).getPath());
            Assert.assertEquals("/api/v4/extract-results/batch/b-1",
                    server.takeRequest(1, TimeUnit.SECONDS).getPath());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void getBatchResultParsesItems() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(json("{\"code\":0,\"data\":{\"batch_id\":\"b-1\",\"extract_result\":["
                + "{\"file_name\":\"a.pdf\",\"state\":\"done\",\"full_zip_url\":\"u1\"},"
                + "{\"file_name\":\"b.pdf\",\"state\":\"failed\",\"err_msg\":\"too many pages\"}"
                + "]},\"msg\":\"ok\"}"));
        try {
            MinerUService service = service(server, true);
            MinerUBatchStatus status = service.getBatchResult("b-1");

            Assert.assertEquals("b-1", status.getBatchId());
            Assert.assertEquals(2, status.getExtractResult().size());
            Assert.assertTrue(status.isFinished());
            Assert.assertEquals("a.pdf", status.getExtractResult().get(0).getFileName());
            Assert.assertEquals("too many pages", status.getExtractResult().get(1).getErrMsg());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void downloadAndExtractUnzipsMarkdownAndImages() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody(new okio.Buffer().write(zipBytes())));
        try {
            MinerUService service = service(server, true);
            MinerUExtractResult result = service.downloadAndExtract(server.url("/r.zip").toString());

            Assert.assertEquals("# Doc\n", result.getMarkdown());
            Assert.assertEquals("[{\"type\":\"text\"}]", result.getContentListJson());
            Assert.assertEquals(1, result.getImages().size());
            Assert.assertTrue(result.getImages().containsKey("images/a.png"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void apiErrorCodeThrowsCommonException() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(json("{\"code\":-60005,\"msg\":\"file size exceeds limit\",\"trace_id\":\"t\"}"));
        try {
            MinerUService service = service(server, true);
            service.getExtractTask("t-1");
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertTrue(e.getMessage().contains("-60005"));
            Assert.assertTrue(e.getMessage().contains("file size exceeds limit"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void applyUploadUrlsDefaultsIsOcrOnFileSpec() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(json("{\"code\":0,\"data\":{\"batch_id\":\"b-1\",\"file_urls\":[\""
                + server.url("/oss/f1") + "\"]},\"msg\":\"ok\"}"));
        try {
            MinerUConfig config = new MinerUConfig();
            config.setBaseUrl(server.url("/api/v4").toString());
            config.setApiKey("test-key");
            config.setIsOcr(true);
            MinerUService service = new MinerUService(config, new OkHttpClient());
            service.applyUploadUrls(MinerUBatchRequest.builder()
                    .file(MinerUFileSpec.ofName("a.pdf")).build());

            JSONObject body = JSON.parseObject(server.takeRequest(1, TimeUnit.SECONDS).getBody().readUtf8());
            JSONObject file = body.getJSONArray("files").getJSONObject(0);
            Assert.assertEquals("a.pdf", file.getString("name"));
            Assert.assertEquals(Boolean.TRUE, file.getBoolean("is_ocr"));
            Assert.assertEquals("vlm", body.getString("model_version"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void downloadRejectsBlankUrl() throws Exception {
        MinerUService service = new MinerUService(new MinerUConfig(), new OkHttpClient());
        try {
            service.downloadAndExtract(null);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertTrue(e.getMessage().contains("url"));
        }
    }

    // ---------------------------------------------------------------
    // v1 Agent 轻量解析 API
    // ---------------------------------------------------------------

    @Test
    public void liteParseByUrlRunsFullChain() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        String mdUrl = server.url("/md/full.md").toString();
        server.enqueue(json("{\"code\":0,\"data\":{\"task_id\":\"lt-9\"},\"msg\":\"ok\"}"));
        server.enqueue(json("{\"code\":0,\"data\":{\"task_id\":\"lt-9\",\"state\":\"done\",\"markdown_url\":\""
                + mdUrl + "\"},\"msg\":\"ok\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("# Url doc\n"));
        try {
            MinerUService service = service(server, false);
            String markdown = service.liteParseByUrl("https://example.com/a.pdf");

            Assert.assertEquals("# Url doc\n", markdown);
            RecordedRequest submit = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/api/v1/agent/parse/url", submit.getPath());
            Assert.assertNull(submit.getHeader("Authorization"));
            JSONObject body = JSON.parseObject(submit.getBody().readUtf8());
            Assert.assertEquals("https://example.com/a.pdf", body.getString("url"));
            Assert.assertEquals("/api/v1/agent/parse/lt-9", server.takeRequest(1, TimeUnit.SECONDS).getPath());
            Assert.assertEquals("/md/full.md", server.takeRequest(1, TimeUnit.SECONDS).getPath());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void liteParseByFileRunsFullChainWithoutAuth() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        String uploadUrl = server.url("/oss/lite1").toString();
        String mdUrl = server.url("/md/full.md").toString();
        server.enqueue(json("{\"code\":0,\"data\":{\"task_id\":\"lt-1\",\"file_url\":\""
                + uploadUrl + "\"},\"msg\":\"ok\"}"));
        server.enqueue(new MockResponse().setResponseCode(200));
        server.enqueue(json("{\"code\":0,\"data\":{\"task_id\":\"lt-1\",\"state\":\"pending\"},\"msg\":\"ok\"}"));
        server.enqueue(json("{\"code\":0,\"data\":{\"task_id\":\"lt-1\",\"state\":\"done\",\"markdown_url\":\""
                + mdUrl + "\"},\"msg\":\"ok\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("# Lite\n"));
        try {
            MinerUService service = service(server, false);
            String markdown = service.liteParseByFile("a.pdf", "bytes".getBytes(StandardCharsets.UTF_8));

            Assert.assertEquals("# Lite\n", markdown);

            RecordedRequest submit = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/api/v1/agent/parse/file", submit.getPath());
            Assert.assertNull(submit.getHeader("Authorization"));
            JSONObject body = JSON.parseObject(submit.getBody().readUtf8());
            Assert.assertEquals("a.pdf", body.getString("file_name"));
            Assert.assertEquals("PUT", server.takeRequest(1, TimeUnit.SECONDS).getMethod());
            Assert.assertEquals("/api/v1/agent/parse/lt-1", server.takeRequest(1, TimeUnit.SECONDS).getPath());
            Assert.assertEquals("/api/v1/agent/parse/lt-1", server.takeRequest(1, TimeUnit.SECONDS).getPath());
            Assert.assertEquals("/md/full.md", server.takeRequest(1, TimeUnit.SECONDS).getPath());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void liteGetTaskRaisesRateLimitOn429() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setResponseCode(429).setBody("too many requests"));
        try {
            MinerUService service = service(server, false);
            service.liteGetTask("lt-1");
            Assert.fail("expected AiRateLimitException");
        } catch (AiRateLimitException e) {
            Assert.assertEquals(429, e.getStatusCode());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void liteWaitTaskThrowsOnFailed() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(json("{\"code\":0,\"data\":{\"task_id\":\"lt-1\",\"state\":\"failed\","
                + "\"err_code\":-30003,\"err_msg\":\"file page count exceeds lightweight API limit\"},\"msg\":\"ok\"}"));
        try {
            MinerUService service = service(server, false);
            service.liteWaitTask("lt-1");
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertTrue(e.getMessage().contains("exceeds lightweight API limit"));
            Assert.assertTrue(e.getMessage().contains("-30003"));
        } finally {
            server.shutdown();
        }
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private static MinerUService service(MockWebServer server, boolean withKey) {
        MinerUConfig config = new MinerUConfig();
        config.setBaseUrl(server.url("/api/v4").toString());
        config.setLiteBaseUrl(server.url("/api/v1/agent").toString());
        config.setPollIntervalMs(10);
        config.setPollTimeoutMs(5000);
        if (withKey) {
            config.setApiKey("test-key");
        }
        return new MinerUService(config, new OkHttpClient());
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static String taskJson(String state) {
        return "{\"code\":0,\"data\":{\"task_id\":\"t-1\",\"state\":\"" + state + "\"},\"msg\":\"ok\"}";
    }

    static byte[] zipBytes() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(out);
        zip.putNextEntry(new ZipEntry("full.md"));
        zip.write("# Doc\n".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.putNextEntry(new ZipEntry("doc_content_list.json"));
        zip.write("[{\"type\":\"text\"}]".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.putNextEntry(new ZipEntry("images/a.png"));
        zip.write(new byte[]{1, 2, 3});
        zip.closeEntry();
        zip.close();
        return out.toByteArray();
    }
}
