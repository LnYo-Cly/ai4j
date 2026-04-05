package io.github.lnyocly.ai4j.coding.shell;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShellCommandResult {

    private String command;

    private String workingDirectory;

    private String stdout;

    private String stderr;

    private int exitCode;

    private boolean timedOut;
}
