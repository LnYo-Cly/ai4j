package io.github.lnyocly.ai4j.docs;

import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import io.github.lnyocly.ai4j.service.nativeapi.NativeResponsesStreamEvent;
import io.github.lnyocly.ai4j.service.nativeapi.NativeResponsesStreamListener;
import io.github.lnyocly.ai4j.service.nativeapi.NativeStreamCall;
import io.github.lnyocly.ai4j.service.nativeapi.ResponsesNativeClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Runnable typed-stream example for the OpenAI Responses native entry.
 *
 * <p>A {@link NativeResponsesStreamListener} receives
 * {@link NativeResponsesStreamEvent}s carrying the reused native
 * {@code ResponseStreamEvent} DTO with its sequence number and delta.</p>
 */
public class NativeResponsesTypedStreamExampleTest {

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
    public void typedResponsesStreamExample() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("event: response.created\n"
                        + "data: {\"type\":\"response.created\",\"sequence_number\":1,"
                        + "\"response\":{\"id\":\"r1\",\"status\":\"in_progress\"}}\n\n"
                        + "event: response.output_text.delta\n"
                        + "data: {\"type\":\"response.output_text.delta\",\"sequence_number\":2,"
                        + "\"item_id\":\"i1\",\"delta\":\"Hi\"}\n\n"
                        + "event: response.completed\n"
                        + "data: {\"type\":\"response.completed\",\"sequence_number\":3,"
                        + "\"response\":{\"id\":\"r1\",\"status\":\"completed\"}}\n\n"));

        ResponsesNativeClient client = ResponsesNativeClient.of(server.url("/").toString(), "native-key");
        ResponseRequest request = ResponseRequest.builder().model("gpt-test").input("hello").build();

        final StringBuilder text = new StringBuilder();
        NativeStreamCall call = client.createStream(request, new NativeResponsesStreamListener() {
            @Override
            public void onResponsesEvent(NativeResponsesStreamEvent event) {
                if (event.getEvent() != null && event.getEvent().getDelta() != null) {
                    text.append(event.getEvent().getDelta());
                }
            }

            @Override
            public void onFailure(Throwable failure) {
            }
        });
        call.await();

        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        Assert.assertEquals("Hi", text.toString());
    }
}
