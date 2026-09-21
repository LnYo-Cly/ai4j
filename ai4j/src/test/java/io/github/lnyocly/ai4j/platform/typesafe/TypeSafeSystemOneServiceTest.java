package io.github.lnyocly.ai4j.platform.typesafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.TypeSafeConfig;
import io.github.lnyocly.ai4j.exception.AiAuthException;
import io.github.lnyocly.ai4j.platform.typesafe.systemone.TypeSafeSystemOneService;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.systemone.entity.ChoiceQuestion;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneAnswer;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneModelCard;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneQuestion;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneRequest;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneResponse;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TypeSafeSystemOneServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private TypeSafeSystemOneService service(MockWebServer server) {
        Configuration configuration = new Configuration();
        configuration.setOkHttpClient(new OkHttpClient());
        TypeSafeConfig config = new TypeSafeConfig();
        config.setApiHost(server.url("/").toString());
        config.setApiKey("test-key");
        return new TypeSafeSystemOneService(configuration, config);
    }

    private static SystemOneRequest sampleRequest() {
        Map<String, Object> criteria = new LinkedHashMap<String, Object>();
        criteria.put("a", "option a");
        criteria.put("b", "option b");
        Map<String, SystemOneQuestion> questions = new LinkedHashMap<String, SystemOneQuestion>();
        questions.put("pick", ChoiceQuestion.of("choose", criteria));
        return SystemOneRequest.of("some state", null, questions);
    }

    @Test
    public void shouldPostEvaluationWithBearerAndDefaultModel() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"model\":\"jev-latest\",\"answers\":{\"pick\":{\"type\":\"choice\",\"choice\":\"a\",\"confidence\":0.9}}}"));
        server.start();
        try {
            SystemOneResponse response = service(server).evaluate(sampleRequest());

            Assert.assertEquals("a", response.getAnswers().get("pick").getChoice());
            Assert.assertEquals(0.9, response.getAnswers().get("pick").getConfidence(), 1e-6);

            RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertNotNull(recorded);
            Assert.assertEquals("/v1/systemone", recorded.getPath());
            Assert.assertEquals("Bearer test-key", recorded.getHeader("Authorization"));

            JsonNode body = mapper.readTree(recorded.getBody().readUtf8());
            Assert.assertEquals("jev-latest", body.get("model").asText());
            Assert.assertEquals("choice", body.get("questions").get("pick").get("type").asText());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void shouldListModels() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"models\":[{\"name\":\"jev-latest\",\"description\":\"Jev\",\"release_date\":\"2026-09-10T18:38:01Z\"}]}"));
        server.start();
        try {
            List<SystemOneModelCard> models = service(server).listModels();
            Assert.assertEquals(1, models.size());
            Assert.assertEquals("jev-latest", models.get(0).getName());
            Assert.assertEquals("2026-09-10T18:38:01Z", models.get(0).getReleaseDate());

            RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
            Assert.assertEquals("/v1/models", recorded.getPath());
            Assert.assertEquals("GET", recorded.getMethod());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void shouldDecodeHttpErrors() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"bad key\"}"));
        server.start();
        try {
            service(server).evaluate(sampleRequest());
            Assert.fail("expected AiAuthException");
        } catch (AiAuthException e) {
            Assert.assertEquals(401, e.getStatusCode());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void shouldRejectEmptyQuestions() throws Exception {
        TypeSafeSystemOneService service = new TypeSafeSystemOneService(new Configuration(), new TypeSafeConfig());
        try {
            service.evaluate(SystemOneRequest.of("s", "m", Collections.<String, SystemOneQuestion>emptyMap()));
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
