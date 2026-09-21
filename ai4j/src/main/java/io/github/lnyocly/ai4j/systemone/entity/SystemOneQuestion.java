package io.github.lnyocly.ai4j.systemone.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Base System One question. A question is one typed judgment the model makes
 * about the request {@code state}; all questions in a request are evaluated
 * in parallel and independently.
 *
 * <p>{@code instructions} accepts the TypeSafe {@code EntryType}:
 * a String, a JSON object ({@code Map}), an array ({@code List}), or null.</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class SystemOneQuestion {

    private final String type;

    private Object instructions;

    protected SystemOneQuestion(String type) {
        this.type = type;
    }
}
