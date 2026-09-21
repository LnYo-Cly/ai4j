package io.github.lnyocly.ai4j.systemone.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Score question: rate the state against an ordered list of descriptive levels.
 *
 * <p>{@code criteria} is the ordered level list (index 0..n); each entry accepts
 * EntryType (String, Map, List, or null). Answer returns the chosen level index
 * as a number, per-level probabilities, the legend, and confidence.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScoreQuestion extends SystemOneQuestion {

    private List<Object> criteria;

    public ScoreQuestion() {
        super("score");
    }

    public static ScoreQuestion of(Object instructions, List<Object> criteria) {
        ScoreQuestion question = new ScoreQuestion();
        question.setInstructions(instructions);
        question.setCriteria(criteria == null ? null : new ArrayList<Object>(criteria));
        return question;
    }
}
