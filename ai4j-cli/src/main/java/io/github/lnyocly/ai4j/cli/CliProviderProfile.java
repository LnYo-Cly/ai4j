package io.github.lnyocly.ai4j.cli;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CliProviderProfile {

    private String provider;

    private String protocol;

    private String model;

    private String baseUrl;

    private String apiKey;
}
