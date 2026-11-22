package io.github.lnyocly.ai4j.service.nativeapi;

import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletion;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import io.github.lnyocly.ai4j.service.Configuration;
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
 * F7 / R1: the typed per-protocol native stream event surface.
 *
 * <p>Each simple client delivers protocol-native typed events (not a shared
 * {@code ModelEvent} and not bare strings), preserving the native shape and the
 * SSE id envelope, with an explicit {@code unknown} marker for unmodelled
 * events and the raw payload retained.</p>
 */
public class NativeTypedStreamContractTest {

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
    public void chatDeliversTypedChunksDoneAndUnknownEvents() throws Exception {
        server.enqueue(sseResponse(
                "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\","
                        + "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Hel\"}}]}\n\n"
                        + "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\","
                        + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"lo\"},\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: {\"custom\":true}\n\n"
                        + "data: [DONE]\n\n"));

        final List<NativeChatStreamEvent> events = Collections.synchronizedList(
                new ArrayList<NativeChatStreamEvent>());
        NativeStreamCall call = ChatCompletionsNativeClient.of(
                server.url("/").toString(), "native-key", configuration)
                .chatCompletionStream(chatRequest(), new NativeChatStreamListener() {
                    @Override
                    public void onChatEvent(NativeChatStreamEvent event) {
                        events.add(event);
                    }

                    @Override
                    public void onFailure(Throwable failure) {
                    }
                });
        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        Assert.assertEquals(4, events.size());

        Assert.assertEquals("chat.completion.chunk", events.get(0).getType());
        Assert.assertEquals("Hel", events.get(0).getChunk().getChoices().get(0).getDelta().getContent().getText());
        Assert.assertTrue(events.get(1).isCompletionSignal());
        Assert.assertEquals("stop", events.get(1).getChunk().getChoices().get(0).getFinishReason());

        Assert.assertTrue("an unmodelled payload must be an explicit unknown event", events.get(2).isUnknown());
        Assert.assertEquals(NativeStreamEvent.UNKNOWN_TYPE, events.get(2).getType());
        Assert.assertEquals("{\"custom\":true}", events.get(2).getRawData());

        Assert.assertTrue(events.get(3).isTerminal());
    }

    @Test
    public void chatInBandErrorEventCarriesTypedError() throws Exception {
        server.enqueue(sseResponse(
                "data: {\"error\":{\"message\":\"rate limited\",\"type\":\"rate_limit_exceeded\",\"code\":\"429\"}}\n\n"));

        final List<NativeChatStreamEvent> events = Collections.synchronizedList(
                new ArrayList<NativeChatStreamEvent>());
        NativeStreamCall call = ChatCompletionsNativeClient.of(
                server.url("/").toString(), "native-key", configuration)
                .chatCompletionStream(chatRequest(), new NativeChatStreamListener() {
                    @Override
                    public void onChatEvent(NativeChatStreamEvent event) {
                        events.add(event);
                    }

                    @Override
                    public void onFailure(Throwable failure) {
                    }
                });
        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(ModelCallStatus.FAILED, call.status());
        Assert.assertEquals(1, events.size());
        Assert.assertNotNull(events.get(0).getError());
        Assert.assertEquals("rate_limit_exceeded", events.get(0).getError().getType());
        Assert.assertEquals("rate limited", events.get(0).getError().getMessage());
        Assert.assertTrue(call.failure() instanceof NativeStreamException);
    }

    @Test
    public void messagesDeliversTypedNativeEvents() throws Exception {
        server.enqueue(sseResponse(
                "event: message_start\n"
                        + "data: {\"type\":\"message_start\",\"message\":{\"id\":\"m1\",\"model\":\"claude\","
                        + "\"usage\":{\"input_tokens\":3}}}\n\n"
                        + "event: content_block_delta\n"
                        + "data: {\"type\":\"content_block_delta\",\"index\":0,"
                        + "\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}\n\n"
                        + "event: content_block_delta\n"
                        + "data: {\"type\":\"content_block_delta\",\"index\":1,"
                        + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"x\\\":\"}}\n\n"
                        + "event: message_delta\n"
                        + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},"
                        + "\"usage\":{\"output_tokens\":2}}\n\n"
                        + "event: ping\ndata: {\"type\":\"ping\"}\n\n"
                        + "event: weird_event\ndata: {\"type\":\"weird_event\",\"note\":\"keep me\"}\n\n"
                        + "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"));

        final List<NativeMessagesStreamEvent> events = Collections.synchronizedList(
                new ArrayList<NativeMessagesStreamEvent>());
        NativeStreamCall call = MessagesNativeClient.of(
                server.url("/").toString(), "native-key", configuration)
                .messagesStream(messagesRequest(), new NativeMessagesStreamListener() {
                    @Override
                    public void onMessagesEvent(NativeMessagesStreamEvent event) {
                        events.add(event);
                    }

                    @Override
                    public void onFailure(Throwable failure) {
                    }
                });
        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        Assert.assertEquals(7, events.size());

