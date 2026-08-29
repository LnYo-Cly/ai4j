package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FileHarnessConfig {

    private Path directory;

    @Builder.Default
    private String harnessId = "default";

    /**
     * Once the durable snapshot is safely written, the transient journal can
     * be replaced when it exceeds this size. A non-positive value disables
     * compaction for operators that explicitly need an untrimmed journal.
     */
    @Builder.Default
    private long journalCompactionBytes = 8L * 1024L * 1024L;

    public FileHarnessConfig(Path directory, String harnessId) {
        this.directory = directory;
        this.harnessId = harnessId;
    }
}
