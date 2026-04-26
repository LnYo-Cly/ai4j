package io.github.lnyocly.ai4j.flowgram.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowGramTaskCancelResponse {

    private boolean success;
}
