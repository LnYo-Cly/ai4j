package io.github.lnyocly.ai4j.coding.loop;

import io.github.lnyocly.ai4j.coding.CodingAgentResult;
import io.github.lnyocly.ai4j.coding.CodingSessionCheckpoint;
import io.github.lnyocly.ai4j.coding.CodingSessionCompactResult;
import io.github.lnyocly.ai4j.coding.process.StoredProcessSnapshot;

import java.util.List;

public final class CodingContinuationPrompt {

    private CodingContinuationPrompt() {
    }

    public static String build(CodingLoopDecision decision,
                               CodingAgentResult previousResult,
                               CodingSessionCompactResult compactResult,
                               int nextTurnNumber) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Internal continuation. This is not a new user message. ");
        prompt.append("Continue the same coding task using the existing workspace and session context.\n");
        prompt.append("Do not restate prior work or ask the user to repeat information already present.\n");
        prompt.append("If the task is already complete, respond with a concise completion summary and stop.\n");
        prompt.append("If user clarification or approval is required, state that clearly and stop.\n");
        prompt.append("Otherwise continue directly with the next concrete step, using tools when useful.\n");
        if (decision != null && decision.getContinueReason() != null) {
            prompt.append("Continuation reason: ").append(decision.getContinueReason()).append(".\n");
        }
        if (decision != null && decision.isCompactApplied()) {
            prompt.append("A context compaction was just applied. Re-anchor yourself from the retained checkpoint and recent messages.\n");
            appendCompactReanchor(prompt, compactResult);
        }
        if (previousResult != null && previousResult.getOutputText() != null && !previousResult.getOutputText().trim().isEmpty()) {
            prompt.append("Latest assistant output:\n");
            prompt.append(previousResult.getOutputText().trim()).append("\n");
        }
        prompt.append("Continuation turn #").append(nextTurnNumber).append(".");
        return prompt.toString();
    }

    private static void appendCompactReanchor(StringBuilder prompt, CodingSessionCompactResult compactResult) {
        if (prompt == null || compactResult == null) {
            return;
        }
        if (!isBlank(compactResult.getStrategy())) {
            prompt.append("Compaction strategy: ").append(compactResult.getStrategy()).append(".\n");
        }
        if (compactResult.isCheckpointReused()) {
            prompt.append("The existing checkpoint was updated with new delta context.\n");
        }
        CodingSessionCheckpoint checkpoint = compactResult.getCheckpoint();
        if (checkpoint != null) {
            if (!isBlank(checkpoint.getGoal())) {
                prompt.append("Checkpoint goal: ").append(clip(checkpoint.getGoal(), 220)).append("\n");
            }
            if (checkpoint.isSplitTurn()) {
                prompt.append("This checkpoint came from a split-turn compaction. Use the kept recent messages as the latest turn tail.\n");
            }
            appendList(prompt, "Checkpoint constraints", checkpoint.getConstraints(), 3);
            appendList(prompt, "Checkpoint blocked items", checkpoint.getBlockedItems(), 2);
            appendList(prompt, "Checkpoint next steps", checkpoint.getNextSteps(), 3);
            appendList(prompt, "Checkpoint critical context", checkpoint.getCriticalContext(), 3);
            appendList(prompt, "Checkpoint in-progress items", checkpoint.getInProgressItems(), 2);
            appendProcesses(prompt, checkpoint.getProcessSnapshots(), 2);
            return;
        }
        if (!isBlank(compactResult.getSummary())) {
            prompt.append("Compact summary excerpt: ")
                    .append(clip(singleLine(compactResult.getSummary()), 280))
                    .append("\n");
        }
    }

    private static void appendList(StringBuilder prompt, String label, List<String> values, int maxItems) {
        if (prompt == null || values == null || values.isEmpty() || maxItems <= 0) {
            return;
        }
        prompt.append(label).append(":\n");
        int written = 0;
        for (String value : values) {
            if (isBlank(value)) {
                continue;
            }
            prompt.append("- ").append(clip(value.trim(), 220)).append("\n");
            written += 1;
            if (written >= maxItems) {
                break;
            }
        }
    }

    private static void appendProcesses(StringBuilder prompt, List<StoredProcessSnapshot> snapshots, int maxItems) {
        if (prompt == null || snapshots == null || snapshots.isEmpty() || maxItems <= 0) {
            return;
        }
        prompt.append("Checkpoint process snapshots:\n");
        int written = 0;
        for (StoredProcessSnapshot snapshot : snapshots) {
            if (snapshot == null || isBlank(snapshot.getProcessId())) {
                continue;
            }
            StringBuilder line = new StringBuilder();
            line.append("- ").append(snapshot.getProcessId());
            if (snapshot.getStatus() != null) {
                line.append(" [").append(snapshot.getStatus()).append("]");
            }
            if (!isBlank(snapshot.getCommand())) {
                line.append(" ").append(clip(snapshot.getCommand().trim(), 140));
            }
            if (snapshot.isRestored()) {
                line.append(" (restored snapshot)");
            }
            prompt.append(line).append("\n");
            written += 1;
            if (written >= maxItems) {
                break;
            }
        }
    }

    private static String singleLine(String value) {
        return isBlank(value) ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String clip(String value, int maxChars) {
        if (isBlank(value) || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
