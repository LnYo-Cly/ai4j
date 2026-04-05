package io.github.lnyocly.ai4j.coding.patch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyPatchFileChange {

    private String path;

    private String operation;

    private int linesAdded;

    private int linesRemoved;
}
