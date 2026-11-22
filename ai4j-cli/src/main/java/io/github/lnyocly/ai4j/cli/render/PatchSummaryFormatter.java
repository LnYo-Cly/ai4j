package io.github.lnyocly.ai4j.cli.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.cli.CliJson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PatchSummaryFormatter {

    private static final String ADD_FILE = "*** Add File: ";
    private static final String ADD_FILE_ALIAS = "*** Add: ";
    private static final String UPDATE_FILE = "*** Update File: ";
    private static final String UPDATE_FILE_ALIAS = "*** Update: ";
    private static final String DELETE_FILE = "*** Delete File: ";
    private static final String DELETE_FILE_ALIAS = "*** Delete: ";

    private PatchSummaryFormatter() {
    }

    public static List<String> summarizePatchRequest(String patch, int maxItems) {
        if (isBlank(patch) || maxItems <= 0) {
            return Collections.emptyList();
        }
        String[] rawLines = patch.replace("\r", "").split("\n");
        List<String> lines = new ArrayList<String>();
        for (String rawLine : rawLines) {
            String line = rawLine == null ? "" : rawLine.trim();
            String summary = summarizePatchDirective(line);
            if (!isBlank(summary)) {
                lines.add(summary);
            }
            if (lines.size() >= maxItems) {
                break;
            }
        }
        return lines;
    }

    public static List<String> summarizePatchResult(ObjectNode output, int maxItems) {
        if (output == null || maxItems <= 0) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<String>();
        ArrayNode fileChanges = CliJson.getArray(output, "fileChanges");
        if (fileChanges != null) {
            for (int i = 0; i < fileChanges.size() && lines.size() < maxItems; i++) {
                String summary = formatPatchFileChange(CliJson.getObject(fileChanges, i));
                if (!isBlank(summary)) {
                    lines.add(summary);
                }
            }
            if (!lines.isEmpty()) {
                return lines;
            }
        }
        ArrayNode changedFiles = CliJson.getArray(output, "changedFiles");
        if (changedFiles != null) {
            for (int i = 0; i < changedFiles.size() && lines.size() < maxItems; i++) {
                JsonNode changedFile = changedFiles.get(i);
                if (changedFile != null && !changedFile.isNull()) {
                    lines.add("Edited " + changedFile.asText());
                }
            }
        }
        return lines;
    }

    public static String formatPatchFileChange(ObjectNode change) {
        if (change == null) {
            return null;
        }
        String path = safeTrimToNull(CliJson.getString(change, "path"));
        if (isBlank(path)) {
            return null;
        }
        String operation = firstNonBlank(CliJson.getString(change, "operation"), "update").toLowerCase(Locale.ROOT);
        int linesAdded = CliJson.getIntValue(change, "linesAdded");
        int linesRemoved = CliJson.getIntValue(change, "linesRemoved");
        String verb;
        if ("add".equals(operation)) {
            verb = "Created";
        } else if ("delete".equals(operation)) {
            verb = "Deleted";
        } else {
            verb = "Edited";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(verb).append(" ").append(path);
        if (linesAdded > 0 || linesRemoved > 0) {
            builder.append(" (+").append(linesAdded).append(" -").append(linesRemoved).append(")");
        }
        return builder.toString();
    }

    private static String summarizePatchDirective(String line) {
        if (isBlank(line)) {
            return null;
        }
        if (line.startsWith(ADD_FILE)) {
            return "Add " + line.substring(ADD_FILE.length()).trim();
        }
        if (line.startsWith(ADD_FILE_ALIAS)) {
            return "Add " + line.substring(ADD_FILE_ALIAS.length()).trim();
        }
        if (line.startsWith(UPDATE_FILE)) {
            return "Update " + line.substring(UPDATE_FILE.length()).trim();
        }
        if (line.startsWith(UPDATE_FILE_ALIAS)) {
            return "Update " + line.substring(UPDATE_FILE_ALIAS.length()).trim();
        }
        if (line.startsWith(DELETE_FILE)) {
            return "Delete " + line.substring(DELETE_FILE.length()).trim();
        }
        if (line.startsWith(DELETE_FILE_ALIAS)) {
            return "Delete " + line.substring(DELETE_FILE_ALIAS.length()).trim();
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String safeTrimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

