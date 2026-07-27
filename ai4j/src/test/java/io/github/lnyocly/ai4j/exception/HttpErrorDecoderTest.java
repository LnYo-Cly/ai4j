package io.github.lnyocly.ai4j.exception;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link HttpErrorDecoder} status-code classification.
 */
public class HttpErrorDecoderTest {

    @Test
    public void decode401_returnsAiAuthException() {
        AiHttpException ex = HttpErrorDecoder.decode(401, "Unauthorized");
        assertTrue(ex instanceof AiAuthException);
        assertEquals(401, ex.getStatusCode());
        assertEquals("Unauthorized", ex.getMessage());
    }

    @Test
    public void decode403_returnsAiAuthException() {
        AiHttpException ex = HttpErrorDecoder.decode(403, "Forbidden");
        assertTrue(ex instanceof AiAuthException);
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    public void decode429_returnsAiRateLimitException() {
        AiHttpException ex = HttpErrorDecoder.decode(429, "Too Many Requests");
        assertTrue(ex instanceof AiRateLimitException);
        assertEquals(429, ex.getStatusCode());
    }

    @Test
    public void decode408_returnsAiTimeoutException() {
        AiHttpException ex = HttpErrorDecoder.decode(408, "Request Timeout");
        assertTrue(ex instanceof AiTimeoutException);
        assertEquals(408, ex.getStatusCode());
    }

    @Test
    public void decode504_returnsAiTimeoutException() {
        AiHttpException ex = HttpErrorDecoder.decode(504, "Gateway Timeout");
        assertTrue(ex instanceof AiTimeoutException);
        assertEquals(504, ex.getStatusCode());
    }

    @Test
    public void decode500_returnsAiServerErrorException() {
        AiHttpException ex = HttpErrorDecoder.decode(500, "Internal Server Error");
        assertTrue(ex instanceof AiServerErrorException);
        assertEquals(500, ex.getStatusCode());
    }

    @Test
    public void decode503_returnsAiServerErrorException() {
        AiHttpException ex = HttpErrorDecoder.decode(503, "Service Unavailable");
        assertTrue(ex instanceof AiServerErrorException);
        assertEquals(503, ex.getStatusCode());
    }

    @Test
    public void decode400_returnsAiClientException() {
        AiHttpException ex = HttpErrorDecoder.decode(400, "Bad Request");
        assertTrue(ex instanceof AiClientException);
        assertFalse(ex instanceof AiAuthException);
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    public void decode404_returnsAiClientException() {
        AiHttpException ex = HttpErrorDecoder.decode(404, "Not Found");
        assertTrue(ex instanceof AiClientException);
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    public void decodeWithNullMessage_usesHttpStatusCodeAsMessage() {
        AiHttpException ex = HttpErrorDecoder.decode(500, null);
        assertEquals("HTTP 500", ex.getMessage());
    }

    @Test
    public void decodeWithEmptyMessage_usesHttpStatusCodeAsMessage() {
        AiHttpException ex = HttpErrorDecoder.decode(500, "");
        assertEquals("HTTP 500", ex.getMessage());
    }

    @Test
    public void allExceptionsExtendAiHttpException() {
        assertTrue(new AiAuthException(401, "msg") instanceof AiHttpException);
        assertTrue(new AiRateLimitException(429, "msg") instanceof AiHttpException);
        assertTrue(new AiTimeoutException(408, "msg") instanceof AiHttpException);
        assertTrue(new AiServerErrorException(500, "msg") instanceof AiHttpException);
        assertTrue(new AiClientException(400, "msg") instanceof AiHttpException);
    }

    @Test
    public void allExceptionsExtendAi4jException() {
        assertTrue(new AiAuthException(401, "msg") instanceof Ai4jException);
        assertTrue(new AiClientException(400, "msg") instanceof Ai4jException);
    }

    @Test
    public void exceptionWithCause_preservesCause() {
        Throwable cause = new RuntimeException("root cause");
        AiHttpException ex = new AiAuthException(401, "Unauthorized", cause);
        assertSame(cause, ex.getCause());
    }
}