        Assert.assertEquals("m1", events.get(0).getMessage().getId());
        Assert.assertEquals(3L, events.get(0).getUsage().getInputTokens());
        Assert.assertEquals("Hi", events.get(1).getDelta().getText());
        Assert.assertEquals("{\"x\":", events.get(2).getPartialJson());
        Assert.assertEquals(Integer.valueOf(1), events.get(2).getIndex());
        Assert.assertEquals("end_turn", events.get(3).getStopReason());
        Assert.assertEquals(2L, events.get(3).getUsage().getOutputTokens());
        Assert.assertEquals("ping", events.get(4).getType());
        Assert.assertTrue("an unmodelled Anthropic event must be unknown", events.get(5).isUnknown());
        Assert.assertTrue(events.get(5).getRawData().contains("keep me"));
        Assert.assertTrue(events.get(6).isTerminal());
    }

    @Test
    public void responsesDeliversTypedNativeEvents() throws Exception {
        server.enqueue(sseResponse(
                "event: response.created\n"
                        + "data: {\"type\":\"response.created\",\"sequence_number\":1,"
                        + "\"response\":{\"id\":\"r1\",\"status\":\"in_progress\"}}\n\n"
                        + "event: response.output_text.delta\n"
                        + "data: {\"type\":\"response.output_text.delta\",\"sequence_number\":2,"
                        + "\"item_id\":\"i1\",\"delta\":\"Hel\"}\n\n"
                        + "event: response.completed\n"
                        + "data: {\"type\":\"response.completed\",\"sequence_number\":3,"
                        + "\"response\":{\"id\":\"r1\",\"status\":\"completed\"}}\n\n"));

        final List<NativeResponsesStreamEvent> events = Collections.synchronizedList(
                new ArrayList<NativeResponsesStreamEvent>());
        NativeStreamCall call = ResponsesNativeClient.of(
                server.url("/").toString(), "native-key", configuration)
                .createStream(responsesRequest(), new NativeResponsesStreamListener() {
                    @Override
                    public void onResponsesEvent(NativeResponsesStreamEvent event) {
                        events.add(event);
                    }

                    @Override
                    public void onFailure(Throwable failure) {
                    }
                });
        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(ModelCallStatus.COMPLETED, call.status());
        Assert.assertEquals(3, events.size());
        Assert.assertEquals("response.created", events.get(0).getType());
        Assert.assertEquals(Integer.valueOf(1), events.get(0).getEvent().getSequenceNumber());
        Assert.assertEquals("Hel", events.get(1).getEvent().getDelta());
        Assert.assertTrue(events.get(2).isTerminal());
    }

    @Test
    public void responsesFailedEventCarriesTypedError() throws Exception {
        server.enqueue(sseResponse(
                "event: response.failed\n"
                        + "data: {\"type\":\"response.failed\",\"response\":{\"id\":\"r1\",\"status\":\"failed\","
                        + "\"error\":{\"code\":\"server_error\",\"message\":\"boom\"}}}\n\n"));

        final List<NativeResponsesStreamEvent> events = Collections.synchronizedList(
                new ArrayList<NativeResponsesStreamEvent>());
        NativeStreamCall call = ResponsesNativeClient.of(
                server.url("/").toString(), "native-key", configuration)
                .createStream(responsesRequest(), new NativeResponsesStreamListener() {
                    @Override
                    public void onResponsesEvent(NativeResponsesStreamEvent event) {
                        events.add(event);
                    }

                    @Override
                    public void onFailure(Throwable failure) {
                    }
                });
        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(ModelCallStatus.FAILED, call.status());
        Assert.assertEquals(1, events.size());
        Assert.assertNotNull(events.get(0).getError());
        Assert.assertEquals("boom", events.get(0).getError().getMessage());
        Assert.assertTrue(call.failure() instanceof NativeStreamException);
    }

    private static ChatCompletion chatRequest() {
        return ChatCompletion.builder()
                .model("gpt-test")
                .messages(Collections.singletonList(ChatMessage.withUser("hello")))
                .build();
    }

    private static ResponseRequest responsesRequest() {
        return ResponseRequest.builder().model("gpt-test").input("hello").build();
    }

    private static AnthropicChatCompletion messagesRequest() {
        AnthropicChatCompletion request = new AnthropicChatCompletion();
        request.setModel("claude-test");
        request.setMaxTokens(64);
        request.setMessages(Collections.singletonList(new AnthropicMessage("user", "hello")));
        return request;
    }

    private static MockResponse sseResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body);
    }
}

