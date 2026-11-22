package io.github.lnyocly.ai4j.model;

/** Who is expected to execute a declared tool (design 6.2). */
public enum ToolKind {

    /** Host-side function declared with a JSON Schema. */
    FUNCTION,

    /** Provider-hosted tool (web search, code interpreter, ...). */
    HOSTED,

    /** Deferred declaration loaded by provider-side retrieval. */
    DEFERRED_REF,

    /** Protocol-native declaration passed through verbatim. */
    NATIVE
}
