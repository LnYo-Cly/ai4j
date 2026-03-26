package io.github.lnyocly.ai4j.cli;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CliWorkspaceConfig {

    private String activeProfile;

    private String modelOverride;
}
