package io.github.lnyocly.ai4j.systemone.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Response body of {@code POST /v1/systemone}: the resolved model name, typed
 * answers keyed by question name, and token usage.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemOneResponse {

    private String model;

    private Map<String, SystemOneAnswer> answers;

    private SystemOneUsage usage;

    /** Answers whose {@code type} is {@code choice}. */
    public Map<String, SystemOneAnswer> choices() {
        return answersOfType("choice");
    }

    /** Answers whose {@code type} is {@code score}. */
    public Map<String, SystemOneAnswer> scores() {
        return answersOfType("score");
    }

    /** Answers whose {@code type} is {@code noul}. */
    public Map<String, SystemOneAnswer> nouls() {
        return answersOfType("noul");
    }

    /** Winning label of the Choice answer stored under {@code name}, or null. */
    public String choice(String name) {
        SystemOneAnswer answer = answer(name);
        return answer == null ? null : answer.getChoice();
    }

    /** Chosen level index of the Score answer stored under {@code name}, or null. */
    public Double score(String name) {
        SystemOneAnswer answer = answer(name);
        return answer == null ? null : answer.getScore();
    }

    /** "Yes" probability of the Noul answer stored under {@code name}, or null. */
    public Double noul(String name) {
        SystemOneAnswer answer = answer(name);
        return answer == null ? null : answer.getNoul();
    }

    /** Model-reported confidence of the answer stored under {@code name}, or null. */
    public Double confidence(String name) {
        SystemOneAnswer answer = answer(name);
        return answer == null ? null : answer.getConfidence();
    }

    private SystemOneAnswer answer(String name) {
        if (answers == null || name == null) {
            return null;
        }
        return answers.get(name);
    }

    private Map<String, SystemOneAnswer> answersOfType(String type) {
        if (answers == null || answers.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, SystemOneAnswer> filtered = new LinkedHashMap<String, SystemOneAnswer>();
        for (Map.Entry<String, SystemOneAnswer> entry : answers.entrySet()) {
            SystemOneAnswer answer = entry.getValue();
            if (answer != null && type.equals(answer.getType())) {
                filtered.put(entry.getKey(), answer);
            }
        }
        return filtered;
    }
}
