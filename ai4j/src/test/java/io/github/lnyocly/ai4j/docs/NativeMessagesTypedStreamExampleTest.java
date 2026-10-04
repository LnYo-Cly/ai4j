package io.github.lnyocly.ai4j.docs;

import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletion;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicMessage;
import io.github.lnyocly.ai4j.service.nativeapi.MessagesNativeClient;
import io.github.lnyocly.ai4j.service.nativeapi.NativeMessagesStreamEvent;
import io.github.lnyocly.ai4j.service.nativeapi.NativeMessagesStreamListener;
import io.github.lnyocly.ai4j.service.nativeapi.NativeStreamCall;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Runnable typed-stream example for the Anthropic Messages native entry.
 *
 * <p>A {@link NativeMessagesStreamListener} receives
 * {@link NativeMessagesStreamEvent}s that keep the native
 * {@code message_start}/{@code content_block_delta}/{@code message_stop}
 * vocabulary, the {@code input_json_delta} tool-argument fragment and the
 * {@code message_stop} terminal.</p>
 */
public class NativeMessagesTypedStreamExampleTest {

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
    public void typedMessagesStreamExample() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("event: message_start\n"
                        + "data: {\"type\":\"message_start\",\"message\":{\"id\":\"m1\",\"model\":\"claude\"}}\n\n"
                        + "event: content_block_delta\n"
                        + "data: {\"type\":\"content_block_delta\",\"index\":0,"
                        + "\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}\n\n"
                        + "event: message_delta\n"
                        + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},"
                        + "\"usage\":{\"output_tokens\":2}}\n\n"
                        + "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"));

        MessagesNativeClient client = MessagesNativeClient.of(server.url("/").toString(), "native-key");
        AnthropicChatCompletion request = new AnthropicChatCompletion();
        request.setModel("claude-test");
        request.setMaxTokens(64);
        request.setMessages(Collections.singletonList(new AnthropicMessage("user", "hello")));

        final StringBuilder text = new StringBuilder();
        final String[] stopReason = new String[1];
        NativeStreamCall call = client.messagesStream(request, new NativeMessagesStreamListener() {
            @Override
            public void onMessagesEvent(NativeMessagesStreamEvent event) {
                if (event.getDelta() != null && event.getDelta().getText() != null) {
                    text.append(event.getDelta().getText());
                }
                if (event.getStopReason() != null) {
                    stopReason[0] = event.getStopReason();
                }
            }

            @Override
            public void onFailure(Throwable failure) {
            }
        });
        call.await();

        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        Assert.assertEquals("Hi", text.toString());
        Assert.assertEquals("end_turn", stopReason[0]);
    }
}
