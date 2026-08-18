package io.github.lnyocly.ai4j.platform.openai.video.entity;

/**
 * Wire format used when creating a video task.
 *
 * <p>{@link #JSON} is the default and matches every first-party video API we target
 * (OpenAI, Grok/xAI, and the other official subscriptions). {@link #MULTIPART} exists
 * only for legacy relay gateways that still require {@code multipart/form-data}; it is
 * deprecated and will be removed once those relays are retired.
 */
public enum VideoBodyMode {
    JSON,
    MULTIPART
}
