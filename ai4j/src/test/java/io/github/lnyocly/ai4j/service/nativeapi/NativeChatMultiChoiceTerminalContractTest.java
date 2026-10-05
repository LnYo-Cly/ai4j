package io.github.lnyocly.ai4j.service.nativeapi;

import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
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
 * Multi-choice terminal completeness for the native Chat Completions entry.
 *
 * <p>A chat stream is only complete when the whole-call contract is met: either
 * the authoritative {@code [DONE]} terminal arrives, or every choice observed on
 * the wire has reported a {@code finish_reason} and at least the request's
 * expected number of choices ({@code n}) has finished. Any other clean EOF is
 * incomplete, because a truncated stream must not be reported as COMPLETED.</p>
 *
 * <p>These tests exercise the real public entry over a local
 * {@code MockWebServer}; the raw payload of every choice chunk is asserted to
 * survive on the listener.</p>
 */
public class NativeChatMultiChoiceTerminalContractTest {

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
    public void partialChoiceFinishThenEofIsIncomplete() throws Exception {
        server.enqueue(sseResponse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"A\"},"
                        + "\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: {\"choices\":[{\"index\":1,\"delta\":{\"content\":\"B\"}}]}\n\n"));

        final List<String> raw = Collections.synchronizedList(new ArrayList<String>());
        NativeStreamCall call = chatClient().chatCompletionStream(multiChoiceRequest(2), rawListener(raw));

        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("a truncated multi-choice stream must not report COMPLETED",
                ModelCallStatus.FAILED, call.status());
        Assert.assertTrue("the failure must be the incomplete kind",
                call.failure() instanceof NativeStreamException);
        Assert.assertEquals(NativeStreamException.KIND_INCOMPLETE,
                ((NativeStreamException) call.failure()).getKind());
        Assert.assertEquals("both choice payloads must still reach the listener", 2, raw.size());
        Assert.assertTrue("the unfinished choice payload must be preserved",
                raw.get(1).contains("\"index\":1"));
    }

    @Test
    public void requestedTwoChoicesOnlyOneEmittedThenEofIsIncomplete() throws Exception {
        server.enqueue(sseResponse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"only\"},"
                        + "\"finish_reason\":\"stop\"}]}\n\n"));

        NativeStreamCall call = chatClient().chatCompletionStream(multiChoiceRequest(2), noopListener());

        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("n=2 with only index 0 finished is not the whole contract",
                ModelCallStatus.FAILED, call.status());
        Assert.assertTrue(call.failure() instanceof NativeStreamException);
        Assert.assertEquals(NativeStreamException.KIND_INCOMPLETE,
                ((NativeStreamException) call.failure()).getKind());
    }

    @Test
    public void multiChoiceAllChoicesFinishedWithoutDoneCompletes() throws Exception {
        server.enqueue(sseResponse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"A\"}}]}\n\n"
                        + "data: {\"choices\":[{\"index\":1,\"delta\":{\"content\":\"B\"}}]}\n\n"
                        + "data: {\"choices\":[{\"index\":1,\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: {\"choices\":[{\"index\":0,\"finish_reason\":\"stop\"}]}\n\n"));

        NativeStreamCall call = chatClient().chatCompletionStream(multiChoiceRequest(2), noopListener());

        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("every expected choice finished, so the call is complete",
                ModelCallStatus.COMPLETED, call.status());
        Assert.assertNull(call.failure());
    }

    @Test
    public void multiChoiceAuthoritativeDoneCompletes() throws Exception {
        server.enqueue(sseResponse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"A\"}}]}\n\n"
                        + "data: {\"choices\":[{\"index\":1,\"delta\":{\"content\":\"B\"}}]}\n\n"
                        + "data: [DONE]\n\n"));

        NativeStreamCall call = chatClient().chatCompletionStream(multiChoiceRequest(2), noopListener());

        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("the [DONE] terminal completes a multi-choice stream",
                ModelCallStatus.COMPLETED, call.status());
    }

    @Test
    public void singleChoiceFinishThenTrailingUsageWithoutDoneCompletes() throws Exception {
        server.enqueue(sseResponse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi\"}}]}\n\n"
                        + "data: {\"choices\":[{\"index\":0,\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":1,"
                        + "\"completion_tokens\":1,\"total_tokens\":2}}\n\n"));

        NativeStreamCall call = chatClient().chatCompletionStream(singleChoiceRequest(), noopListener());

        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("trailing usage after a single finished choice stays complete",
                ModelCallStatus.COMPLETED, call.status());
    }

    @Test
    public void singleChoiceFinishWithoutDoneStillCompletes() throws Exception {
        server.enqueue(sseResponse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi\"},"
                        + "\"finish_reason\":\"stop\"}]}\n\n"));

        NativeStreamCall call = chatClient().chatCompletionStream(singleChoiceRequest(), noopListener());

        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("the existing single-choice finish+EOF behavior must be preserved",
                ModelCallStatus.COMPLETED, call.status());
    }

    @Test
    public void multiChoicePartialThenExplicitErrorFails() throws Exception {
        server.enqueue(sseResponse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"A\"},"
                        + "\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: {\"error\":{\"message\":\"boom\",\"type\":\"server_error\"}}\n\n"));

        NativeStreamCall call = chatClient().chatCompletionStream(multiChoiceRequest(2), noopListener());

        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("an in-band error after partial content is a failure",
                ModelCallStatus.FAILED, call.status());
        Assert.assertTrue(call.failure() instanceof NativeStreamException);
        Assert.assertEquals(NativeStreamException.KIND_IN_BAND_ERROR,
                ((NativeStreamException) call.failure()).getKind());
    }

    @Test
    public void multiChoiceUnexpectedIndexSetThenEofIsIncomplete() throws Exception {
        server.enqueue(sseResponse(
                "data: {\"choices\":[{\"index\":0,\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: {\"choices\":[{\"index\":2,\"finish_reason\":\"stop\"}]}\n\n"));

        NativeStreamCall call = chatClient().chatCompletionStream(multiChoiceRequest(2), noopListener());

        Assert.assertTrue(call.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("an index set that skips an expected choice is not the whole contract",
                ModelCallStatus.FAILED, call.status());
        Assert.assertTrue(call.failure() instanceof NativeStreamException);
        Assert.assertEquals(NativeStreamException.KIND_INCOMPLETE,
                ((NativeStreamException) call.failure()).getKind());
    }

    private ChatCompletionsNativeClient chatClient() {
        return ChatCompletionsNativeClient.of(server.url("/").toString(), "native-key", configuration);
    }

    private static ChatCompletion multiChoiceRequest(int n) {
        return ChatCompletion.builder()
                .model("gpt-test")
                .n(n)
                .messages(Collections.singletonList(ChatMessage.withUser("hello")))
                .build();
    }

    private static ChatCompletion singleChoiceRequest() {
        return ChatCompletion.builder()
                .model("gpt-test")
                .messages(Collections.singletonList(ChatMessage.withUser("hello")))
                .build();
    }

    private static NativeSseListener rawListener(final List<String> raw) {
        return new NativeSseListener() {
            @Override
            public void onEvent(String id, String type, String data) {
                raw.add(data);
            }

            @Override
            public void onFailure(Throwable failure) {
            }
        };
    }

    private static NativeSseListener noopListener() {
        return new NativeSseListener() {
            @Override
            public void onEvent(String id, String type, String data) {
            }

            @Override
            public void onFailure(Throwable failure) {
            }
        };
    }

    private static MockResponse sseResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body);
    }
}
