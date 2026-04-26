package io.github.lnyocly.ai4j.cli.render;

import org.jline.utils.AttributedString;
import org.jline.utils.WCWidth;

public final class CliDisplayWidth {

    private static final String LINE_HEAD_FORBIDDEN = ",.:;!?)]}>%}，。！？；：、）》」』】〕〉》”’";

    private CliDisplayWidth() {
    }

    public static String clip(String value, int maxWidth) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (maxWidth <= 0 || normalized.isEmpty()) {
            return maxWidth <= 0 ? "" : normalized;
        }
        if (displayWidth(normalized) <= maxWidth) {
            return normalized;
        }
        if (maxWidth <= 3) {
            return sliceByColumns(normalized, maxWidth);
        }
        return sliceByColumns(normalized, maxWidth - 3) + "...";
    }

    public static int displayWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += charWidth(text.charAt(i));
        }
        return width;
    }

    public static String sliceByColumns(String text, int width) {
        if (text == null || text.isEmpty() || width <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int used = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int charWidth = charWidth(ch);
            if (used + charWidth > width) {
                break;
            }
            builder.append(ch);
            used += charWidth;
        }
        return builder.toString();
    }

    public static WrappedAnsi wrapAnsi(String ansiText, int terminalWidth, int startColumn) {
        String safe = ansiText == null ? "" : ansiText.replace("\r", "");
        if (safe.isEmpty() || terminalWidth <= 0) {
            return new WrappedAnsi(safe, startColumn);
        }
        int column = Math.max(0, startColumn);
        StringBuilder builder = new StringBuilder();
        int start = 0;
        while (start <= safe.length()) {
            int newlineIndex = safe.indexOf('\n', start);
            String line = newlineIndex >= 0 ? safe.substring(start, newlineIndex) : safe.substring(start);
            if (!line.isEmpty()) {
                AttributedString attributed = AttributedString.fromAnsi(line);
                int offset = 0;
                int totalColumns = attributed.columnLength();
                while (offset < totalColumns) {
                    if (column >= terminalWidth) {
                        builder.append('\n');
                        column = 0;
                    }
                    int remaining = Math.max(1, terminalWidth - column);
                    int end = Math.min(totalColumns, offset + remaining);
                    AttributedString fragment = attributed.columnSubSequence(offset, end);
                    if (end < totalColumns && startsWithLineHeadForbidden(attributed.columnSubSequence(end, totalColumns).toString())) {
                        int adjustedEnd = retreatBreak(attributed, offset, end, column);
                        if (adjustedEnd <= offset && column > 0) {
                            builder.append('\n');
                            column = 0;
                            continue;
                        }
                        if (adjustedEnd > offset && adjustedEnd < end) {
                            end = adjustedEnd;
                            fragment = attributed.columnSubSequence(offset, end);
                        }
                    }
                    builder.append(fragment.toAnsi());
                    int fragmentWidth = fragment.columnLength();
                    column += fragmentWidth;
                    offset += fragmentWidth;
                    if (offset < totalColumns && column >= terminalWidth) {
                        builder.append('\n');
                        column = 0;
                    }
                }
            }
            if (newlineIndex < 0) {
                break;
            }
            builder.append('\n');
            column = 0;
            start = newlineIndex + 1;
        }
        return new WrappedAnsi(builder.toString(), column);
    }

    private static int charWidth(char ch) {
        int width = WCWidth.wcwidth(ch);
        return width <= 0 ? 1 : width;
    }

    private static int retreatBreak(AttributedString attributed, int start, int end, int currentColumn) {
        int adjustedEnd = end;
        while (adjustedEnd > start) {
            AttributedString fragment = attributed.columnSubSequence(start, adjustedEnd);
            String text = fragment.toString();
            if (text.isEmpty()) {
                break;
            }
            char last = text.charAt(text.length() - 1);
            int lastWidth = charWidth(last);
            if (adjustedEnd - lastWidth < start) {
                break;
            }
            adjustedEnd -= lastWidth;
            if (adjustedEnd <= start) {
                return currentColumn > 0 ? adjustedEnd : end;
            }
            if (!startsWithLineHeadForbidden(attributed.columnSubSequence(adjustedEnd, attributed.columnLength()).toString())) {
                return adjustedEnd;
            }
        }
        return end;
    }

    private static boolean startsWithLineHeadForbidden(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return LINE_HEAD_FORBIDDEN.indexOf(text.charAt(0)) >= 0;
    }

    public static final class WrappedAnsi {
        private final String text;
        private final int endColumn;

        public WrappedAnsi(String text, int endColumn) {
            this.text = text == null ? "" : text;
            this.endColumn = Math.max(0, endColumn);
        }

        public String text() {
            return text;
        }

        public int endColumn() {
            return endColumn;
        }
    }
}

