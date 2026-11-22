package io.github.lnyocly.ai4j.model;

/** Identity kind of a media reference. */
public enum MediaKind {

    /** Publicly reachable URL. */
    URL,

    /** Inline base64 payload owned by the caller. */
    INLINE_BASE64,

    /** Provider-scoped uploaded file id; replay requires matching endpoint/account. */
    PROVIDER_FILE_ID
}
