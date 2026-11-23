package io.github.lnyocly.ai4j.platform.suno.music;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.config.SunoConfig;
import io.github.lnyocly.ai4j.platform.suno.music.entity.SunoFetchResponse;
import io.github.lnyocly.ai4j.platform.suno.music.entity.SunoLyricsRequest;
import io.github.lnyocly.ai4j.platform.suno.music.entity.SunoMusicRequest;
import io.github.lnyocly.ai4j.platform.suno.music.entity.SunoSong;
import io.github.lnyocly.ai4j.platform.suno.music.entity.SunoSubmitResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SunoMusicServiceTest {

    @Test
    public void submitMusicPostsChatFireSunoJson() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"code\":\"success\",\"data\":\"task-123\",\"message\":\"\"}"));
        server.start();
        try {
            SunoMusicService service = new SunoMusicService(configuration(server));
            SunoSubmitResponse response = service.submitMusic(SunoMusicRequest.builder()
                    .prompt("[Verse] city lights")
                    .tags("emotional punk")
                    .mv("chirp-v4")
                    .title("City Lights")
                    .makeInstrumental(Boolean.FALSE)
                    .gptDescriptionPrompt("write a song")
                    .build());

            Assert.assertTrue(response.isSuccess());
            Assert.assertEquals("task-123", response.getData());
            Assert.assertEquals("task-123", response.getRaw().get("data"));

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertNotNull(request);
            Assert.assertEquals("/suno/submit/music", request.getPath());
            Assert.assertEquals("Bearer test-key", request.getHeader("Authorization"));
            Assert.assertTrue(request.getHeader("Content-Type").startsWith("application/json"));
            JsonNode body = JsonCodec.readTree(request.getBody().readUtf8());
            Assert.assertEquals("[Verse] city lights", body.path("prompt").asText());
            Assert.assertEquals("emotional punk", body.path("tags").asText());
            Assert.assertEquals("chirp-v4", body.path("mv").asText());
            Assert.assertEquals("City Lights", body.path("title").asText());
            Assert.assertFalse(body.path("make_instrumental").asBoolean());
            Assert.assertEquals("write a song", body.path("gpt_description_prompt").asText());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void submitLyricsPostsLyricsEndpoint() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"code\":\"success\",\"data\":\"lyrics-task\",\"message\":\"\"}"));
        server.start();
        try {
            SunoMusicService service = new SunoMusicService(configuration(server));
            SunoSubmitResponse response = service.submitLyrics(SunoLyricsRequest.builder()
                    .prompt("chat fire")
                    .extraFields(Collections.<String, Object>singletonMap("language", "zh"))
                    .build());

            Assert.assertEquals("lyrics-task", response.getData());

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/suno/submit/lyrics", request.getPath());
            Assert.assertEquals("Bearer test-key", request.getHeader("Authorization"));
            JsonNode body = JsonCodec.readTree(request.getBody().readUtf8());
            Assert.assertEquals("chat fire", body.path("prompt").asText());
            Assert.assertEquals("zh", body.path("language").asText());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void fetchEncodesTaskIdAndKeepsResultJson() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"code\":\"success\",\"message\":\"\",\"data\":{\"task_id\":\"task 1/2\",\"action\":\"MUSIC\",\"status\":\"SUCCESS\",\"progress\":\"100%\",\"data\":[{\"id\":\"song-1\",\"title\":\"City Lights\",\"audio_url\":\"https://cdn.example/song.mp3\",\"image_url\":\"https://cdn.example/song.jpg\",\"model_name\":\"chirp-v4\"}]}}"));
        server.start();
        try {
            SunoMusicService service = new SunoMusicService(configuration(server));
            SunoFetchResponse response = service.fetch("task 1/2");

            Assert.assertTrue(response.isSuccess());
            Assert.assertEquals("task 1/2", response.getData().getTaskId());
            Assert.assertEquals("MUSIC", response.getData().getAction());
            JsonNode data = response.getData().getData();
            Assert.assertTrue(data.isArray());
            SunoSong song = JsonCodec.treeToValue(data.get(0), SunoSong.class);
            Assert.assertEquals("City Lights", song.getTitle());
            Assert.assertEquals("https://cdn.example/song.mp3", song.getAudioUrl());

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/suno/fetch/task%201%2F2", request.getPath());
            Assert.assertEquals("Bearer test-key", request.getHeader("Authorization"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void fetchSupportsPlaceholderEndpoint() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(jsonResponse("{\"code\":\"success\",\"message\":\"\",\"data\":{\"task_id\":\"task:abc\",\"status\":\"IN_PROGRESS\"}}"));
        server.start();
        try {
            SunoConfig sunoConfig = new SunoConfig();
            sunoConfig.setApiHost(server.url("/").toString());
            sunoConfig.setApiKey("test-key");
            sunoConfig.setFetchUrl("suno/fetch/{task_id}");

            Configuration configuration = new Configuration();
            configuration.setSunoConfig(sunoConfig);
            configuration.setOkHttpClient(new OkHttpClient());

            SunoMusicService service = new SunoMusicService(configuration);
            SunoFetchResponse response = service.fetch("task:abc");
            Assert.assertEquals("IN_PROGRESS", response.getData().getStatus());

            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/suno/fetch/task%3Aabc", request.getPath());
        } finally {
            server.shutdown();
        }
    }

    private static Configuration configuration(MockWebServer server) {
        SunoConfig sunoConfig = new SunoConfig();
        sunoConfig.setApiHost(server.url("/").toString());
        sunoConfig.setApiKey("test-key");
        sunoConfig.setMusicUrl("suno/submit/music");
        sunoConfig.setLyricsUrl("suno/submit/lyrics");
        sunoConfig.setFetchUrl("suno/fetch");

        Configuration configuration = new Configuration();
        configuration.setSunoConfig(sunoConfig);
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
