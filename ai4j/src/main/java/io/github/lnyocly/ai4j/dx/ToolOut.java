package io.github.lnyocly.ai4j.dx;

/**
 * Pi-style dual-channel tool execution output.
 *
 * <p>{@link #getContent()} is sent back to the LLM context (compact text summary),
 * while {@link #getDetails()} carries strongly-typed UI / audit data that never
 * enters the model prompt context.</p>
 */
public final class ToolOut<D> {

    private final String content;
    private final D details;
    private final boolean error;

    private ToolOut(String content, D details, boolean error) {
        this.content = content == null ? "" : content;
        this.details = details;
        this.error = error;
    }

    public static <D> ToolOut<D> of(String content, D details) {
        return new ToolOut<D>(content, details, false);
    }

    public static ToolOut<Void> text(String content) {
        return new ToolOut<Void>(content, null, false);
    }

    public static <D> ToolOut<D> error(String content, D details) {
        return new ToolOut<D>(content, details, true);
    }

    public String getContent() {
        return content;
    }

    public D getDetails() {
        return details;
    }

    public boolean isError() {
        return error;
    }
}
