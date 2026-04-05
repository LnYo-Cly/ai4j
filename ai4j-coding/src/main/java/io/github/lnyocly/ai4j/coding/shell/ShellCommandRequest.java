package io.github.lnyocly.ai4j.coding.shell;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShellCommandRequest {

    private String command;

    private String workingDirectory;

    private Long timeoutMs;
}
