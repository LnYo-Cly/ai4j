package io.github.lnyocly.ai4j.platform.agnes.video;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.exception.AiClientException;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoCreateRequest;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class AgnesVideoServiceTest {

    @Test
    public void test_create_text_mode_posts_agnes_dialect() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"id\":\"task-1\",\"video_id\":\"video-1\",\"task_id\":\"task-1\",\"object\":\"video\",\"status\":\"queued\"}"));
        server.start();
        try {
            AgnesVideoService service = new AgnesVideoService(configuration(server));
            VideoResponse response = service.create(VideoCreateRequest.builder()
                    .model("agnes-video-2.5-flash")
                    .prompt("a paper boat")
                    .durationSeconds(4)
                    .aspectRatio("16:9")
                    .build());

            // video_id is the polling key, not the task id.
            Assert.assertEquals("video-1", response.getTaskId());

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertNotNull(request);
            Assert.assertEquals("/v1/videos", request.getPath());
            Assert.assertEquals("Bearer test-key", request.getHeader("Authorization"));
            JSONObject body = JSON.parseObject(request.getBody().readUtf8());
            Assert.assertEquals("agnes-video-2.5-flash", body.getString("model"));
            Assert.assertEquals("text", body.getString("mode"));
            Assert.assertEquals("4", body.getString("seconds"));
            Assert.assertEquals("720P", body.getString("size"));
            Assert.assertEquals("16:9", body.getString("aspect_ratio"));
            Assert.assertEquals(Integer.valueOf(1), body.getInteger("n"));
            Assert.assertFalse(body.containsKey("first_frame"));
            Assert.assertFalse(body.containsKey("images"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_create_keyframe_maps_first_and_last_frame() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"video_id\":\"video-2\",\"status\":\"queued\"}"));
        server.start();
        try {
            AgnesVideoService service = new AgnesVideoService(configuration(server));
            service.create(VideoCreateRequest.builder()
                    .model("agnes-video-2.5-flash")
                    .prompt("walk to the window")
                    .inputImage("https://cdn.example/first.png")
                    .lastFrame("https://cdn.example/last.png")
                    .build());

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            JSONObject body = JSON.parseObject(request.getBody().readUtf8());
            Assert.assertEquals("keyframe", body.getString("mode"));
            Assert.assertEquals("https://cdn.example/first.png", body.getString("first_frame"));
            Assert.assertEquals("https://cdn.example/last.png", body.getString("last_frame"));
            Assert.assertFalse(body.containsKey("images"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_create_reference_maps_images_and_audios() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"video_id\":\"video-3\",\"status\":\"queued\"}"));
        server.start();
        try {
            AgnesVideoService service = new AgnesVideoService(configuration(server));
            service.create(VideoCreateRequest.builder()
                    .model("agnes-video-2.5-flash")
                    .prompt("use <Picture 1> and <Audio 1>")
                    .referenceImages(Arrays.asList("https://cdn.example/a.png", "https://cdn.example/b.png"))
                    .referenceAudios(Collections.singletonList("https://cdn.example/beat.mp3"))
                    .build());

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            JSONObject body = JSON.parseObject(request.getBody().readUtf8());
            Assert.assertEquals("reference", body.getString("mode"));
            Assert.assertEquals(2, body.getJSONArray("images").size());
            Assert.assertEquals("https://cdn.example/beat.mp3", body.getJSONArray("audios").getString(0));
            Assert.assertFalse(body.containsKey("first_frame"));
            Assert.assertFalse(body.containsKey("videos"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_retrieve_uses_agnesapi_with_model_name() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"id\":\"task-1\",\"video_id\":\"video-1\",\"status\":\"completed\",\"progress\":100,"
                + "\"metadata\":{\"url\":\"https://cdn.example/out.mp4\"}}"));
        server.start();
        try {
            AgnesVideoService service = new AgnesVideoService(configuration(server));
            VideoResponse response = service.retrieve(null, null, "video-1", "agnes-video-2.5-flash");

            Assert.assertEquals("completed", response.getStatus());
            Assert.assertEquals("https://cdn.example/out.mp4", response.getVideoUrl());
            Assert.assertEquals("video-1", response.getTaskId());

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/agnesapi?video_id=video-1&model_name=agnes-video-2.5-flash", request.getPath());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_retrieve_without_model_sends_bare_video_id() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"video_id\":\"video-1\",\"status\":\"in_progress\",\"progress\":40}"));
        server.start();
        try {
            AgnesVideoService service = new AgnesVideoService(configuration(server));
            VideoResponse response = service.retrieve("video-1");

            Assert.assertEquals("in_progress", response.getStatus());
            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/agnesapi?video_id=video-1", request.getPath());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_create_size_validation_error_surfaces_detail() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"detail\":\"size must be 720P\"}"));
        server.start();
        try {
            AgnesVideoService service = new AgnesVideoService(configuration(server));
            AiClientException error = Assert.assertThrows(AiClientException.class, () ->
                    service.create(VideoCreateRequest.builder()
                            .model("agnes-video-2.5-flash")
                            .prompt("x")
                            .size("1080P")
                            .build()));
            Assert.assertTrue(error.getMessage().contains("size must be 720P"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_remix_is_unsupported() {
        AgnesVideoService service = new AgnesVideoService(configuration(null));
        Assert.assertThrows(UnsupportedOperationException.class, () -> service.remix("video-1", "x"));
    }

    private static Configuration configuration(MockWebServer server) {
        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiHost(server == null ? "https://apihub.agnes-ai.com/" : server.url("/").toString());
        openAiConfig.setApiKey("test-key");
        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(openAiConfig);
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
