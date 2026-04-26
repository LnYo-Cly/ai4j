package io.github.lnyocly.ai4j.coding;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodingAgentRequest {

    private String input;

    private Map<String, Object> metadata;
}
