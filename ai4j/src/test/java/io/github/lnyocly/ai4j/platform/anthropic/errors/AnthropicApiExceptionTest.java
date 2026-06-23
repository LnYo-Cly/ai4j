package io.github.lnyocly.ai4j.platform.anthropic.errors;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AnthropicApiExceptionTest {

    @Test
    public void ofMapsByErrorType() {
        assertTrue(AnthropicApiException.of(429, "rate_limit_error", "m", "r")
                instanceof AnthropicApiException.AnthropicRateLimitException);
        assertTrue(AnthropicApiException.of(529, "overloaded_error", "m", "r")
                instanceof AnthropicApiException.AnthropicOverloadedException);
        assertTrue(AnthropicApiException.of(401, "authentication_error", "m", "r")
                instanceof AnthropicApiException.AnthropicAuthenticationException);
        assertTrue(AnthropicApiException.of(400, "invalid_request_error", "m", "r")
                instanceof AnthropicApiException.AnthropicInvalidRequestException);
        // type containing "invalid" (substring) also maps
        assertTrue(AnthropicApiException.of(400, "invalid_json", "m", "r")
                instanceof AnthropicApiException.AnthropicInvalidRequestException);
        // type wins over status: rate_limit type on a 500 status still -> RateLimit
        assertTrue(AnthropicApiException.of(500, "rate_limit_error", "m", "r")
                instanceof AnthropicApiException.AnthropicRateLimitException);
    }

    @Test
    public void ofMapsByStatusWhenTypeNull() {
        assertTrue(AnthropicApiException.of(429, null, "m", null)
                instanceof AnthropicApiException.AnthropicRateLimitException);
        assertTrue(AnthropicApiException.of(529, null, "m", null)
                instanceof AnthropicApiException.AnthropicOverloadedException);
        assertTrue(AnthropicApiException.of(401, null, "m", null)
                instanceof AnthropicApiException.AnthropicAuthenticationException);
        assertTrue(AnthropicApiException.of(403, null, "m", null)
                instanceof AnthropicApiException.AnthropicAuthenticationException);
    }

    @Test
    public void ofFallsBackToBaseForUnknownStatusAndType() {
        AnthropicApiException e = AnthropicApiException.of(500, "api_error", "boom", "req-1");
        // exact base class, not one of the subclasses
        assertEquals(AnthropicApiException.class, e.getClass());
        assertEquals(500, e.getStatus());
        assertEquals("api_error", e.getType());
        assertEquals("boom", e.getMessage());
        assertEquals("req-1", e.getRequestId());
    }
}
