package io.github.lnyocly.ai4j.flowgram.springboot.exception;

import io.github.lnyocly.ai4j.flowgram.springboot.security.FlowGramAction;
import org.springframework.http.HttpStatus;

public class FlowGramAccessDeniedException extends FlowGramApiException {

    public FlowGramAccessDeniedException(FlowGramAction action) {
        super(HttpStatus.FORBIDDEN,
                "FLOWGRAM_ACCESS_DENIED",
                "Access denied for FlowGram action: " + action);
    }
}
