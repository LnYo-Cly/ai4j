package io.github.lnyocly.ai4j.platform.openai.chat;

import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.exception.AiAuthException;
import io.github.lnyocly.ai4j.exception.AiHttpException;
import io.github.lnyocly.ai4j.exception.AiRateLimitException;
import io.github.lnyocly.ai4j.exception.AiServerErrorException;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Assert;
import org.junit.Test;

/**
 * Offline tests verifying that {@link OpenAiChatService} throws structured
 * {@link AiHttpException} subclasses on non-2xx HTTP responses instead of
 * returning null.
 */
public class OpenAiChatServiceHttpErrorTest {

    @Test
    public void chatCompletion_401_throwsAiAuthException() {
        OpenAiChatService service = buildService(401,
                "{\"error\":{\"message\":\"Invalid API key\",\"type\":\"invalid_request_error\"}}");

        try {
            service.chatCompletion(simpleRequest());
            Assert.fail("Expected AiAuthException");
        } catch (AiAuthException e) {
            Assert.assertEquals(401, e.getStatusCode());
            Assert.assertTrue(e.getMessage().contains("Invalid API key"));
        } catch (Exception e) {
            Assert.fail("Expected AiAuthException, got " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    @Test
    public void chatCompletion_429_throwsAiRateLimitException() {
        OpenAiChatService service = buildService(429,
                "{\"error\":{\"message\":\"Rate limit exceeded\"}}");

        try {
            service.chatCompletion(simpleRequest());
            Assert.fail("Expected AiRateLimitException");
        } catch (AiRateLimitException e) {
            Assert.assertEquals(429, e.getStatusCode());
            Assert.assertTrue(e.getMessage().contains("Rate limit"));
        } catch (Exception e) {
            Assert.fail("Expected AiRateLimitException, got " + e.getClass().getName());
        }
    }

    @Test
    public void chatCompletion_500_throwsAiServerErrorException() {
        OpenAiChatService service = buildService(500, "{}");

        try {
            service.chatCompletion(simpleRequest());
            Assert.fail("Expected AiServerErrorException");
        } catch (AiServerErrorException e) {
            Assert.assertEquals(500, e.getStatusCode());
        } catch (Exception e) {
            Assert.fail("Expected AiServerErrorException, got " + e.getClass().getName());
        }
    }

    @Test
    public void chatCompletion_doesNotReturnNullOnNon2xx() {
        OpenAiChatService service = buildService(403, "{\"error\":{\"message\":\"forbidden\"}}");

        try {
            ChatCompletion req = simpleRequest();
            Object result = service.chatCompletion(req);
            Assert.fail("Should have thrown, but returned: " + result);
        } catch (AiAuthException e) {
            Assert.assertEquals(403, e.getStatusCode());
        } catch (Exception e) {
            Assert.fail("Expected AiAuthException, got " + e.getClass().getName());
        }
    }

    // ---- helpers ----

    private OpenAiChatService buildService(final int statusCode, final String errorBody) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(statusCode)
                        .message("Error")
                        .body(ResponseBody.create(errorBody, MediaType.get("application/json")))
                        .build())
                .build();

        OpenAiConfig config = new OpenAiConfig();
        config.setApiKey("test-key");
        config.setApiHost("https://unit.test/");
        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(config);
        configuration.setOkHttpClient(okHttpClient);
        return new OpenAiChatService(configuration);
    }

    private ChatCompletion simpleRequest() {
        return ChatCompletion.builder()
                .model("gpt-4")
                .message(ChatMessage.withUser("hello"))
                .build();
    }
}
