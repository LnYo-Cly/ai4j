package io.github.lnyocly.ai4j.coding.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CodingPromptDescriptor {

    private String name;
    private String description;
    private String promptFilePath;
    private String source;
}
