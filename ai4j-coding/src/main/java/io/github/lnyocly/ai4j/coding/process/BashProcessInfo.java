package io.github.lnyocly.ai4j.coding.process;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BashProcessInfo {

    private String processId;

    private String command;

    private String workingDirectory;

    private BashProcessStatus status;

    private Long pid;

    private Integer exitCode;

    private long startedAt;

    private Long endedAt;

    private boolean restored;

    private boolean controlAvailable;
}
