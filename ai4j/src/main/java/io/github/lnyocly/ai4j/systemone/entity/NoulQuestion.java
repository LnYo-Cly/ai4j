package io.github.lnyocly.ai4j.systemone.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Noul question: evaluate a yes/no statement about the state. The answer is a
 * single number — the probability that the answer is "yes" (0..1).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NoulQuestion extends SystemOneQuestion {

    private NoulCriteria criteria;

    public NoulQuestion() {
        super("noul");
    }

    public static NoulQuestion of(Object instructions) {
        return of(instructions, null);
    }

    public static NoulQuestion of(Object instructions, NoulCriteria criteria) {
        NoulQuestion question = new NoulQuestion();
        question.setInstructions(instructions);
        question.setCriteria(criteria);
        return question;
    }
}
