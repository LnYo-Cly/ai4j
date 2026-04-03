package io.github.lnyocly.ai4j.coding.patch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyPatchResult {

    private int filesChanged;

    private int operationsApplied;

    private List<String> changedFiles;

    private List<ApplyPatchFileChange> fileChanges;
}
