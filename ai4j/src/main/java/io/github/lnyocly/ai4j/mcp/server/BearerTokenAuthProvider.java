package io.github.lnyocly.ai4j.mcp.server;

import com.sun.net.httpserver.HttpExchange;

import java.security.SecureRandom;
import java.util.List;

/**
 * Default {@link McpAuthProvider} that validates an HTTP {@code Authorization: Bearer <token>}
 * header against an expected token. The token is compared in constant time to resist timing
 * side-channels.
 *
 * <p>If no token is supplied at construction time, a cryptographically random token is generated
 * so that token auth is effective out of the box. The token is exposed via {@link #getToken()}
 * so the caller (typically the MCP server at startup) can log it for the operator.</p>
 */
public class BearerTokenAuthProvider implements McpAuthProvider {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final String token;

    /**
     * Generates a random bearer token.
     */
    public BearerTokenAuthProvider() {
        this(generateToken());
    }

    /**
     * Uses the supplied token verbatim.
     *
     * @param token the expected bearer token (must not be blank)
     */
    public BearerTokenAuthProvider(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Bearer token must not be blank");
        }
        this.token = token;
    }

    @Override
    public boolean authenticate(HttpExchange exchange) {
        if (exchange == null) {
            return false;
        }
        List<String> headers = exchange.getRequestHeaders().get(AUTHORIZATION_HEADER);
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        for (String header : headers) {
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                String presented = header.substring(BEARER_PREFIX.length()).trim();
                if (constantTimeEquals(presented, token)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String describe() {
        String masked = token.length() <= 8
                ? "****"
                : token.substring(0, 4) + "..." + token.substring(token.length() - 4);
        return "Bearer token: " + masked;
    }

    /** Returns the full token so the server can log it at startup. */
    public String getToken() {
        return token;
    }

    private static String generateToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Compares two strings in constant time to avoid timing side-channels.
     * Returns {@code true} only when the strings have the same length and all characters match.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
