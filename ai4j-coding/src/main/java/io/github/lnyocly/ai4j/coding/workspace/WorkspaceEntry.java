package io.github.lnyocly.ai4j.coding.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceEntry {

    private String path;

    private boolean directory;

    private long size;
}
