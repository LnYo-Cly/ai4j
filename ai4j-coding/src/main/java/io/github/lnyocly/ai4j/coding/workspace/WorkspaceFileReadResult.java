package io.github.lnyocly.ai4j.coding.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceFileReadResult {

    private String path;

    private String content;

    private int startLine;

    private int endLine;

    private boolean truncated;
}
