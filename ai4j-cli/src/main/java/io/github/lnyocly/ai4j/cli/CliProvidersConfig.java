package io.github.lnyocly.ai4j.cli;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CliProvidersConfig {

    private String defaultProfile;

    @Builder.Default
    private Map<String, CliProviderProfile> profiles = new LinkedHashMap<String, CliProviderProfile>();
}
