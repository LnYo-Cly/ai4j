package io.github.lnyocly.ai4j.coding.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceWriteResult {

    private String path;

    private long bytesWritten;

    private boolean created;

    private boolean appended;
}
