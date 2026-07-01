package io.github.lnyocly.ai4j.agent.compact;

import io.github.lnyocly.ai4j.agent.context.ContextReport;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompactResult {

    private MemorySnapshot memory;

    private String summary;

    private List<String> completed;

    private List<String> pending;

    private List<String> decisions;

    private List<String> changedArtifacts;

    private List<String> failedCommands;

    private List<String> testResults;

    private List<String> userConfirmations;

    private List<String> sandboxState;

    private List<String> openQuestions;

    private List<String> readFiles;

    private List<String> modifiedFiles;

    private ContextReport contextReport;

    public List<String> getCompleted() {
        return copy(completed);
    }

    public List<String> getPending() {
        return copy(pending);
    }

    public List<String> getDecisions() {
        return copy(decisions);
    }

    public List<String> getChangedArtifacts() {
        return copy(changedArtifacts);
    }

    public List<String> getFailedCommands() {
        return copy(failedCommands);
    }

    public List<String> getTestResults() {
        return copy(testResults);
    }

    public List<String> getUserConfirmations() {
        return copy(userConfirmations);
    }

    public List<String> getSandboxState() {
        return copy(sandboxState);
    }

    public List<String> getOpenQuestions() {
        return copy(openQuestions);
    }

    public List<String> getReadFiles() {
        return copy(readFiles);
    }

    public List<String> getModifiedFiles() {
        return copy(modifiedFiles);
    }

    public MemorySnapshot getMemory() {
        return memory == null ? null : MemorySnapshot.from(memory.getItems(), memory.getSummary());
    }

    public void setMemory(MemorySnapshot memory) {
        this.memory = memory == null ? null : MemorySnapshot.from(memory.getItems(), memory.getSummary());
    }

    public ContextReport getContextReport() {
        return contextReport == null ? null : contextReport.copy();
    }

    public void setContextReport(ContextReport contextReport) {
        this.contextReport = contextReport == null ? null : contextReport.copy();
    }

    public CompactResult copy() {
        return CompactResult.builder()
                .memory(getMemory())
                .summary(summary)
                .completed(getCompleted())
                .pending(getPending())
                .decisions(getDecisions())
                .changedArtifacts(getChangedArtifacts())
                .failedCommands(getFailedCommands())
                .testResults(getTestResults())
                .userConfirmations(getUserConfirmations())
                .sandboxState(getSandboxState())
                .openQuestions(getOpenQuestions())
                .readFiles(getReadFiles())
                .modifiedFiles(getModifiedFiles())
                .contextReport(getContextReport())
                .build();
    }

    private static List<String> copy(List<String> source) {
        return source == null ? new ArrayList<String>() : new ArrayList<String>(source);
    }
}
