package io.github.lnyocly.ai4j.exception;

import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.platform.openai.audio.OpenAiAudioService;
import io.github.lnyocly.ai4j.platform.openai.audio.entity.Transcription;
import io.github.lnyocly.ai4j.platform.openai.image.OpenAiImageService;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageGeneration;
import io.github.lnyocly.ai4j.platform.openai.response.OpenAiResponsesService;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import io.github.lnyocly.ai4j.platform.standard.rerank.StandardRerankService;
import io.github.lnyocly.ai4j.rerank.entity.RerankRequest;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

/**
 * Offline regression tests for issue #228: non-chat services used to discard the
 * provider's error body and throw a hardcoded generic message, leaving callers
 * with nothing to debug.
 *
 * <p>Every service here must now route HTTP failures through
 * {@link HttpErrorDecoder}, matching what the {@code *ChatService} family
 * already does.
 */
public class NonChatServiceHttpErrorTest {

    /** The exact failure reported in issue #228, observed live against a gateway. */
    private static final String GATEWAY_MESSAGE =
            "previous_response_id is only supported on Responses WebSocket v2";

    // ---- OpenAI Responses (the originally reported service) ----

    @Test
    public void responsesCreate_propagatesProviderMessageInsteadOfGenericText() {
        OpenAiResponsesService service = responsesService(400,
                "{\"error\":{\"message\":\"" + GATEWAY_MESSAGE + "\"}}");

        try {
            service.create(ResponseRequest.builder().model("gpt-4o-mini").input("hi").build());
            Assert.fail("Expected AiClientException");
        } catch (AiClientException e) {
            Assert.assertEquals(400, e.getStatusCode());
            Assert.assertTrue("provider message must survive, got: " + e.getMessage(),
                    e.getMessage().contains(GATEWAY_MESSAGE));
        } catch (Exception e) {
            Assert.fail("Expected AiClientException, got " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    @Test
    public void responsesCreate_401_isTypedAsAuthFailure() {
        OpenAiResponsesService service = responsesService(401,
                "{\"error\":{\"message\":\"Invalid API key\"}}");

        try {
            service.create(ResponseRequest.builder().model("gpt-4o-mini").input("hi").build());
            Assert.fail("Expected AiAuthException");
        } catch (AiAuthException e) {
            Assert.assertEquals(401, e.getStatusCode());
            Assert.assertTrue(e.getMessage().contains("Invalid API key"));
        } catch (Exception e) {
            Assert.fail("Expected AiAuthException, got " + e.getClass().getName());
        }
    }

    @Test
    public void responsesRetrieve_alsoDecodesTheErrorBody() {
        OpenAiResponsesService service = responsesService(429,
                "{\"error\":{\"message\":\"Rate limit exceeded\"}}");

        try {
            service.retrieve("resp_123");
            Assert.fail("Expected AiRateLimitException");
        } catch (AiRateLimitException e) {
            Assert.assertEquals(429, e.getStatusCode());
            Assert.assertTrue(e.getMessage().contains("Rate limit exceeded"));
        } catch (Exception e) {
            Assert.fail("Expected AiRateLimitException, got " + e.getClass().getName());
        }
    }

    @Test
    public void responsesDelete_alsoDecodesTheErrorBody() {
        OpenAiResponsesService service = responsesService(404,
                "{\"error\":{\"message\":\"No such response\"}}");

        try {
            service.delete("resp_missing");
            Assert.fail("Expected AiClientException");
        } catch (AiClientException e) {
            Assert.assertTrue(e.getMessage().contains("No such response"));
        } catch (Exception e) {
            Assert.fail("Expected AiClientException, got " + e.getClass().getName());
        }
    }

    /**
     * Gateways and reverse proxies fail with HTML or plain text, not the OpenAI
     * error envelope. That body is often the only clue, so it must not be dropped.
     */
    @Test
    public void nonJsonErrorBodyIsPreservedRatherThanReducedToStatusLine() {
        OpenAiResponsesService service = responsesService(502,
                "<html><body>Bad Gateway: upstream connect timeout</body></html>");

        try {
            service.create(ResponseRequest.builder().model("gpt-4o-mini").input("hi").build());
            Assert.fail("Expected AiServerErrorException");
        } catch (AiServerErrorException e) {
            Assert.assertEquals(502, e.getStatusCode());
            Assert.assertTrue("raw body must survive, got: " + e.getMessage(),
                    e.getMessage().contains("upstream connect timeout"));
        } catch (Exception e) {
            Assert.fail("Expected AiServerErrorException, got " + e.getClass().getName());
        }
    }

    @Test
    public void oversizedErrorBodyIsTruncatedNotDumpedWhole() {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            huge.append('x');
        }
        OpenAiResponsesService service = responsesService(500, huge.toString());

        try {
            service.create(ResponseRequest.builder().model("gpt-4o-mini").input("hi").build());
            Assert.fail("Expected AiServerErrorException");
        } catch (AiServerErrorException e) {
            Assert.assertTrue("message should be bounded, was " + e.getMessage().length(),
                    e.getMessage().length() < 700);
            Assert.assertTrue(e.getMessage().contains("truncated"));
        } catch (Exception e) {
            Assert.fail("Expected AiServerErrorException, got " + e.getClass().getName());
        }
    }

    // ---- Audio: used to swallow the exception and return null ----

    @Test
    public void audioTranscription_throwsInsteadOfReturningNull() throws Exception {
        File audio = File.createTempFile("ai4j-test-", ".mp3");
        audio.deleteOnExit();

        OpenAiAudioService service = new OpenAiAudioService(
                configuration(400, "{\"error\":{\"message\":\"Audio file is too short\"}}"));

        try {
            Object result = service.transcription(
                    Transcription.builder().file(audio).model("whisper-1").build());
            Assert.fail("Should have thrown instead of returning " + result);
        } catch (AiClientException e) {
            Assert.assertEquals(400, e.getStatusCode());
            Assert.assertTrue(e.getMessage().contains("Audio file is too short"));
        }
    }

    // ---- Image and rerank: same generic-message defect ----

    @Test
    public void imageGeneration_propagatesProviderMessage() {
        OpenAiImageService service = new OpenAiImageService(
                configuration(400, "{\"error\":{\"message\":\"Your prompt was rejected\"}}"));

        try {
            service.generate(ImageGeneration.builder().model("dall-e-3").prompt("a cat").build());
            Assert.fail("Expected AiClientException");
        } catch (AiClientException e) {
            Assert.assertTrue(e.getMessage().contains("Your prompt was rejected"));
        } catch (Exception e) {
            Assert.fail("Expected AiClientException, got " + e.getClass().getName());
        }
    }

    @Test
    public void rerank_propagatesProviderMessage() {
        StandardRerankService service = new StandardRerankService(
                stubClient(400, "{\"error\":{\"message\":\"Unknown rerank model\"}}"),
                "https://unit.test/", "test-key", "/v1/rerank");

        try {
            service.rerank(RerankRequest.builder().model("bad-model").query("q").build());
            Assert.fail("Expected AiClientException");
        } catch (AiClientException e) {
            Assert.assertTrue(e.getMessage().contains("Unknown rerank model"));
        } catch (Exception e) {
            Assert.fail("Expected AiClientException, got " + e.getClass().getName());
        }
    }

    // ---- helpers ----

    private OpenAiResponsesService responsesService(int statusCode, String errorBody) {
        return new OpenAiResponsesService(configuration(statusCode, errorBody));
    }

    private Configuration configuration(int statusCode, String errorBody) {
        OpenAiConfig config = new OpenAiConfig();
        config.setApiKey("test-key");
        config.setApiHost("https://unit.test/");

        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(config);
        configuration.setOkHttpClient(stubClient(statusCode, errorBody));
        return configuration;
    }

    /** Short-circuits the call so no network is required. */
    private OkHttpClient stubClient(final int statusCode, final String errorBody) {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(statusCode)
                        .message("Error")
                        .body(ResponseBody.create(errorBody, MediaType.get("application/json")))
                        .build())
                .build();
    }
}
