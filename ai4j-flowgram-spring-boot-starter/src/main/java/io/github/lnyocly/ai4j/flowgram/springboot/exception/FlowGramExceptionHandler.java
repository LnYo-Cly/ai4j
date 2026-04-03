package io.github.lnyocly.ai4j.flowgram.springboot.exception;

import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class FlowGramExceptionHandler {

    @ExceptionHandler(FlowGramApiException.class)
    public ResponseEntity<FlowGramErrorResponse> handleFlowGramApiException(FlowGramApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(error(ex.getCode(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<FlowGramErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("FLOWGRAM_BAD_REQUEST", ex.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<FlowGramErrorResponse> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("FLOWGRAM_RUNTIME_ERROR", ex.getMessage(), null));
    }

    private FlowGramErrorResponse error(String code, String message, Object details) {
        return FlowGramErrorResponse.builder()
                .code(code)
                .message(message)
                .details(details)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
