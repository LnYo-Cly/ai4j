package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TypeSafe AI (Jev / System One) platform configuration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypeSafeConfig {

    private String apiHost = "https://api.typesafe.ai/";

    private String apiKey = "";

    private String systemOneUrl = "v1/systemone";

    private String modelsUrl = "v1/models";

    private String defaultModel = "jev-latest";
}
