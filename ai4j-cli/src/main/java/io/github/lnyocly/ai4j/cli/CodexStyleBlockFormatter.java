package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.coding.CodingSessionCompactResult;
import io.github.lnyocly.ai4j.tui.TuiAssistantToolView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class CodexStyleBlockFormatter {

    static final String CODE_BLOCK_HEADER_PREFIX = "  [";
    static final String CODE_BLOCK_HEADER_SUFFIX = "]";
    static final String CODE_BLOCK_LINE_PREFIX = "    ";
    static final String CODE_BLOCK_EMPTY_LINE = "    ";

    private final int maxWidth;
    private final int maxToolPreviewLines;

    CodexStyleBlockFormatter(int maxWidth, int maxToolPreviewLines) {
        this.maxWidth = Math.max(48, maxWidth);
        this.maxToolPreviewLines = Math.max(1, maxToolPreviewLines);
    }

    List<String> formatAssistant(String text) {
        List<String> rawLines = trimBlankEdges(splitLines(text));
        if (rawLines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<String>();
        boolean insideCodeBlock = false;
        for (String rawLine : rawLines) {
            if (isCodeFenceLine(rawLine)) {
                insideCodeBlock = !insideCodeBlock;
                continue;
            }
            if (insideCodeBlock) {
                lines.add(formatCodeContentLine(rawLine));
                continue;
            }
            lines.add(rawLine == null ? "" : rawLine);
        }
        return trimBlankEdges(lines);
    }

    boolean isCodeFenceLine(String rawLine) {
        if (rawLine == null) {
            return false;
        }
        String trimmed = rawLine.trim();
        return trimmed.startsWith("```");
    }

    String formatCodeFenceOpen(String rawLine) {
        return null;
    }

    String formatCodeFenceClose() {
        return null;
    }

    String formatCodeContentLine(String rawLine) {
        if (rawLine == null || rawLine.isEmpty()) {
            return CODE_BLOCK_EMPTY_LINE;
        }
        return CODE_BLOCK_LINE_PREFIX + rawLine;
    }

    String codeFenceLanguage(String rawLine) {
        if (rawLine == null) {
            return null;
        }
        String trimmed = rawLine.trim();
        if (!trimmed.startsWith("```")) {
            return null;
        }
        String value = trimmed.substring(3).trim();
        return isBlank(value) ? null : value;
    }

    List<String> formatTool(TuiAssistantToolView toolView) {
        if (toolView == null) {
            return Collections.emptyList();
        }

        String toolName = firstNonBlank(toolView.getToolName(), "tool");
        String status = firstNonBlank(toolView.getStatus(), "done").toLowerCase(Locale.ROOT);
        List<String> lines = new ArrayList<String>(formatToolPrimaryLines(toolName, toolView.getTitle(), status));

        String detail = safeTrimToNull(toolView.getDetail());
        if ("bash".equals(toolName) && !"timed out".equalsIgnoreCase(firstNonBlank(detail, ""))) {
            if (!"error".equalsIgnoreCase(firstNonBlank(toolView.getStatus(), ""))) {
                detail = null;
            }
        }
        if (!isBlank(detail)) {
            lines.addAll(wrapPrefixedText("  \u2514 ", "    ", detail, maxWidth));
        }

        List<String> previewLines = normalizeToolPreviewLines(toolName, status, toolView.getPreviewLines());
        int max = Math.min(previewLines.size(), maxToolPreviewLines);
        for (int i = 0; i < max; i++) {
            String prefix = i == 0 && isBlank(detail) ? "  \u2514 " : "    ";
            lines.addAll(wrapPrefixedText(prefix, "    ", previewLines.get(i), maxWidth));
        }
        if (previewLines.size() > max) {
            lines.add("    \u2026 +" + (previewLines.size() - max) + " lines");
        }
        return trimBlankEdges(lines);
    }

    String formatRunningStatus(TuiAssistantToolView toolView) {
        if (toolView == null) {
            return "Planning the next step";
        }
        List<String> primaryLines = formatToolPrimaryLines(toolView.getToolName(), toolView.getTitle(), "pending");
        if (primaryLines.isEmpty()) {
            return "Planning the next step";
        }
        return clip(stripLeadingBullet(primaryLines.get(0)), maxWidth);
    }

    List<String> formatCompact(CodingSessionCompactResult result) {
        if (result == null) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<String>();
        lines.add(result.isAutomatic() ? "\u2022 Auto-compacted session context" : "\u2022 Compacted session context");

        StringBuilder metrics = new StringBuilder();
        metrics.append("tokens ")
                .append(result.getEstimatedTokensBefore())
                .append("->")
                .append(result.getEstimatedTokensAfter())
                .append(", items ")
                .append(result.getBeforeItemCount())
                .append("->")
                .append(result.getAfterItemCount());
        if (result.isSplitTurn()) {
            metrics.append(", split turn");
        }
        lines.addAll(wrapPrefixedText("  \u2514 ", "    ", metrics.toString(), maxWidth));

        String summary = safeTrimToNull(result.getSummary());
        if (!isBlank(summary)) {
            lines.addAll(wrapPrefixedText("    ", "    ", summary, maxWidth));
        }
        return lines;
    }

    List<String> formatError(String message) {
        List<String> lines = new ArrayList<String>();
        lines.add("\u2022 Error");
        lines.addAll(wrapPrefixedText("  \u2514 ", "    ", firstNonBlank(safeTrimToNull(message), "Agent run failed."), maxWidth));
        return lines;
    }

    List<String> formatInfoBlock(String title, List<String> rawLines) {
        List<String> lines = new ArrayList<String>();
        lines.add("\u2022 " + firstNonBlank(safeTrimToNull(title), "Info"));
        boolean hasDetail = false;
        if (rawLines != null) {
            for (String rawLine : rawLines) {
                if (isBlank(rawLine)) {
                    if (hasDetail) {
                        lines.add("");
                    }
                    continue;
                }
                String detail = normalizeInfoLine(rawLine);
                if (isBlank(detail)) {
                    continue;
                }
                lines.addAll(wrapPrefixedText(hasDetail ? "    " : "  \u2514 ", "    ", detail, maxWidth));
                hasDetail = true;
            }
        }
        if (!hasDetail) {
            lines.add("  \u2514 (none)");
        }
        return trimBlankEdges(lines);
    }

    List<String> formatOutput(String text) {
        List<String> rawLines = trimBlankEdges(splitLines(text));
        if (rawLines.isEmpty()) {
            return Collections.emptyList();
        }
        ParsedInfoBlock parsed = parseOutputBlock(rawLines);
        if (parsed != null) {
            return formatInfoBlock(parsed.title, parsed.bodyLines);
        }
        return formatInfoBlock("Info", rawLines);
    }

    private List<String> formatToolPrimaryLines(String toolName, String title, String status) {
        List<String> lines = new ArrayList<String>();
        String normalizedTool = firstNonBlank(toolName, "tool");
        String normalizedStatus = firstNonBlank(status, "done").toLowerCase(Locale.ROOT);
        String label = normalizeToolPrimaryLabel(firstNonBlank(title, normalizedTool));
        if ("error".equals(normalizedStatus)) {
            if ("bash".equals(normalizedTool)) {
                return wrapPrefixedText("\u2022 Command failed ", "  \u2502 ", label, maxWidth);
            }
            lines.add("\u2022 Tool failed " + clip(label, Math.max(24, maxWidth - 16)));
            return lines;
        }
        if ("apply_patch".equals(normalizedTool)) {
            lines.add("pending".equals(normalizedStatus) ? "\u2022 Applying patch" : "\u2022 Applied patch");
            return lines;
        }
        return wrapPrefixedText(resolveToolPrimaryPrefix(normalizedTool, title, normalizedStatus), "  \u2502 ", label, maxWidth);
    }

    private ParsedInfoBlock parseOutputBlock(List<String> rawLines) {
        if (rawLines == null || rawLines.isEmpty()) {
            return null;
        }
        String firstLine = safeTrimToNull(rawLines.get(0));
        if (isBlank(firstLine)) {
            return null;
        }
        if (firstLine.endsWith(":")) {
            return new ParsedInfoBlock(normalizeBlockTitle(firstLine.substring(0, firstLine.length() - 1)), rawLines.subList(1, rawLines.size()));
        }
        int colonIndex = firstLine.indexOf(':');
        if (colonIndex <= 0 || colonIndex >= firstLine.length() - 1 || firstLine.contains("://")) {
            return null;
        }
        String rawTitle = firstLine.substring(0, colonIndex).trim();
        if (!isStructuredOutputTitle(rawTitle)) {
            return null;
        }
        List<String> bodyLines = new ArrayList<String>();
        String inlineDetail = safeTrimToNull(firstLine.substring(colonIndex + 1));
        if (!isBlank(inlineDetail)) {
            bodyLines.add(inlineDetail);
        }
        for (int i = 1; i < rawLines.size(); i++) {
            bodyLines.add(rawLines.get(i));
        }
        return new ParsedInfoBlock(normalizeBlockTitle(rawTitle), bodyLines);
    }

    private boolean isStructuredOutputTitle(String rawTitle) {
        if (isBlank(rawTitle)) {
            return false;
        }
        String normalized = rawTitle.trim().toLowerCase(Locale.ROOT);
        return "status".equals(normalized)
                || "session".equals(normalized)
                || "sessions".equals(normalized)
                || "history".equals(normalized)
                || "tree".equals(normalized)
                || "events".equals(normalized)
                || "replay".equals(normalized)
                || "compacts".equals(normalized)
                || "processes".equals(normalized)
                || "process status".equals(normalized)
                || "process logs".equals(normalized)
                || "process write".equals(normalized)
                || "process stopped".equals(normalized)
                || "checkpoint".equals(normalized)
                || "commands".equals(normalized)
                || "themes".equals(normalized)
                || "stream".equals(normalized)
                || "compact".equals(normalized);
    }

    private String normalizeBlockTitle(String rawTitle) {
        if (isBlank(rawTitle)) {
            return "Info";
        }
        String title = rawTitle.trim();
        if (title.length() == 1) {
            return title.toUpperCase(Locale.ROOT);
        }
        return Character.toUpperCase(title.charAt(0)) + title.substring(1);
    }

    private String normalizeInfoLine(String rawLine) {
        if (rawLine == null) {
            return null;
        }
        String value = rawLine.trim();
        if (value.startsWith("- ")) {
            return value.substring(2).trim();
        }
        return value;
    }

    private String resolveToolPrimaryPrefix(String toolName, String title, String status) {
        String normalizedTool = firstNonBlank(toolName, "tool");
        String normalizedTitle = firstNonBlank(title, normalizedTool);
        boolean pending = "pending".equalsIgnoreCase(status);
        if ("read_file".equals(normalizedTool)) {
            return pending ? "\u2022 Reading " : "\u2022 Read ";
        }
        if ("write_file".equals(normalizedTool)) {
            return pending ? "\u2022 Writing " : "\u2022 Wrote ";
        }
        if ("bash".equals(normalizedTool)) {
            if (normalizedTitle.startsWith("bash logs ")) {
                return pending ? "\u2022 Reading logs " : "\u2022 Read logs ";
            }
            if (normalizedTitle.startsWith("bash status ")) {
                return pending ? "\u2022 Checking " : "\u2022 Checked ";
            }
            if (normalizedTitle.startsWith("bash write ")) {
                return pending ? "\u2022 Writing to " : "\u2022 Wrote to ";
            }
            if (normalizedTitle.startsWith("bash stop ")) {
                return pending ? "\u2022 Stopping " : "\u2022 Stopped ";
            }
        }
        return pending ? "\u2022 Running " : "\u2022 Ran ";
    }

    private String normalizeToolPrimaryLabel(String title) {
        String normalizedTitle = firstNonBlank(title, "tool").trim();
        if (normalizedTitle.startsWith("$ ")) {
            return normalizedTitle.substring(2).trim();
        }
        if (normalizedTitle.startsWith("read ")) {
            return normalizedTitle.substring(5).trim();
        }
        if (normalizedTitle.startsWith("write ")) {
            return normalizedTitle.substring(6).trim();
        }
        if (normalizedTitle.startsWith("bash logs ")) {
            return normalizedTitle.substring("bash logs ".length()).trim();
        }
        if (normalizedTitle.startsWith("bash status ")) {
            return normalizedTitle.substring("bash status ".length()).trim();
        }
        if (normalizedTitle.startsWith("bash write ")) {
            return normalizedTitle.substring("bash write ".length()).trim();
        }
        if (normalizedTitle.startsWith("bash stop ")) {
            return normalizedTitle.substring("bash stop ".length()).trim();
        }
        return normalizedTitle;
    }

    private List<String> normalizeToolPreviewLines(String toolName, String status, List<String> previewLines) {
        if (previewLines == null || previewLines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalized = new ArrayList<String>();
        for (String previewLine : previewLines) {
            String candidate = stripPreviewLabel(previewLine);
            if (isBlank(candidate)) {
                continue;
            }
            if ("bash".equals(toolName)) {
                if ("pending".equalsIgnoreCase(status)) {
                    continue;
                }
                if ("(no command output)".equalsIgnoreCase(candidate)) {
                    continue;
                }
            }
            if ("apply_patch".equals(toolName) && "(no changed files)".equalsIgnoreCase(candidate)) {
                continue;
            }
            normalized.add(candidate);
        }
        return normalized;
    }

    private String stripPreviewLabel(String previewLine) {
        if (isBlank(previewLine)) {
            return null;
        }
        String value = previewLine.trim();
        int separator = value.indexOf("> ");
        if (separator > 0) {
            String prefix = value.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            if ("stdout".equals(prefix)
                    || "stderr".equals(prefix)
                    || "log".equals(prefix)
                    || "file".equals(prefix)
                    || "path".equals(prefix)
                    || "cwd".equals(prefix)
                    || "timeout".equals(prefix)
                    || "process".equals(prefix)
                    || "status".equals(prefix)
                    || "command".equals(prefix)
                    || "stdin".equals(prefix)
                    || "meta".equals(prefix)
                    || "out".equals(prefix)) {
                return value.substring(separator + 2).trim();
            }
        }
        return value;
    }

    private List<String> wrapPrefixedText(String firstPrefix, String continuationPrefix, String rawText, int maxWidth) {
        List<String> lines = new ArrayList<String>();
        if (isBlank(rawText)) {
            return lines;
        }
        String first = firstPrefix == null ? "" : firstPrefix;
        String continuation = continuationPrefix == null ? "" : continuationPrefix;
        int firstWidth = Math.max(12, maxWidth - first.length());
        int continuationWidth = Math.max(12, maxWidth - continuation.length());
        boolean firstLine = true;
        String[] paragraphs = rawText.replace("\r", "").split("\n");
        for (String paragraph : paragraphs) {
            String text = safeTrimToNull(paragraph);
            if (isBlank(text)) {
                continue;
            }
            while (!isBlank(text)) {
                int width = firstLine ? firstWidth : continuationWidth;
                int split = findWrapIndex(text, width);
                lines.add((firstLine ? first : continuation) + text.substring(0, split).trim());
                text = text.substring(split).trim();
                firstLine = false;
            }
        }
        return lines;
    }

    private int findWrapIndex(String text, int width) {
        if (isBlank(text) || text.length() <= width) {
            return text == null ? 0 : text.length();
        }
        int whitespace = -1;
        for (int i = Math.min(width, text.length() - 1); i >= 0; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                whitespace = i;
                break;
            }
        }
        return whitespace > 0 ? whitespace : width;
    }

    private List<String> splitLines(String text) {
        if (text == null) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<String>();
        String[] rawLines = text.replace("\r", "").split("\n", -1);
        for (String rawLine : rawLines) {
            lines.add(rawLine == null ? "" : rawLine);
        }
        return lines;
    }

    private List<String> trimBlankEdges(List<String> rawLines) {
        if (rawLines == null || rawLines.isEmpty()) {
            return Collections.emptyList();
        }
        int start = 0;
        int end = rawLines.size() - 1;
        while (start <= end && isBlank(rawLines.get(start))) {
            start++;
        }
        while (end >= start && isBlank(rawLines.get(end))) {
            end--;
        }
        if (start > end) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<String>();
        for (int i = start; i <= end; i++) {
            lines.add(rawLines.get(i) == null ? "" : rawLines.get(i));
        }
        return lines;
    }

    private String stripLeadingBullet(String text) {
        if (isBlank(text)) {
            return "";
        }
        String value = text.trim();
        if (value.startsWith("\u2022 ")) {
            return value.substring(2).trim();
        }
        if (value.startsWith("\u2502 ")) {
            return value.substring(2).trim();
        }
        return value;
    }

    private String clip(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars)) + "...";
    }

    private String safeTrimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class ParsedInfoBlock {
        private final String title;
        private final List<String> bodyLines;

        private ParsedInfoBlock(String title, List<String> bodyLines) {
            this.title = title;
            this.bodyLines = bodyLines == null ? Collections.<String>emptyList() : new ArrayList<String>(bodyLines);
        }
    }
}
