package io.github.lnyocly.ai4j.platform.doubao.video;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.config.DoubaoConfig;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SeedanceVideoServiceTest {

    @Test
    public void test_create_posts_seedance_dialect_body() throws Exception {
        MockWebServer server = new MockWebServer();
        // Live shape (verified 2026-08-27 via notoken.pro gateway): id/task_id + queued.
        server.enqueue(jsonResponse("{\"id\":\"cgt-1\",\"task_id\":\"cgt-1\",\"status\":\"queued\",\"model\":\"seedance-2.0-mini\"}"));
        server.start();
        try {
            SeedanceVideoService service = new SeedanceVideoService(configuration(server));
            VideoResponse response = service.create(VideoCreateRequest.builder()
                    .model("seedance-2.0-mini")
                    .prompt("一只猫在月光下奔跑")
                    .inputImage("https://cdn.example/first.png")
                    .durationSeconds(4)
                    .resolution("480p")
                    .aspectRatio("16:9")
                    .build());

            Assert.assertEquals("cgt-1", response.getTaskId());
            Assert.assertEquals("SUBMITTED", response.getStatus());

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertNotNull(request);
            Assert.assertEquals("/api/v3/contents/generations/tasks", request.getPath());
            Assert.assertEquals("Bearer test-key", request.getHeader("Authorization"));
            Assert.assertTrue(request.getHeader("Content-Type").startsWith("application/json"));
            JSONObject body = JSON.parseObject(request.getBody().readUtf8());
            Assert.assertEquals("seedance-2.0-mini", body.getString("model"));
            Assert.assertEquals(Integer.valueOf(4), body.getInteger("duration"));
            Assert.assertEquals("480p", body.getString("resolution"));
            Assert.assertEquals("16:9", body.getString("ratio"));
            JSONArray content = body.getJSONArray("content");
            Assert.assertEquals(2, content.size());
            JSONObject text = content.getJSONObject(0);
            Assert.assertEquals("text", text.getString("type"));
            Assert.assertEquals("一只猫在月光下奔跑", text.getString("text"));
            JSONObject firstFrame = content.getJSONObject(1);
            Assert.assertEquals("image_url", firstFrame.getString("type"));
            Assert.assertEquals("https://cdn.example/first.png", firstFrame.getJSONObject("image_url").getString("url"));
            Assert.assertEquals("first_frame", firstFrame.getString("role"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_create_maps_reference_images_and_last_frame_roles() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"id\":\"cgt-2\",\"status\":\"queued\"}"));
        server.start();
        try {
            SeedanceVideoService service = new SeedanceVideoService(configuration(server));
            Map<String, Object> extra = new LinkedHashMap<String, Object>();
            extra.put("last_frame_url", "https://cdn.example/last.png");
            service.create(VideoCreateRequest.builder()
                    .model("seedance-2.5")
                    .prompt("p")
                    .inputImage("https://cdn.example/first.png")
                    .referenceImages(java.util.Arrays.asList("https://cdn.example/ref1.png", "https://cdn.example/ref2.png"))
                    .extraFields(extra)
                    .build());

            JSONObject body = JSON.parseObject(server.takeRequest(1, TimeUnit.SECONDS).getBody().readUtf8());
            // last_frame_url 是图片角色而非顶层参数
            Assert.assertFalse(body.containsKey("last_frame_url"));
            JSONArray content = body.getJSONArray("content");
            Assert.assertEquals(5, content.size());
            Assert.assertEquals("first_frame", content.getJSONObject(1).getString("role"));
            Assert.assertEquals("reference_image", content.getJSONObject(2).getString("role"));
            Assert.assertEquals("https://cdn.example/ref1.png", content.getJSONObject(2).getJSONObject("image_url").getString("url"));
            Assert.assertEquals("reference_image", content.getJSONObject(3).getString("role"));
            Assert.assertEquals("last_frame", content.getJSONObject(4).getString("role"));
            Assert.assertEquals("https://cdn.example/last.png", content.getJSONObject(4).getJSONObject("image_url").getString("url"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_create_merges_extra_fields_for_vendor_params() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"id\":\"cgt-3\",\"status\":\"queued\"}"));
        server.start();
        try {
            SeedanceVideoService service = new SeedanceVideoService(configuration(server));
            Map<String, Object> extra = new LinkedHashMap<String, Object>();
            extra.put("generate_audio", Boolean.TRUE);
            extra.put("watermark", Boolean.FALSE);
            extra.put("seed", 42);
            service.create(VideoCreateRequest.builder()
                    .model("seedance-2.0-mini")
                    .prompt("p")
                    .extraFields(extra)
                    .build());

            JSONObject body = JSON.parseObject(server.takeRequest(1, TimeUnit.SECONDS).getBody().readUtf8());
            Assert.assertEquals(Boolean.TRUE, body.getBoolean("generate_audio"));
            Assert.assertEquals(Boolean.FALSE, body.getBoolean("watermark"));
            Assert.assertEquals(Integer.valueOf(42), body.getInteger("seed"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_create_falls_back_to_legacy_seconds_for_duration() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"id\":\"cgt-4\",\"status\":\"queued\"}"));
        server.start();
        try {
            SeedanceVideoService service = new SeedanceVideoService(configuration(server));
            service.create(VideoCreateRequest.builder()
                    .model("seedance-1.5")
                    .prompt("p")
                    .seconds(8)
                    .build());

            JSONObject body = JSON.parseObject(server.takeRequest(1, TimeUnit.SECONDS).getBody().readUtf8());
            Assert.assertEquals(Integer.valueOf(8), body.getInteger("duration"));
            Assert.assertFalse(body.containsKey("seconds"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_create_task_id_field_alone_is_mapped() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"task_id\":\"cgt-5\",\"status\":\"queued\"}"));
        server.start();
        try {
            SeedanceVideoService service = new SeedanceVideoService(configuration(server));
            VideoResponse response = service.create(VideoCreateRequest.builder().model("seedance-2.0").prompt("p").build());
            Assert.assertEquals("cgt-5", response.getTaskId());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_retrieve_queued_and_processing_map_status_without_video() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"id\":\"cgt-6\",\"status\":\"queued\"}"));
        server.enqueue(jsonResponse("{\"id\":\"cgt-6\",\"status\":\"processing\"}"));
        server.start();
        try {
            SeedanceVideoService service = new SeedanceVideoService(configuration(server));
            Assert.assertEquals("SUBMITTED", service.retrieve("cgt-6").getStatus());
            VideoResponse processing = service.retrieve("cgt-6");
            Assert.assertEquals("RUNNING", processing.getStatus());
            Assert.assertNull(processing.getVideoUrl());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_retrieve_succeeded_extracts_nested_video_url() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse(
                "{\"id\":\"cgt-7\",\"status\":\"succeeded\",\"model\":\"seedance-2.0-mini\",\"content\":{\"video_url\":\"https://ark.example.com/output.mp4\"}}"));
        server.start();
        try {
            SeedanceVideoService service = new SeedanceVideoService(configuration(server));
            VideoResponse response = service.retrieve("cgt-7");

            Assert.assertEquals("SUCCEEDED", response.getStatus());
            Assert.assertEquals("https://ark.example.com/output.mp4", response.getVideoUrl());
            Assert.assertEquals("seedance-2.0-mini", response.getModel());
            Assert.assertEquals("cgt-7", response.getRaw().get("id"));

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/api/v3/contents/generations/tasks/cgt-7", request.getPath());
            Assert.assertEquals("Bearer test-key", request.getHeader("Authorization"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_retrieve_failed_maps_status_and_task_error() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse(
                "{\"id\":\"cgt-8\",\"status\":\"failed\",\"error\":{\"code\":400,\"message\":\"content policy violation\"}}"));
        server.start();
        try {
            SeedanceVideoService service = new SeedanceVideoService(configuration(server));
            VideoResponse response = service.retrieve("cgt-8");

            Assert.assertEquals("FAILED", response.getStatus());
            Assert.assertNull(response.getVideoUrl());
            Assert.assertEquals("400", response.getError().getCode());
            Assert.assertEquals("content policy violation", response.getErrorMessage());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_retrieve_http_error_surfaces_via_decoder() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"code\":\"NotFound\",\"message\":\"task not found\"}}"));
        server.start();
        try {
            SeedanceVideoService service = new SeedanceVideoService(configuration(server));
            service.retrieve("cgt-missing");
            Assert.fail("expected exception for 404");
        } catch (Exception expected) {
            Assert.assertTrue(expected.getMessage().contains("task not found")
                    || expected.getMessage().contains("404"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_content_downloads_video_stream() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(jsonResponse(
                "{\"id\":\"cgt-9\",\"status\":\"succeeded\",\"content\":{\"video_url\":\"" + server.url("/download.mp4") + "\"}}"));
        byte[] expected = "fake-seedance-video".getBytes(StandardCharsets.UTF_8);
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setBody(new okio.Buffer().write(expected)));
        try {
            SeedanceVideoService service = new SeedanceVideoService(configuration(server));
            byte[] actual;
            try (InputStream stream = service.content("cgt-9")) {
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
    public void test_create_uses_explicit_base_url_and_api_key_overrides() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"id\":\"cgt-10\",\"status\":\"queued\"}"));
        server.start();
        try {
            // 网关场景：base/key 全部由调用方传入，配置留空
            DoubaoConfig doubaoConfig = new DoubaoConfig();
            Configuration configuration = new Configuration();
            configuration.setDoubaoConfig(doubaoConfig);
            configuration.setOkHttpClient(new OkHttpClient());
            SeedanceVideoService service = new SeedanceVideoService(configuration);
            VideoResponse response = service.create(server.url("/").toString(), "gateway-key",
                    VideoCreateRequest.builder().model("seedance-2.0-mini").prompt("p").build());

            Assert.assertEquals("cgt-10", response.getTaskId());
            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/api/v3/contents/generations/tasks", request.getPath());
            Assert.assertEquals("Bearer gateway-key", request.getHeader("Authorization"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_remix_is_unsupported() {
        SeedanceVideoService service = new SeedanceVideoService(configuration(new MockWebServer()));
        try {
            service.remix("cgt-1", "p");
            Assert.fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Seedance 无 remix 端点
        }
    }

    private static Configuration configuration(MockWebServer server) {
        DoubaoConfig doubaoConfig = new DoubaoConfig();
        doubaoConfig.setApiKey("chat-key");
        doubaoConfig.setVideoApiHost(server.url("/").toString());
        doubaoConfig.setVideoApiKey("test-key");

        Configuration configuration = new Configuration();
        configuration.setDoubaoConfig(doubaoConfig);
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
