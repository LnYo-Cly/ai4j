package io.github.lnyocly.ai4j.platform.minimax.video;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.config.MinimaxConfig;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoCreateRequest;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class MinimaxVideoServiceTest {

    @Test
    public void test_create_posts_minimax_dialect_body() throws Exception {
        MockWebServer server = new MockWebServer();
        // Live shape (verified 2026-08-25): HTTP 200 + task_id + base_resp.
        server.enqueue(jsonResponse("{\"task_id\":\"435016732930334\",\"base_resp\":{\"status_code\":0,\"status_msg\":\"success\"}}"));
        server.start();
        try {
            MinimaxVideoService service = new MinimaxVideoService(configuration(server));
            VideoResponse response = service.create(VideoCreateRequest.builder()
                    .model("MiniMax-Hailuo-2.3")
                    .prompt("一只猫在月光下奔跑")
                    .inputImage("https://cdn.example/first.png")
                    .build());

            Assert.assertEquals("435016732930334", response.getTaskId());
            Assert.assertNull(response.getError());

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertNotNull(request);
            Assert.assertEquals("/v1/video_generation", request.getPath());
            Assert.assertEquals("Bearer test-key", request.getHeader("Authorization"));
            Assert.assertTrue(request.getHeader("Content-Type").startsWith("application/json"));
            JSONObject body = JSON.parseObject(request.getBody().readUtf8());
            Assert.assertEquals("MiniMax-Hailuo-2.3", body.getString("model"));
            Assert.assertEquals("一只猫在月光下奔跑", body.getString("prompt"));
            Assert.assertEquals("https://cdn.example/first.png", body.getString("first_frame_image"));
            Assert.assertEquals(Boolean.FALSE, body.getBoolean("promptenhance"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_create_merges_extra_fields_for_vendor_params() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"task_id\":\"t-1\",\"base_resp\":{\"status_code\":0,\"status_msg\":\"success\"}}"));
        server.start();
        try {
            MinimaxVideoService service = new MinimaxVideoService(configuration(server));
            java.util.Map<String, Object> extra = new java.util.LinkedHashMap<String, Object>();
            extra.put("promptenhance", Boolean.TRUE);
            extra.put("duration", 6);
            service.create(VideoCreateRequest.builder()
                    .model("MiniMax-Hailuo-2.3")
                    .prompt("p")
                    .extraFields(extra)
                    .build());

            JSONObject body = JSON.parseObject(server.takeRequest(1, TimeUnit.SECONDS).getBody().readUtf8());
            Assert.assertEquals(Boolean.TRUE, body.getBoolean("promptenhance"));
            Assert.assertEquals(Integer.valueOf(6), body.getInteger("duration"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_create_surfaces_base_resp_error_returned_with_http_200() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"base_resp\":{\"status_code\":1004,\"status_msg\":\"invalid api key\"}}"));
        server.start();
        try {
            MinimaxVideoService service = new MinimaxVideoService(configuration(server));
            VideoResponse response = service.create(VideoCreateRequest.builder()
                    .model("MiniMax-Hailuo-2.3")
                    .prompt("p")
                    .build());

            Assert.assertEquals("failed", response.getStatus());
            Assert.assertEquals("1004", response.getError().getCode());
            Assert.assertEquals("invalid api key", response.getErrorMessage());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_retrieve_processing_maps_status_and_skips_file_lookup() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse(
                "{\"task_id\":\"t-1\",\"status\":\"Processing\",\"base_resp\":{\"status_code\":0,\"status_msg\":\"\"}}"));
        server.start();
        try {
            MinimaxVideoService service = new MinimaxVideoService(configuration(server));
            VideoResponse response = service.retrieve("t-1");

            Assert.assertEquals("processing", response.getStatus());
            Assert.assertNull(response.getVideoUrl());

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/v1/query/video_generation?task_id=t-1", request.getPath());
            // 未成功时不应触发 files/retrieve
            Assert.assertNull(server.takeRequest(1, TimeUnit.SECONDS));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_retrieve_success_resolves_download_url_via_files_retrieve() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse(
                "{\"task_id\":\"t-1\",\"status\":\"Success\",\"file_id\":\"123\",\"video_width\":1366,\"video_height\":768,\"base_resp\":{\"status_code\":0,\"status_msg\":\"success\"}}"));
        server.enqueue(jsonResponse(
                "{\"file\":{\"file_id\":123,\"download_url\":\"https://public-cdn.example.com/output_aigc.mp4\",\"filename\":\"output_aigc.mp4\",\"bytes\":0,\"purpose\":\"video_generation\"}}"));
        server.start();
        try {
            MinimaxVideoService service = new MinimaxVideoService(configuration(server));
            VideoResponse response = service.retrieve("t-1");

            Assert.assertEquals("succeeded", response.getStatus());
            Assert.assertEquals("https://public-cdn.example.com/output_aigc.mp4", response.getVideoUrl());
            Assert.assertEquals("1366x768", response.getSize());
            Assert.assertEquals("t-1", response.getRaw().get("task_id"));

            server.takeRequest(1, TimeUnit.SECONDS);
            RecordedRequest fileRequest = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/v1/files/retrieve?file_id=123", fileRequest.getPath());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_retrieve_fail_maps_status_and_error_message() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse(
                "{\"task_id\":\"t-1\",\"status\":\"Fail\",\"base_resp\":{\"status_code\":3001,\"status_msg\":\"content policy violation\"}}"));
        server.start();
        try {
            MinimaxVideoService service = new MinimaxVideoService(configuration(server));
            VideoResponse response = service.retrieve("t-1");

            Assert.assertEquals("failed", response.getStatus());
            Assert.assertNull(response.getVideoUrl());
            Assert.assertEquals("3001", response.getError().getCode());
            Assert.assertEquals("content policy violation", response.getErrorMessage());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_content_downloads_video_stream() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(jsonResponse(
                "{\"task_id\":\"t-1\",\"status\":\"Success\",\"file_id\":\"123\",\"base_resp\":{\"status_code\":0,\"status_msg\":\"success\"}}"));
        server.enqueue(jsonResponse(
                "{\"file\":{\"file_id\":123,\"download_url\":\"" + server.url("/download.mp4") + "\"}}"));
        byte[] expected = "fake-hailuo-video".getBytes(StandardCharsets.UTF_8);
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setBody(new okio.Buffer().write(expected)));
        try {
            MinimaxVideoService service = new MinimaxVideoService(configuration(server));
            byte[] actual;
            try (InputStream stream = service.content("t-1")) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[256];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                actual = out.toByteArray();
            }
            Assert.assertArrayEquals(expected, actual);
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_remix_is_unsupported() {
        MinimaxConfig minimaxConfig = new MinimaxConfig();
        minimaxConfig.setVideoApiHost("https://api.minimax.chat");
        Configuration configuration = new Configuration();
        configuration.setMinimaxConfig(minimaxConfig);
        configuration.setOkHttpClient(new OkHttpClient());
        MinimaxVideoService service = new MinimaxVideoService(configuration);
        try {
            service.remix("t-1", "p");
            Assert.fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // MiniMax 无 remix 端点
        }
    }

    private static Configuration configuration(MockWebServer server) {
        MinimaxConfig minimaxConfig = new MinimaxConfig();
        minimaxConfig.setApiHost("https://api.minimaxi.com/");
        minimaxConfig.setApiKey("chat-key");
        minimaxConfig.setVideoApiHost(server.url("/").toString());
        minimaxConfig.setVideoApiKey("test-key");

        Configuration configuration = new Configuration();
        configuration.setMinimaxConfig(minimaxConfig);
        configuration.setOkHttpClient(new OkHttpClient());
        return configuration;
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
