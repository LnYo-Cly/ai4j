package io.github.lnyocly.ai4j.systemone.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request body for {@code POST /v1/systemone}.
 *
 * <p>{@code state} accepts EntryType: a String, a JSON object ({@code Map}),
 * an array ({@code List}), or null. {@code questions} is keyed by the names
 * that identify the returned answers.</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemOneRequest {

    private Object state;

    private String model;

    private Map<String, SystemOneQuestion> questions;

    public static SystemOneRequest of(Object state, String model, Map<String, ? extends SystemOneQuestion> questions) {
        SystemOneRequest request = new SystemOneRequest();
        request.setState(state);
        request.setModel(model);
        if (questions != null) {
            request.setQuestions(new LinkedHashMap<String, SystemOneQuestion>(questions));
        }
        return request;
    }
}
