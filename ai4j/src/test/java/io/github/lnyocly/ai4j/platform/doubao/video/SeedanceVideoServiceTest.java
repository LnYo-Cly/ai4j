package io.github.lnyocly.ai4j.platform.doubao.video;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.platform.openai.video.OpenAiVideoServiceTest;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoCreateRequest;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoReference;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Shapes below mirror a live Ark-contract run (2026-08-19): create returns only
 * {@code {"id": "cgt-..."}} and the finished task nests the asset under
 * {@code content.video_url} while reporting {@code status: succeeded}.
 */
public class SeedanceVideoServiceTest {

    @Test
    public void test_create_posts_content_array_to_ark_task_path() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(OpenAiVideoServiceTest.jsonResponse("{\"id\":\"cgt-20260819155207-n9qmw\"}"));
        server.start();
        try {
            SeedanceVideoService service = new SeedanceVideoService(OpenAiVideoServiceTest.configuration(server));
            List<VideoReference> references = new ArrayList<VideoReference>();
            references.add(VideoReference.lastFrame("https://cdn.example/last.png"));
            references.add(VideoReference.video("https://cdn.example/ref.mp4"));
            references.add(VideoReference.audio("https://cdn.example/bgm.mp3"));

            VideoResponse response = service.create(VideoCreateRequest.builder()
                    .model("doubao-seedance-2-0-fast-real_480p")
                    .prompt("柠檬在木桌上滚动")
                    .durationSeconds(4)
                    .aspectRatio("16:9")
                    .resolution("480p")
                    .generateAudio(Boolean.TRUE)
                    .watermark(Boolean.FALSE)
                    .inputImage("https://cdn.example/first.png")
                    .references(references)
                    .build());

            Assert.assertEquals("cgt-20260819155207-n9qmw", response.getTaskId());

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertNotNull(request);
            Assert.assertEquals("/api/v3/contents/generations/tasks", request.getPath());
            Assert.assertTrue(request.getHeader("Content-Type").startsWith("application/json"));

            JSONObject body = JSON.parseObject(request.getBody().readUtf8());
            Assert.assertEquals("doubao-seedance-2-0-fast-real_480p", body.getString("model"));
            Assert.assertEquals(Integer.valueOf(4), body.getInteger("duration"));
            Assert.assertEquals("16:9", body.getString("ratio"));
            Assert.assertEquals("480p", body.getString("resolution"));
            Assert.assertEquals(Boolean.TRUE, body.getBoolean("generate_audio"));
            Assert.assertEquals(Boolean.FALSE, body.getBoolean("watermark"));
            // Ark carries prompt and references as typed content parts, not top-level fields.
            Assert.assertNull(body.getString("prompt"));

            JSONArray content = body.getJSONArray("content");
            Assert.assertEquals(5, content.size());
            Assert.assertEquals("text", content.getJSONObject(0).getString("type"));
            Assert.assertEquals("柠檬在木桌上滚动", content.getJSONObject(0).getString("text"));

            JSONObject first = content.getJSONObject(1);
            Assert.assertEquals("image_url", first.getString("type"));
            Assert.assertEquals("first_frame", first.getString("role"));
            Assert.assertEquals("https://cdn.example/first.png", first.getJSONObject("image_url").getString("url"));

            Assert.assertEquals("last_frame", content.getJSONObject(2).getString("role"));
            Assert.assertEquals("video_url", content.getJSONObject(3).getString("type"));
            Assert.assertEquals("reference_video", content.getJSONObject(3).getString("role"));
            Assert.assertEquals("audio_url", content.getJSONObject(4).getString("type"));
            Assert.assertEquals("reference_audio", content.getJSONObject(4).getString("role"));
            Assert.assertEquals("https://cdn.example/bgm.mp3",
                    content.getJSONObject(4).getJSONObject("audio_url").getString("url"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_retrieve_reads_nested_content_video_url_on_same_task_path() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(OpenAiVideoServiceTest.jsonResponse(
                "{\"id\":\"cgt-1\",\"model\":\"doubao-seedance-1-5-pro-251215\",\"status\":\"succeeded\","
                        + "\"content\":{\"video_url\":\"https://tos.example/out.mp4\"},"
                        + "\"usage\":{\"completion_tokens\":40594,\"total_tokens\":40594}}"));
        server.start();
        try {
            SeedanceVideoService service = new SeedanceVideoService(OpenAiVideoServiceTest.configuration(server));
            VideoResponse response = service.retrieve("cgt-1");

            Assert.assertEquals("succeeded", response.getStatus());
            Assert.assertNull(response.getVideoUrl());
            Assert.assertEquals("https://tos.example/out.mp4", response.resolveVideoUrl());
            Assert.assertEquals(40594, ((Number) response.getUsage().get("total_tokens")).intValue());

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/api/v3/contents/generations/tasks/cgt-1", request.getPath());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_relay_path_prefix_is_configurable() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(OpenAiVideoServiceTest.jsonResponse("{\"id\":\"cgt-2\"}"));
        server.enqueue(OpenAiVideoServiceTest.jsonResponse("{\"id\":\"cgt-2\",\"status\":\"running\"}"));
        server.start();
        try {
            SeedanceVideoService service = new SeedanceVideoService(
                    OpenAiVideoServiceTest.configuration(server), "volcengine/api/v3/contents/generations/tasks");
            service.create(VideoCreateRequest.builder().model("m").prompt("p").durationSeconds(4).build());
            service.retrieve("cgt-2");

            Assert.assertEquals("/volcengine/api/v3/contents/generations/tasks",
                    server.takeRequest(1, TimeUnit.SECONDS).getPath());
            Assert.assertEquals("/volcengine/api/v3/contents/generations/tasks/cgt-2",
                    server.takeRequest(1, TimeUnit.SECONDS).getPath());
        } finally {
            server.shutdown();
        }
    }
}
