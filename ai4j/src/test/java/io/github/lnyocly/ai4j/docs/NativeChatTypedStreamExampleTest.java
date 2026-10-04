package io.github.lnyocly.ai4j.docs;

import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.service.nativeapi.ChatCompletionsNativeClient;
import io.github.lnyocly.ai4j.service.nativeapi.NativeChatStreamEvent;
import io.github.lnyocly.ai4j.service.nativeapi.NativeChatStreamListener;
import io.github.lnyocly.ai4j.service.nativeapi.NativeStreamCall;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runnable typed-stream example for the Chat Completions native entry.
 *
 * <p>A {@link NativeChatStreamListener} receives {@link NativeChatStreamEvent}s
 * with the native chunk DTO and explicit done/unknown markers, instead of
 * reparsing raw SSE strings.</p>
 */
public class NativeChatTypedStreamExampleTest {

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
    public void typedChatStreamExample() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\","
                        + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hel\"}}]}\n\n"
                        + "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\","
                        + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"lo\"},\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: [DONE]\n\n"));

        ChatCompletionsNativeClient client =
                ChatCompletionsNativeClient.of(server.url("/").toString(), "native-key");
        ChatCompletion request = ChatCompletion.builder()
                .model("gpt-test")
                .messages(Collections.singletonList(ChatMessage.withUser("hello")))
                .build();

        final StringBuilder text = new StringBuilder();
        NativeStreamCall call = client.chatCompletionStream(request, new NativeChatStreamListener() {
            @Override
            public void onChatEvent(NativeChatStreamEvent event) {
                if (event.getChunk() != null && event.getChunk().getChoices() != null
                        && !event.getChunk().getChoices().isEmpty()
                        && event.getChunk().getChoices().get(0).getDelta() != null
                        && event.getChunk().getChoices().get(0).getDelta().getContent() != null) {
                    text.append(event.getChunk().getChoices().get(0).getDelta().getContent().getText());
                }
            }

            @Override
            public void onFailure(Throwable failure) {
            }
        });
        call.await();

        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        Assert.assertEquals("Hello", text.toString());
    }
}
