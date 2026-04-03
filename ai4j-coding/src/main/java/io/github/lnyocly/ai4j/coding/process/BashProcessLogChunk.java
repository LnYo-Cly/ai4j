package io.github.lnyocly.ai4j.coding.process;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BashProcessLogChunk {

    private String processId;

    private long offset;

    private long nextOffset;

    private boolean truncated;

    private String content;

    private BashProcessStatus status;

    private Integer exitCode;
}
