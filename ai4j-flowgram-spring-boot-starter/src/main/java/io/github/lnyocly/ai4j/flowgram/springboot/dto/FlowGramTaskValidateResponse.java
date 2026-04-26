package io.github.lnyocly.ai4j.flowgram.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowGramTaskValidateResponse {

    private boolean valid;
    private List<String> errors;
}
