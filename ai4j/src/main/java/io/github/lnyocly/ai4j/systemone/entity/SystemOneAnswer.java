package io.github.lnyocly.ai4j.systemone.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

/**
 * One typed answer keyed by its question name.
 *
 * <p>Read the field matching the question type: {@link #choice} for
 * {@code "choice"}, {@link #score} for {@code "score"}, {@link #noul} for
 * {@code "noul"}. Choice and Score also carry {@link #probabilities} and
 * {@link #confidence}; Score answers include {@link #legend} mapping level
 * indices back to their descriptions.</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemOneAnswer {

    private String type;

    /** Winning option label for Choice answers. */
    private String choice;

    /** Chosen level index for Score answers. */
    private Double score;

    /** Probability that the answer is "yes" (0..1) for Noul answers. */
    private Double noul;

    /** Per-option (Choice) or per-level (Score) probability distribution. */
    private Map<String, Double> probabilities;

    /** Model-reported certainty for Choice and Score answers (0..1). */
    private Double confidence;

    /** Score level index → description echo. */
    private Map<String, String> legend;
}
