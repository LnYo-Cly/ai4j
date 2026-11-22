package io.github.lnyocly.ai4j.service.nativeapi;

import java.util.Map;

/** Maps a non-2xx HTTP response to the protocol's native exception type. */
interface NativeErrorMapper {

    RuntimeException fromHttp(int statusCode, String bodyText, Map<String, String> headers);
}
