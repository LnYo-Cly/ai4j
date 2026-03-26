package io.github.lnyocly.ai4j.cli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class AssistantTranscriptRenderer {

    List<Line> render(String markdown) {
        List<String> rawLines = trimBlankEdges(splitLines(markdown));
        if (rawLines.isEmpty()) {
            return Collections.emptyList();
        }
        List<Line> lines = new ArrayList<Line>();
        boolean insideCodeBlock = false;
        String codeBlockLanguage = null;
        for (String rawLine : rawLines) {
            if (isCodeFenceLine(rawLine)) {
                if (!insideCodeBlock) {
                    insideCodeBlock = true;
                    codeBlockLanguage = codeFenceLanguage(rawLine);
                } else {
                    insideCodeBlock = false;
                    codeBlockLanguage = null;
                }
                continue;
            }
            if (insideCodeBlock) {
                lines.add(Line.code(formatCodeContentLine(rawLine), codeBlockLanguage));
            } else {
                lines.add(Line.text(rawLine == null ? "" : rawLine));
            }
        }
        return trimBlankEdgeLines(lines);
    }

    List<String> plainLines(String markdown) {
        List<Line> lines = render(markdown);
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> plain = new ArrayList<String>(lines.size());
        for (Line line : lines) {
            plain.add(line == null ? "" : line.text());
        }
        return plain;
    }

    String styleBlock(String markdown, CliThemeStyler themeStyler) {
        if (themeStyler == null) {
            return "";
        }
        List<Line> lines = render(markdown);
        if (lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            Line line = lines.get(index);
            if (line == null) {
                continue;
            }
            builder.append(line.code()
                    ? themeStyler.styleTranscriptCodeLine(line.text(), line.language())
                    : themeStyler.styleTranscriptLine(line.text(), new CliThemeStyler.TranscriptStyleState()));
        }
        return builder.toString();
    }

    int rowCount(String markdown, int terminalWidth) {
        List<Line> lines = render(markdown);
        if (lines.isEmpty()) {
            return 0;
        }
        int rows = 0;
        int width = Math.max(1, terminalWidth);
        for (Line line : lines) {
            String text = line == null ? "" : line.text();
            if (text.isEmpty()) {
                rows += 1;
                continue;
            }
            String wrapped = CliDisplayWidth.wrapAnsi(text, width, 0).text();
            rows += Math.max(1, wrapped.split("\n", -1).length);
        }
        return rows;
    }

    private List<String> splitLines(String markdown) {
        if (markdown == null) {
            return Collections.emptyList();
        }
        String normalized = markdown.replace("\r", "");
        String[] rawLines = normalized.split("\n", -1);
        List<String> lines = new ArrayList<String>(rawLines.length);
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
        List<String> lines = new ArrayList<String>(end - start + 1);
        for (int index = start; index <= end; index++) {
            lines.add(rawLines.get(index) == null ? "" : rawLines.get(index));
        }
        return lines;
    }

    private List<Line> trimBlankEdgeLines(List<Line> rawLines) {
        if (rawLines == null || rawLines.isEmpty()) {
            return Collections.emptyList();
        }
        int start = 0;
        int end = rawLines.size() - 1;
        while (start <= end && isBlank(rawLines.get(start) == null ? null : rawLines.get(start).text())) {
            start++;
        }
        while (end >= start && isBlank(rawLines.get(end) == null ? null : rawLines.get(end).text())) {
            end--;
        }
        if (start > end) {
            return Collections.emptyList();
        }
        List<Line> lines = new ArrayList<Line>(end - start + 1);
        for (int index = start; index <= end; index++) {
            lines.add(rawLines.get(index));
        }
        return lines;
    }

    private boolean isCodeFenceLine(String rawLine) {
        return rawLine != null && rawLine.trim().startsWith("```");
    }

    private String codeFenceLanguage(String rawLine) {
        if (!isCodeFenceLine(rawLine)) {
            return null;
        }
        String value = rawLine.trim().substring(3).trim();
        return isBlank(value) ? null : value;
    }

    private String formatCodeContentLine(String rawLine) {
        if (rawLine == null || rawLine.isEmpty()) {
            return CodexStyleBlockFormatter.CODE_BLOCK_EMPTY_LINE;
        }
        return CodexStyleBlockFormatter.CODE_BLOCK_LINE_PREFIX + rawLine;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static final class Line {

        private final String text;
        private final boolean code;
        private final String language;

        private Line(String text, boolean code, String language) {
            this.text = text == null ? "" : text;
            this.code = code;
            this.language = language;
        }

        static Line text(String text) {
            return new Line(text, false, null);
        }

        static Line code(String text, String language) {
            return new Line(text, true, language);
        }

        String text() {
            return text;
        }

        boolean code() {
            return code;
        }

        String language() {
            return language;
        }
    }
}
