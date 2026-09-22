package io.github.lnyocly.ai4j.systemone.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request body for {@code POST /v1/systemone}.
 *
 * <p>{@code state} accepts EntryType: a String, a JSON object ({@code Map}),
 * an array ({@code List}), or null. {@code questions} is keyed by the names
 * that identify the returned answers.</p>
 *
 * <p>Prefer the fluent builder for the common case:</p>
 * <pre>{@code
 * SystemOneRequest request = SystemOneRequest.builder()
 *         .state(state)
 *         .model("jev-latest")
 *         .choice("route", "Which team should handle this?", routeOptions)
 *         .score("urgency", "How urgent is the request?", levels)
 *         .noul("contains_pii", "Does it contain personal data?", null)
 *         .build();
 * }</pre>
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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

    /**
     * Builder with one typed method per System One question kind. Each method
     * appends a question under {@code name}; insertion order is preserved, and
     * a later question with the same name replaces the earlier one.
     */
    public static class SystemOneRequestBuilder {

        /** Append a Choice question: the model picks one label from {@code criteria}. */
        public SystemOneRequestBuilder choice(String name, Object instructions, Map<String, Object> criteria) {
            return question(name, ChoiceQuestion.of(instructions, criteria));
        }

        /** Append a Score question: the model picks one level index from ordered {@code criteria}. */
        public SystemOneRequestBuilder score(String name, Object instructions, List<Object> criteria) {
            return question(name, ScoreQuestion.of(instructions, criteria));
        }

        /** Append a Noul question without branch descriptions. */
        public SystemOneRequestBuilder noul(String name, Object instructions) {
            return question(name, NoulQuestion.of(instructions));
        }

        /** Append a Noul question with explicit {@code true}/{@code false} branch descriptions. */
        public SystemOneRequestBuilder noul(String name, Object instructions, NoulCriteria criteria) {
            return question(name, NoulQuestion.of(instructions, criteria));
        }

        /** Append an already-built question under {@code name}. */
        public SystemOneRequestBuilder question(String name, SystemOneQuestion question) {
            if (name == null || question == null) {
                throw new IllegalArgumentException("SystemOne question name and value must not be null");
            }
            if (this.questions == null) {
                this.questions = new LinkedHashMap<String, SystemOneQuestion>();
            }
            this.questions.put(name, question);
            return this;
        }
    }
}
