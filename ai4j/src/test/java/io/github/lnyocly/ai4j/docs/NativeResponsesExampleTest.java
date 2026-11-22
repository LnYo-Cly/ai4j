package io.github.lnyocly.ai4j.docs;

import io.github.lnyocly.ai4j.client.RecordingFunction;
import io.github.lnyocly.ai4j.exception.AiRateLimitException;
import io.github.lnyocly.ai4j.exception.AiServerErrorException;
import io.github.lnyocly.ai4j.listener.StreamExecutionOptions;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.platform.openai.response.entity.Response;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import io.github.lnyocly.ai4j.service.nativeapi.NativeSseListener;
import io.github.lnyocly.ai4j.service.nativeapi.NativeStreamCall;
import io.github.lnyocly.ai4j.service.nativeapi.ResponsesNativeClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executable example for the protocol-native Responses entry.
 *
 * <p>A caller needs only a base URL, an API key and a native
 * {@link ResponseRequest}. Protocol API {@code metadata} stays on the wire while
 * the local {@code streamExecution} field never leaves the process; raw SSE event
 * types and order are preserved.</p>
 */
public class NativeResponsesExampleTest {

    private MockWebServer server;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void minimalNonStreamCallKeepsProtocolMetadataAndDropsLocalMetadata() throws Exception {
        RecordingFunction.INVOCATIONS.set(0);
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(RESPONSES_TOOL_CALL_BODY));

        ResponsesNativeClient client =
                ResponsesNativeClient.of(server.url("/").toString(), "native-key");
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("trace_id", "abc-123");
        ResponseRequest request = ResponseRequest.builder()
                .model("gpt-test")
                .input("hello")
                .metadata(metadata)
                .streamExecution(StreamExecutionOptions.builder().idleTimeoutMs(4321L).build())
                .build();

        Response response = client.create(request);

        Assert.assertNotNull(response);
        Assert.assertEquals("completed", response.getStatus());
        Assert.assertEquals("the native entry must never execute a tool",
                0, RecordingFunction.INVOCATIONS.get());

        RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);
        Assert.assertNotNull(recorded);
        Assert.assertEquals("/v1/responses", recorded.getPath());
        Assert.assertEquals("Bearer native-key", recorded.getHeader("Authorization"));
        String body = recorded.getBody().readUtf8();
        Assert.assertTrue("protocol API metadata must be on the wire",
                body.contains("\"metadata\":{\"trace_id\":\"abc-123\"}"));
        Assert.assertFalse("local stream execution options must not leak to the wire",
                body.contains("idleTimeoutMs"));
    }

    @Test
    public void streamingDeliversRawSseEventTypesInOrder() throws Exception {
        server.enqueue(sseResponse(
                "event: response.created\n"
                        + "data: {\"type\":\"response.created\",\"response\":{\"id\":\"resp_native_1\",\"status\":\"in_progress\"}}\n\n"
                        + "event: response.output_text.delta\n"
                        + "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"item_1\",\"delta\":\"Hel\"}\n\n"
                        + "event: response.output_text.delta\n"
                        + "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"item_1\",\"delta\":\"lo\"}\n\n"
                        + "event: response.completed\n"
                        + "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"resp_native_1\",\"status\":\"completed\"}}\n\n"));

        ResponsesNativeClient client =
                ResponsesNativeClient.of(server.url("/").toString(), "native-key");
        ResponseRequest request = ResponseRequest.builder()
                .model("gpt-test")
                .input("hello")
                .build();

        final List<String> events = Collections.synchronizedList(new ArrayList<String>());
        NativeStreamCall call = client.createStream(request, new NativeSseListener() {
            @Override
            public void onEvent(String id, String type, String data) {
                events.add(type + "|" + data);
            }

            @Override
            public void onFailure(Throwable failure) {
                events.add("failure:" + failure.getClass().getSimpleName());
            }
        });
        call.await();

        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        Assert.assertEquals(4, events.size());
        Assert.assertTrue(events.get(0).startsWith("response.created|"));
        Assert.assertTrue(events.get(1).startsWith("response.output_text.delta|"));
        Assert.assertTrue(events.get(1).contains("\"delta\":\"Hel\""));
        Assert.assertTrue(events.get(2).startsWith("response.output_text.delta|"));
        Assert.assertTrue(events.get(2).contains("\"delta\":\"lo\""));
        Assert.assertTrue(events.get(3).startsWith("response.completed|"));
        Assert.assertFalse("the native entry must not mutate the caller's stream flag",
                Boolean.TRUE.equals(request.getStream()));

        RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);
        Assert.assertNotNull(recorded);
        Assert.assertTrue(recorded.getBody().readUtf8().contains("\"stream\":true"));
    }

    @Test
    public void httpErrorThrowsTypedNativeException() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"message\":\"boom\"}}"));

        ResponsesNativeClient client =
                ResponsesNativeClient.of(server.url("/").toString(), "native-key");
        try {
            client.create(ResponseRequest.builder().model("gpt-test").input("hello").build());
            Assert.fail("expected a typed HTTP error");
        } catch (AiServerErrorException expected) {
            Assert.assertEquals(500, expected.getStatusCode());
        }
    }

    @Test
    public void streamingHttpErrorSurfacesOnHandleAndListener() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"message\":\"rate limited\"}}"));

        ResponsesNativeClient client =
                ResponsesNativeClient.of(server.url("/").toString(), "native-key");
        final AtomicReference<Throwable> listenerFailure = new AtomicReference<Throwable>();
        NativeStreamCall call = client.createStream(
                ResponseRequest.builder().model("gpt-test").input("hello").build(),
                new NativeSseListener() {
                    @Override
                    public void onEvent(String id, String type, String data) {
                    }

                    @Override
                    public void onFailure(Throwable failure) {
                        listenerFailure.set(failure);
                    }
                });
        call.await();

        Assert.assertEquals(ModelCallStatus.FAILED, call.status());
        Assert.assertTrue(call.failure() instanceof AiRateLimitException);
        Assert.assertTrue(listenerFailure.get() instanceof AiRateLimitException);
    }

    private static MockResponse sseResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body);
    }

    private static final String RESPONSES_TOOL_CALL_BODY = "{"
            + "\"id\":\"resp_native_1\",\"object\":\"response\",\"status\":\"completed\",\"model\":\"gpt-test\","
            + "\"output\":[{\"type\":\"function_call\",\"call_id\":\"call_1\","
            + "\"name\":\"audit_recording_function\",\"arguments\":\"{\\\"value\\\":\\\"x\\\"}\"}],"
            + "\"usage\":{\"input_tokens\":10,\"output_tokens\":5,\"total_tokens\":15}}";
}
