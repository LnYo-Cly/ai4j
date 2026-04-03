package io.github.lnyocly.ai4j.coding.process;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class StoredProcessSnapshot {

    private String processId;

    private String command;

    private String workingDirectory;

    private BashProcessStatus status;

    private Long pid;

    private Integer exitCode;

    private long startedAt;

    private Long endedAt;

    private long lastLogOffset;

    private String lastLogPreview;

    private boolean restored;

    private boolean controlAvailable;
}
