package io.github.lnyocly.ai4j.systemone.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Choice question: pick one option from a labelled set.
 *
 * <p>{@code criteria} maps each option label to an optional description
 * (EntryType: String, Map, List, or null). Answer returns the winning label
 * plus per-option probabilities and confidence.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChoiceQuestion extends SystemOneQuestion {

    private Map<String, Object> criteria;

    public ChoiceQuestion() {
        super("choice");
    }

    public static ChoiceQuestion of(Object instructions, Map<String, Object> criteria) {
        ChoiceQuestion question = new ChoiceQuestion();
        question.setInstructions(instructions);
        question.setCriteria(criteria == null ? null : new LinkedHashMap<String, Object>(criteria));
        return question;
    }
}
