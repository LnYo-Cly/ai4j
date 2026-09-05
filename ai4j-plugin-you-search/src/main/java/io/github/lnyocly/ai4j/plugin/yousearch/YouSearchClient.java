package io.github.lnyocly.ai4j.plugin.yousearch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Thin HTTP client for the You.com Search API. Java 8 compatible, no external dependencies.
 *
 * <p>Endpoint and API key are resolved at call time: the key comes from the
 * {@code YDC_API_KEY} environment variable (never hardcoded, never logged), and the
 * endpoint defaults to {@link YouSearchExtension#DEFAULT_SEARCH_ENDPOINT} but can be
 * overridden with the system property {@code ai4j.extensions.you-search.baseUrl}.</p>
 */
final class YouSearchClient {

    private static final int CONNECT_TIMEOUT_MS = 10 * 1000;
    private static final int READ_TIMEOUT_MS = 30 * 1000;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final int MAX_SNIPPET_CHARS = 800;
    private static final int DEFAULT_NUM_RESULTS = 5;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private YouSearchClient() {
    }

    /** Entry point used by the tool executor and the CLI command. Never throws. */
    static String search(String arguments) {
        String query = YouSearchPayloads.extractQuery(arguments);
        if (query == null || query.isEmpty()) {
            return YouSearchPayloads.errorResponse("",
                    "missing_query",
                    "Provide tool arguments like {\"query\":\"...\"} or /you-search <query>.");
        }
        Integer requested = YouSearchPayloads.extractNumResults(arguments);
        int numResults = requested == null ? DEFAULT_NUM_RESULTS : requested.intValue();

        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return YouSearchPayloads.errorResponse(query,
                    "missing_api_key",
                    "Set the YDC_API_KEY environment variable (https://you.com/platform/api-keys) or use the keyless You.com MCP server profile instead.");
        }

        String endpoint = resolveEndpoint();
        String body = "{\"query\":" + quote(query) + ",\"numResults\":" + numResults + "}";

        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-API-Key", apiKey.trim());
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            connection.setDoOutput(true);
            byte[] payload = body.getBytes(UTF8);
            connection.setFixedLengthStreamingMode(payload.length);
            OutputStream output = connection.getOutputStream();
            try {
                output.write(payload);
            } finally {
                closeQuietly(output);
            }

            int status = connection.getResponseCode();
            String response = readBounded(connection, status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            if (status < 200 || status >= 300) {
                return YouSearchPayloads.errorResponse(query,
                        "http_" + status,
                        "You.com Search API returned HTTP " + status + ". Check YDC_API_KEY and the endpoint URL.");
            }
            List<Map<String, String>> hits = YouApiResults.extractHits(response);
            if (hits.isEmpty()) {
                return YouSearchPayloads.errorResponse(query,
                        "unparsable_response",
                        "The search endpoint responded but no result fields were recognized.");
            }
            return YouSearchPayloads.searchResponse(query, Math.min(numResults, hits.size()), hits);
        } catch (IOException ex) {
            return YouSearchPayloads.errorResponse(query,
                    "network_error",
                    "Could not reach " + endpoint + ": " + safeMessage(ex));
        } catch (RuntimeException ex) {
            return YouSearchPayloads.errorResponse(query,
                    "unexpected_error",
                    safeMessage(ex));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String resolveApiKey() {
        String key = System.getenv(YouSearchExtension.API_KEY_ENV);
        if (key == null || key.trim().isEmpty()) {
            key = System.getProperty("ai4j.extensions.you-search.apiKey");
        }
        return key;
    }

    private static String resolveEndpoint() {
        String endpoint = System.getProperty("ai4j.extensions.you-search.baseUrl");
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return YouSearchExtension.DEFAULT_SEARCH_ENDPOINT;
        }
        return endpoint.trim();
    }

    private static String quote(String value) {
        return "\"" + YouSearchPayloads.escapeJson(value) + "\"";
    }

    private static String readBounded(HttpURLConnection connection, InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int total = 0;
        try {
            int read;
            while ((read = stream.read(chunk)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("response body exceeded " + MAX_RESPONSE_BYTES + " bytes");
                }
                buffer.write(chunk, 0, read);
            }
        } finally {
            closeQuietly(stream);
        }
        return new String(buffer.toByteArray(), UTF8);
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null ? ex.getClass().getSimpleName() : message;
    }

    /**
     * Extracts top-level result fields from a You.com Search API JSON response.
     * Flat string extraction keeps the parser small and Java 8 friendly.
     */
    static final class YouApiResults {

        private YouApiResults() {
        }

        static List<Map<String, String>> extractHits(String responseJson) {
            List<Map<String, String>> hits = new ArrayList<Map<String, String>>();
            if (responseJson == null) {
                return hits;
            }
            int index = 0;
            String resultsKey = locateResultsArray(responseJson, index);
            while (resultsKey != null) {
                index = scanResultsArray(responseJson, resultsKey, hits);
                resultsKey = locateResultsArray(responseJson, index);
                if (hits.size() >= 20) {
                    break;
                }
            }
            if (hits.isEmpty()) {
                // fallback: {"title": "...", "url": "...", "description": "..."} flat object
                Map<String, String> flat = YouSearchPayloads.readFlatObject(responseJson.trim());
                if (flat != null && (flat.containsKey("url") || flat.containsKey("hits"))) {
                    addHit(hits, flat.get("title"), flat.get("url"), flat.get("description"));
                }
            }
            return hits;
        }

        private static String locateResultsArray(String source, int from) {
            String[] keys = {"\"results\"", "\"hits\"", "\"news\""};
            int best = -1;
            String bestKey = null;
            for (String key : keys) {
                int at = source.indexOf(key, from);
                if (at >= 0 && (best == -1 || at < best)) {
                    best = at;
                    bestKey = key;
                }
            }
            if (bestKey == null) {
                return null;
            }
            int colon = best + bestKey.length();
            while (colon < source.length() && Character.isWhitespace(source.charAt(colon))) {
                colon++;
            }
            if (colon >= source.length() || source.charAt(colon) != ':') {
                return null;
            }
            colon++;
            while (colon < source.length() && Character.isWhitespace(source.charAt(colon))) {
                colon++;
            }
            if (colon >= source.length() || source.charAt(colon) != '[') {
                return null;
            }
            return source.substring(best, best + bestKey.length()) + "@" + colon;
        }

        private static int scanResultsArray(String source, String keyAndOffset, List<Map<String, String>> hits) {
            int at = Integer.parseInt(keyAndOffset.substring(keyAndOffset.indexOf('@') + 1));
            int i = at + 1;
            int depth = 1;
            int objectStart = -1;
            while (i < source.length() && depth > 0) {
                char ch = source.charAt(i);
                if (ch == '{') {
                    if (depth == 1) {
                        objectStart = i;
                    }
                    depth++;
                } else if (ch == '}') {
                    depth--;
                    if (depth == 1 && objectStart >= 0) {
                        String object = source.substring(objectStart, i + 1);
                        Map<String, String> flat = YouSearchPayloads.readFlatObject(object);
                        if (flat != null) {
                            addHit(hits,
                                    firstNonEmpty(flat.get("title"), flat.get("name")),
                                    firstNonEmpty(flat.get("url"), flat.get("link")),
                                    firstNonEmpty(flat.get("description"), flat.get("snippet")));
                        }
                        objectStart = -1;
                    }
                } else if (ch == '[') {
                    depth++;
                } else if (ch == ']') {
                    depth--;
                } else if (ch == '"') {
                    i = skipString(source, i);
                }
                i++;
            }
            return i;
        }

        private static int skipString(String source, int start) {
            int i = start + 1;
            while (i < source.length()) {
                char ch = source.charAt(i);
                if (ch == '\\') {
                    i += 2;
                } else if (ch == '"') {
                    return i;
                } else {
                    i++;
                }
            }
            return i;
        }

        private static String firstNonEmpty(String... values) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
            return null;
        }

        private static void addHit(List<Map<String, String>> hits, String title, String url, String snippet) {
            if (url == null && title == null) {
                return;
            }
            Map<String, String> hit = new java.util.LinkedHashMap<String, String>();
            hit.put("title", cap(title == null ? "" : title));
            hit.put("url", url == null ? "" : url);
            hit.put("snippet", cap(snippet == null ? "" : snippet));
            hits.add(hit);
        }

        private static String cap(String value) {
            if (value.length() <= MAX_SNIPPET_CHARS) {
                return value;
            }
            return value.substring(0, MAX_SNIPPET_CHARS) + "…";
        }
    }
}
