package io.github.lnyocly.ai4j.platform.openai.video;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
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

public class OpenAiVideoServiceTest {

    @Test
    public void test_create_video_uses_multipart_videos_endpoint() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"id\":\"video-1\",\"object\":\"video\",\"status\":\"queued\",\"created_at\":1764240518}"));
        server.start();
        try {
            OpenAiVideoService service = new OpenAiVideoService(configuration(server));
            Map<String, Object> extraFields = new LinkedHashMap<String, Object>();
            extraFields.put("enable_upsample", "true");
            VideoResponse response = service.create(VideoCreateRequest.builder()
                    .model("veo3.1")
                    .prompt("飞上天")
                    .seconds(8)
                    .size("1280x720")
                    .extraFields(extraFields)
                    .build());

            Assert.assertEquals("video-1", response.getId());
            Assert.assertEquals("queued", response.getStatus());
            Assert.assertEquals(Long.valueOf(1764240518L), response.getCreatedAt());

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertNotNull(request);
            Assert.assertEquals("/v1/videos", request.getPath());
            Assert.assertEquals("Bearer test-key", request.getHeader("Authorization"));
            Assert.assertTrue(request.getHeader("Content-Type").startsWith("multipart/form-data"));
            String body = request.getBody().readUtf8();
            Assert.assertTrue(body.contains("name=\"model\""));
            Assert.assertTrue(body.contains("veo3.1"));
            Assert.assertTrue(body.contains("name=\"prompt\""));
            Assert.assertTrue(body.contains("飞上天"));
            Assert.assertTrue(body.contains("name=\"enable_upsample\""));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_retrieve_video_parses_video_url_and_keeps_raw() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"id\":\"video-1\",\"object\":\"video\",\"status\":\"completed\",\"progress\":100,\"video_url\":\"https://cdn.example/video.mp4\",\"created_at\":1764240669}"));
        server.start();
        try {
            OpenAiVideoService service = new OpenAiVideoService(configuration(server));
            VideoResponse response = service.retrieve("video-1");

            Assert.assertEquals("completed", response.getStatus());
            Assert.assertEquals(Integer.valueOf(100), response.getProgress());
            Assert.assertEquals("https://cdn.example/video.mp4", response.getVideoUrl());
            Assert.assertEquals("video", response.getRaw().get("object"));

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/v1/videos/video-1", request.getPath());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_content_returns_readable_stream_and_encodes_id() throws Exception {
        MockWebServer server = new MockWebServer();
        byte[] expected = "fake-video".getBytes(StandardCharsets.UTF_8);
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "video/mp4").setBody(new okio.Buffer().write(expected)));
        server.start();
        try {
            OpenAiVideoService service = new OpenAiVideoService(configuration(server));
            byte[] actual;
            try (InputStream stream = service.content("video_1:openai/sora-2-t2v")) {
                actual = readAll(stream);
            }
            Assert.assertArrayEquals(expected, actual);

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/v1/videos/video_1%3Aopenai%2Fsora-2-t2v/content", request.getPath());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void test_remix_posts_prompt_json() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"id\":\"video-2\",\"object\":\"video\",\"status\":\"queued\",\"created_at\":1764240999}"));
        server.start();
        try {
            OpenAiVideoService service = new OpenAiVideoService(configuration(server));
            VideoResponse response = service.remix("video-1", "让背景变成蓝天");
            Assert.assertEquals("video-2", response.getId());

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/v1/videos/video-1/remix", request.getPath());
            JSONObject body = JSON.parseObject(request.getBody().readUtf8());
            Assert.assertEquals("让背景变成蓝天", body.getString("prompt"));
        } finally {
            server.shutdown();
        }
    }

    private static Configuration configuration(MockWebServer server) {
        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiHost(server.url("/").toString());
        openAiConfig.setApiKey("test-key");
        openAiConfig.setVideoUrl("v1/videos");

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

    private static byte[] readAll(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }
}
