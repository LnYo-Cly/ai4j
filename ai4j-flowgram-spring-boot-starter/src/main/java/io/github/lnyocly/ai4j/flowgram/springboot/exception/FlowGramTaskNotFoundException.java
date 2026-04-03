package io.github.lnyocly.ai4j.flowgram.springboot.exception;

import org.springframework.http.HttpStatus;

public class FlowGramTaskNotFoundException extends FlowGramApiException {

    public FlowGramTaskNotFoundException(String taskId) {
        super(HttpStatus.NOT_FOUND,
                "FLOWGRAM_TASK_NOT_FOUND",
                "FlowGram task not found: " + taskId);
    }
}
