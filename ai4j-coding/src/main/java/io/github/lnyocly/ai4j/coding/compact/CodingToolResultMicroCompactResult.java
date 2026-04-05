package io.github.lnyocly.ai4j.coding.compact;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CodingToolResultMicroCompactResult {

    private List<Object> items;

    private int beforeTokens;

    private int afterTokens;

    private int compactedToolResultCount;

    private String summary;
}
