package io.github.lnyocly.ai4j.platform.grok.video;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.platform.openai.video.OpenAiVideoServiceTest;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoCreateRequest;
import io.github.lnyocly.ai4j.platform.openai.video.entity.VideoResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GrokVideoServiceTest {

    @Test
    public void test_create_posts_json_to_videos_generations() throws Exception {
        MockWebServer server = new MockWebServer();
        // Live gateway shape (verified 2026-08-19): create returns ONLY request_id.
        server.enqueue(OpenAiVideoServiceTest.jsonResponse("{\"request_id\":\"video_zoobap6\"}"));
        server.start();
        try {
            GrokVideoService service = new GrokVideoService(OpenAiVideoServiceTest.configuration(server));
            List<String> references = new ArrayList<String>();
            references.add("https://cdn.example/ref-1.png");

            VideoResponse response = service.create(VideoCreateRequest.builder()
                    .model("grok-imagine-video")
                    .prompt("城市屋顶的星空")
                    .durationSeconds(6)
                    .aspectRatio("16:9")
                    .resolution("720p")
                    .inputImage("https://cdn.example/first.png")
                    .referenceImages(references)
                    .build());

            Assert.assertNull(response.getId());
            Assert.assertEquals("video_zoobap6", response.getRequestId());
            Assert.assertEquals("video_zoobap6", response.getTaskId());

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertNotNull(request);
            Assert.assertEquals("/v1/videos/generations", request.getPath());
            Assert.assertTrue(request.getHeader("Content-Type").startsWith("application/json"));

            JSONObject body = JSON.parseObject(request.getBody().readUtf8());
            Assert.assertEquals("grok-imagine-video", body.getString("model"));
            Assert.assertEquals("城市屋顶的星空", body.getString("prompt"));
            Assert.assertEquals(Integer.valueOf(6), body.getInteger("duration"));
            Assert.assertEquals("16:9", body.getString("aspect_ratio"));
            Assert.assertEquals("720p", body.getString("resolution"));
            Assert.assertEquals("https://cdn.example/first.png", body.getString("image"));
            JSONArray refs = body.getJSONArray("reference_images");
            Assert.assertEquals(1, refs.size());
            Assert.assertEquals("https://cdn.example/ref-1.png", refs.getString(0));
            Assert.assertNull(body.getString("seconds"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_retrieve_surfaces_task_failure_returned_with_http_200() throws Exception {
        MockWebServer server = new MockWebServer();
        // Live gateway shape (verified 2026-08-19): failures come back as HTTP 200 + error body.
        server.enqueue(OpenAiVideoServiceTest.jsonResponse(
                "{\"error\":{\"code\":\"internal_error\",\"message\":\"Console 媒体上游返回 429: Free usage quota exceeded\"},\"status\":\"failed\"}"));
        server.start();
        try {
            GrokVideoService service = new GrokVideoService(OpenAiVideoServiceTest.configuration(server));
            VideoResponse response = service.retrieve("video_zoobap6");

            Assert.assertEquals("failed", response.getStatus());
            Assert.assertNull(response.getVideoUrl());
            Assert.assertNotNull(response.getError());
            Assert.assertEquals("internal_error", response.getError().getCode());
            Assert.assertTrue(response.getErrorMessage().contains("429"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_retrieve_polls_videos_request_id_without_generations_suffix() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(OpenAiVideoServiceTest.jsonResponse("{\"id\":\"req-1\",\"status\":\"completed\",\"video_url\":\"https://cdn.example/out.mp4\"}"));
        server.start();
        try {
            GrokVideoService service = new GrokVideoService(OpenAiVideoServiceTest.configuration(server));
            VideoResponse response = service.retrieve("req-1");

            Assert.assertEquals("completed", response.getStatus());
            Assert.assertEquals("https://cdn.example/out.mp4", response.getVideoUrl());

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/v1/videos/req-1", request.getPath());
        } finally {
            server.shutdown();
        }
    }
}
