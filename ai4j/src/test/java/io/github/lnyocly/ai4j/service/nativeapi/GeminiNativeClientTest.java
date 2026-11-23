package io.github.lnyocly.ai4j.service.nativeapi;

import io.github.lnyocly.ai4j.exception.AiRateLimitException;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Contract tests for {@link GeminiNativeClient}, {@link NativeGeminiStreamEvent},
 * and {@link NativeGeminiStreamListener}.
 */
public class GeminiNativeClientTest {

    private MockWebServer server;
    private Configuration configuration;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        configuration = new Configuration();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void generateContentSyncSupportsApiKeyAndBearer() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"responseId\":\"gem_1\",\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]},\"finishReason\":\"STOP\"}]}"));

        GeminiNativeClient apiKeyClient = GeminiNativeClient.of(server.url("/").toString(), "key-abc", configuration);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("contents", Collections.singletonList(Collections.singletonMap("role", "user")));
        Map<String, Object> resp = apiKeyClient.generateContent("gemini-2.5-flash", body);

        RecordedRequest req1 = server.takeRequest();
        Assert.assertEquals("key-abc", req1.getHeader("x-goog-api-key"));
        Assert.assertEquals("/v1beta/models/gemini-2.5-flash:generateContent", req1.getPath());
        Assert.assertEquals("gem_1", resp.get("responseId"));

        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"responseId\":\"gem_2\"}"));
        GeminiNativeClient bearerClient = GeminiNativeClient.ofBearer(server.url("/").toString(), "bearer-xyz", configuration);
        bearerClient.generateContent("models/gemini-2.5-pro", body);
        RecordedRequest req2 = server.takeRequest();
        Assert.assertEquals("Bearer bearer-xyz", req2.getHeader("Authorization"));
        Assert.assertEquals("/v1beta/models/gemini-2.5-pro:generateContent", req2.getPath());
    }

    @Test
    public void streamGenerateContentDeliversTypedEventsAndHandlesErrors() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"responseId\":\"g1\",\"candidates\":[{\"index\":0,\"content\":{\"parts\":[{\"thought\":true,\"text\":\"reasoning\",\"thoughtSignature\":\"sig_1\"}]}}]}\n\n"
                        + "data: {\"candidates\":[{\"index\":0,\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"get_time\",\"args\":{}},\"thoughtSignature\":\"sig_2\"},{\"text\":\"done\"}]}}]}\n\n"
                        + "data: {\"candidates\":[{\"index\":0,\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":5}}\n\n"));

        GeminiNativeClient client = GeminiNativeClient.of(server.url("/").toString(), "key-abc", configuration);
        final List<NativeGeminiStreamEvent> events = Collections.synchronizedList(new ArrayList<NativeGeminiStreamEvent>());
        final List<Throwable> failures = Collections.synchronizedList(new ArrayList<Throwable>());
        NativeStreamCall call = client.streamGenerateContent(
                "gemini-2.5-flash",
                Collections.<String, Object>singletonMap("contents", Collections.emptyList()),
                new NativeGeminiStreamListener() {
                    @Override
                    public void onGeminiEvent(NativeGeminiStreamEvent event) {
                        events.add(event);
                    }

                    @Override
                    public void onFailure(Throwable failure) {
                        failures.add(failure);
                    }
                });

        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        Assert.assertTrue(failures.isEmpty());
        Assert.assertEquals(3, events.size());
        Assert.assertEquals("reasoning", events.get(0).getThoughtText());
        Assert.assertEquals(Collections.singletonList("sig_1"), events.get(0).getThoughtSignatures());
        Assert.assertEquals(1, events.get(1).getFunctionCalls().size());
        Assert.assertEquals("done", events.get(1).getText());
        Assert.assertTrue(events.get(2).isCompletionSignal());
        Assert.assertEquals("STOP", events.get(2).getFinishReason());

        // Google HTTP 429 error maps to AiRateLimitException with x-goog-request-id
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setHeader("x-goog-request-id", "g-req-1")
                .setBody("{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"too fast\"}}"));
        try {
            client.generateContent("gemini-2.5-flash", Collections.<String, Object>emptyMap());
            Assert.fail("Expected AiRateLimitException");
        } catch (AiRateLimitException expected) {
            Assert.assertTrue(expected.getMessage().contains("RESOURCE_EXHAUSTED"));
            Assert.assertTrue(expected.getMessage().contains("too fast"));
            Assert.assertTrue(expected.getMessage().contains("g-req-1"));
        }

        // In-band Gemini stream error fails the call
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\",\"message\":\"overloaded\"}}\n\n"));
        final java.util.concurrent.CountDownLatch inBandLatch = new java.util.concurrent.CountDownLatch(1);
        final List<Throwable> inBandFailures = Collections.synchronizedList(new ArrayList<Throwable>());
        NativeStreamCall inBandErr = client.streamGenerateContent(
                "gemini-2.5-flash",
                Collections.<String, Object>emptyMap(),
                new NativeGeminiStreamListener() {
                    @Override
                    public void onGeminiEvent(NativeGeminiStreamEvent event) {
                    }

                    @Override
                    public void onFailure(Throwable failure) {
                        inBandFailures.add(failure);
                        inBandLatch.countDown();
                    }
                });
        Assert.assertTrue(inBandErr.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(ModelCallStatus.FAILED, inBandErr.status());
        Assert.assertNotNull(inBandErr.failure());
        Assert.assertTrue(inBandLatch.await(5, TimeUnit.SECONDS));
        Assert.assertFalse(inBandFailures.isEmpty());

        // Truncated stream without finishReason fails as incomplete
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"candidates\":[{\"index\":0,\"content\":{\"parts\":[{\"text\":\"partial\"}]}}]}\n\n"));
        NativeStreamCall truncated = client.streamGenerateContent(
                "gemini-2.5-flash",
                Collections.<String, Object>emptyMap(),
                new NativeGeminiStreamListener() {
                    @Override
                    public void onGeminiEvent(NativeGeminiStreamEvent event) {
                    }

                    @Override
                    public void onFailure(Throwable failure) {
                    }
                });
        Assert.assertTrue(truncated.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(ModelCallStatus.FAILED, truncated.status());
    }
}
