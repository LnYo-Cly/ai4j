package io.github.lnyocly.ai4j.flowgram.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowGramTaskResultResponse {

    private String taskId;
    private String status;
    private boolean terminated;
    private String error;
    private Map<String, Object> result;
}
