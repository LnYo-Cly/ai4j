package io.github.lnyocly.ai4j.coding;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.coding.process.StoredProcessSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CodingSessionCheckpointFormatter {

    private CodingSessionCheckpointFormatter() {
    }

    public static CodingSessionCheckpoint parse(String summary) {
        CodingSessionCheckpoint checkpoint = CodingSessionCheckpoint.builder().build();
        if (isBlank(summary)) {
            return checkpoint;
        }
        CodingSessionCheckpoint structured = parseStructured(summary);
        if (structured != null) {
            return structured;
        }

        String[] lines = summary.replace("\r", "").split("\n");
        Section section = null;
        ProgressSection progressSection = null;
        StringBuilder goal = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if ("## Goal".equals(line)) {
                section = Section.GOAL;
                progressSection = null;
                continue;
            }
            if ("## Constraints & Preferences".equals(line)) {
                section = Section.CONSTRAINTS;
                progressSection = null;
                continue;
            }
            if ("## Progress".equals(line)) {
                section = Section.PROGRESS;
                progressSection = null;
                continue;
            }
            if ("### Done".equals(line)) {
                section = Section.PROGRESS;
                progressSection = ProgressSection.DONE;
                continue;
            }
            if ("### In Progress".equals(line)) {
                section = Section.PROGRESS;
                progressSection = ProgressSection.IN_PROGRESS;
                continue;
            }
            if ("### Blocked".equals(line)) {
                section = Section.PROGRESS;
                progressSection = ProgressSection.BLOCKED;
                continue;
            }
            if ("## Key Decisions".equals(line)) {
                section = Section.KEY_DECISIONS;
                progressSection = null;
                continue;
            }
            if ("## Next Steps".equals(line)) {
                section = Section.NEXT_STEPS;
                progressSection = null;
                continue;
            }
            if ("## Critical Context".equals(line)) {
                section = Section.CRITICAL_CONTEXT;
                progressSection = null;
                continue;
            }
            if ("## Process Snapshots".equals(line)) {
                section = Section.PROCESS_SNAPSHOTS;
                progressSection = null;
                continue;
            }
            if (section == null) {
                continue;
            }

            switch (section) {
                case GOAL:
                    if (goal.length() > 0) {
                        goal.append('\n');
                    }
                    goal.append(stripListPrefix(line));
                    break;
                case CONSTRAINTS:
                    addIfPresent(checkpoint.getConstraints(), stripListPrefix(line));
                    break;
                case PROGRESS:
                    addProgressLine(checkpoint, progressSection, line);
                    break;
                case KEY_DECISIONS:
                    addIfPresent(checkpoint.getKeyDecisions(), stripListPrefix(line));
                    break;
                case NEXT_STEPS:
                    addIfPresent(checkpoint.getNextSteps(), stripNumberPrefix(stripListPrefix(line)));
                    break;
                case CRITICAL_CONTEXT:
                    addIfPresent(checkpoint.getCriticalContext(), stripListPrefix(line));
                    break;
                case PROCESS_SNAPSHOTS:
                    // 进程快照以结构化 state 为准，文本渲染仅做人类可读展示。
                    break;
                default:
                    break;
            }
        }

        if (goal.length() > 0) {
            checkpoint.setGoal(goal.toString().trim());
        }
        return checkpoint;
    }

    public static String renderStructuredJson(CodingSessionCheckpoint checkpoint) {
        return toStructuredObject(checkpoint).toJSONString();
    }

    public static CodingSessionCheckpoint create(String summary,
                                                 List<StoredProcessSnapshot> processSnapshots,
                                                 int sourceItemCount,
                                                 boolean splitTurn) {
        CodingSessionCheckpoint checkpoint = parse(summary);
        checkpoint.setProcessSnapshots(copyProcesses(processSnapshots));
        checkpoint.setSourceItemCount(sourceItemCount);
        checkpoint.setSplitTurn(splitTurn);
        checkpoint.setGeneratedAtEpochMs(System.currentTimeMillis());
        if (isBlank(checkpoint.getGoal())) {
            checkpoint.setGoal("Continue the current coding session.");
        }
        return checkpoint;
    }

    public static String render(CodingSessionCheckpoint checkpoint) {
        if (checkpoint == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("## Goal\n");
        builder.append(defaultText(checkpoint.getGoal(), "Continue the current coding session.")).append('\n');
        builder.append("## Constraints & Preferences\n");
        appendBulletSection(builder, checkpoint.getConstraints(), "(none)");
        builder.append("## Progress\n");
        builder.append("### Done\n");
        appendProgressSection(builder, checkpoint.getDoneItems(), "x");
        builder.append("### In Progress\n");
        appendProgressSection(builder, checkpoint.getInProgressItems(), " ");
        builder.append("### Blocked\n");
        appendBulletSection(builder, checkpoint.getBlockedItems(), "(none)");
        builder.append("## Key Decisions\n");
        appendBulletSection(builder, checkpoint.getKeyDecisions(), "(none)");
        builder.append("## Next Steps\n");
        appendNumberSection(builder, checkpoint.getNextSteps(), "Resume from the latest workspace state.");
        builder.append("## Critical Context\n");
        appendBulletSection(builder, checkpoint.getCriticalContext(), "(none)");
        if (checkpoint.getProcessSnapshots() != null && !checkpoint.getProcessSnapshots().isEmpty()) {
            builder.append("## Process Snapshots\n");
            for (StoredProcessSnapshot snapshot : checkpoint.getProcessSnapshots()) {
                builder.append("- ").append(renderProcessSnapshot(snapshot)).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private static CodingSessionCheckpoint parseStructured(String summary) {
        String json = extractJsonObject(summary);
        if (isBlank(json)) {
            return null;
        }
        try {
            JSONObject object = JSON.parseObject(json);
            if (object == null || object.isEmpty()) {
                return null;
            }
            return fromStructuredObject(object);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static CodingSessionCheckpoint fromStructuredObject(JSONObject object) {
        if (object == null) {
            return null;
        }
        JSONObject progress = object.getJSONObject("progress");
        CodingSessionCheckpoint checkpoint = CodingSessionCheckpoint.builder()
                .goal(safeTrim(object.getString("goal")))
                .constraints(readStringList(object.getJSONArray("constraints")))
                .doneItems(readStringList(progress == null ? object.getJSONArray("doneItems") : progress.getJSONArray("done")))
                .inProgressItems(readStringList(progress == null ? object.getJSONArray("inProgressItems") : progress.getJSONArray("inProgress")))
                .blockedItems(readStringList(progress == null ? object.getJSONArray("blockedItems") : progress.getJSONArray("blocked")))
                .keyDecisions(readStringList(object.getJSONArray("keyDecisions")))
                .nextSteps(readStringList(object.getJSONArray("nextSteps")))
                .criticalContext(readStringList(object.getJSONArray("criticalContext")))
                .build();
        if (checkpoint.getGoal() == null
                && checkpoint.getConstraints().isEmpty()
                && checkpoint.getDoneItems().isEmpty()
                && checkpoint.getInProgressItems().isEmpty()
                && checkpoint.getBlockedItems().isEmpty()
                && checkpoint.getKeyDecisions().isEmpty()
                && checkpoint.getNextSteps().isEmpty()
                && checkpoint.getCriticalContext().isEmpty()) {
            return null;
        }
        return checkpoint;
    }

    private static JSONObject toStructuredObject(CodingSessionCheckpoint checkpoint) {
        JSONObject object = new JSONObject();
        CodingSessionCheckpoint effective = checkpoint == null ? CodingSessionCheckpoint.builder().build() : checkpoint;
        object.put("goal", defaultText(effective.getGoal(), "Continue the current coding session."));
        object.put("constraints", new JSONArray(normalize(effective.getConstraints())));
        JSONObject progress = new JSONObject();
        progress.put("done", new JSONArray(normalize(effective.getDoneItems())));
        progress.put("inProgress", new JSONArray(normalize(effective.getInProgressItems())));
        progress.put("blocked", new JSONArray(normalize(effective.getBlockedItems())));
        object.put("progress", progress);
        object.put("keyDecisions", new JSONArray(normalize(effective.getKeyDecisions())));
        object.put("nextSteps", new JSONArray(normalize(effective.getNextSteps())));
        object.put("criticalContext", new JSONArray(normalize(effective.getCriticalContext())));
        return object;
    }

    private static void addProgressLine(CodingSessionCheckpoint checkpoint, ProgressSection progressSection, String line) {
        String normalized = stripCheckboxPrefix(stripListPrefix(line));
        if (isBlank(normalized)) {
            return;
        }
        if (progressSection == ProgressSection.DONE) {
            checkpoint.getDoneItems().add(normalized);
            return;
        }
        if (progressSection == ProgressSection.IN_PROGRESS) {
            checkpoint.getInProgressItems().add(normalized);
            return;
        }
        if (progressSection == ProgressSection.BLOCKED) {
            checkpoint.getBlockedItems().add(normalized);
        }
    }

    private static void appendBulletSection(StringBuilder builder, List<String> values, String emptyValue) {
        List<String> normalized = normalize(values);
        if (normalized.isEmpty()) {
            builder.append("- ").append(emptyValue).append('\n');
            return;
        }
        for (String value : normalized) {
            builder.append("- ").append(value).append('\n');
        }
    }

    private static void appendProgressSection(StringBuilder builder, List<String> values, String marker) {
        List<String> normalized = normalize(values);
        if (normalized.isEmpty()) {
            builder.append("- [").append(marker).append("] ").append("(none)").append('\n');
            return;
        }
        for (String value : normalized) {
            builder.append("- [").append(marker).append("] ").append(value).append('\n');
        }
    }

    private static void appendNumberSection(StringBuilder builder, List<String> values, String emptyValue) {
        List<String> normalized = normalize(values);
        if (normalized.isEmpty()) {
            builder.append("1. ").append(emptyValue).append('\n');
            return;
        }
        for (int i = 0; i < normalized.size(); i++) {
            builder.append(i + 1).append(". ").append(normalized.get(i)).append('\n');
        }
    }

    private static String renderProcessSnapshot(StoredProcessSnapshot snapshot) {
        if (snapshot == null) {
            return "(unknown)";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(defaultText(snapshot.getProcessId(), "(unknown)")).append(" | ");
        builder.append(snapshot.isControlAvailable() ? "live" : "metadata-only").append(" | ");
        builder.append("status=").append(snapshot.getStatus()).append(" | ");
        builder.append("cwd=").append(defaultText(snapshot.getWorkingDirectory(), "(unknown)")).append(" | ");
        builder.append("command=").append(defaultText(snapshot.getCommand(), "(none)"));
        if (snapshot.getLastLogOffset() > 0) {
            builder.append(" | lastLogOffset=").append(snapshot.getLastLogOffset());
        }
        if (!isBlank(snapshot.getLastLogPreview())) {
            builder.append(" | preview=").append(clip(snapshot.getLastLogPreview(), 160));
        }
        return builder.toString();
    }

    private static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            if (!isBlank(value) && !"(none)".equalsIgnoreCase(value.trim())) {
                result.add(value.trim());
            }
        }
        return result;
    }

    private static void addIfPresent(List<String> target, String value) {
        if (target == null || isBlank(value) || "(none)".equalsIgnoreCase(value.trim())) {
            return;
        }
        target.add(value.trim());
    }

    private static List<StoredProcessSnapshot> copyProcesses(List<StoredProcessSnapshot> processSnapshots) {
        if (processSnapshots == null || processSnapshots.isEmpty()) {
            return new ArrayList<StoredProcessSnapshot>();
        }
        List<StoredProcessSnapshot> copies = new ArrayList<StoredProcessSnapshot>();
        for (StoredProcessSnapshot snapshot : processSnapshots) {
            if (snapshot != null) {
                copies.add(snapshot.toBuilder().build());
            }
        }
        return copies;
    }

    private static String stripListPrefix(String value) {
        if (isBlank(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("- ")) {
            return trimmed.substring(2).trim();
        }
        return trimmed;
    }

    private static String stripCheckboxPrefix(String value) {
        if (isBlank(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("[x] ") || trimmed.startsWith("[X] ") || trimmed.startsWith("[ ] ")) {
            return trimmed.substring(4).trim();
        }
        return trimmed;
    }

    private static String stripNumberPrefix(String value) {
        if (isBlank(value)) {
            return "";
        }
        int index = value.indexOf('.');
        if (index > 0) {
            boolean digitsOnly = true;
            for (int i = 0; i < index; i++) {
                if (!Character.isDigit(value.charAt(i))) {
                    digitsOnly = false;
                    break;
                }
            }
            if (digitsOnly && index + 1 < value.length()) {
                return value.substring(index + 1).trim();
            }
        }
        return value.trim();
    }

    private static List<String> readStringList(JSONArray values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<String>();
        }
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < values.size(); i++) {
            String value = safeTrim(values.getString(i));
            if (!isBlank(value) && !"(none)".equalsIgnoreCase(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private static String extractJsonObject(String summary) {
        if (isBlank(summary)) {
            return null;
        }
        String trimmed = stripCodeFence(summary.trim());
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return trimmed.substring(start, end + 1);
    }

    private static String stripCodeFence(String text) {
        if (isBlank(text) || !text.startsWith("```")) {
            return text;
        }
        int firstNewline = text.indexOf('\n');
        if (firstNewline < 0) {
            return text;
        }
        String body = text.substring(firstNewline + 1);
        int lastFence = body.lastIndexOf("```");
        if (lastFence >= 0) {
            body = body.substring(0, lastFence);
        }
        return body.trim();
    }

    private static String safeTrim(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private static String clip(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private enum Section {
        GOAL,
        CONSTRAINTS,
        PROGRESS,
        KEY_DECISIONS,
        NEXT_STEPS,
        CRITICAL_CONTEXT,
        PROCESS_SNAPSHOTS
    }

    private enum ProgressSection {
        DONE,
        IN_PROGRESS,
        BLOCKED
    }
}
