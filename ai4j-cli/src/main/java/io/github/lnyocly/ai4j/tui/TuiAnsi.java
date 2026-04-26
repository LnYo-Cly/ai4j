package io.github.lnyocly.ai4j.tui;

final class TuiAnsi {

    private static final String ESC = "\u001b[";
    private static final String RESET = ESC + "0m";

    private TuiAnsi() {
    }

    static String bold(String value, boolean ansi) {
        return style(value, null, null, ansi, true);
    }

    static String fg(String value, String hexColor, boolean ansi) {
        return style(value, hexColor, null, ansi, false);
    }

    static String bg(String value, String hexColor, boolean ansi) {
        return style(value, null, hexColor, ansi, false);
    }

    static String badge(String value, String foreground, String background, boolean ansi) {
        if (!ansi) {
            return "[" + value + "]";
        }
        return style(" " + value + " ", foreground, background, true, true);
    }

    static String colorize(String value, String hexColor, boolean ansi, boolean bold) {
        return style(value, hexColor, null, ansi, bold);
    }

    static String style(String value, String foreground, String background, boolean ansi, boolean bold) {
        if (!ansi || isEmpty(value)) {
            return value;
        }
        StringBuilder codes = new StringBuilder();
        if (bold) {
            codes.append('1');
        }
        if (!isBlank(foreground)) {
            appendColorCode(codes, "38;2;", parseHex(foreground));
        }
        if (!isBlank(background)) {
            appendColorCode(codes, "48;2;", parseHex(background));
        }
        if (codes.length() == 0) {
            return value;
        }
        return ESC + codes + "m" + value + RESET;
    }

    private static void appendColorCode(StringBuilder codes, String prefix, int[] rgb) {
        if (codes == null || rgb == null || rgb.length < 3) {
            return;
        }
        if (codes.length() > 0) {
            codes.append(';');
        }
        codes.append(prefix)
                .append(rgb[0]).append(';')
                .append(rgb[1]).append(';')
                .append(rgb[2]);
    }

    private static int[] parseHex(String hexColor) {
        String normalized = hexColor == null ? "" : hexColor.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() != 6) {
            return new int[]{255, 255, 255};
        }
        return new int[]{
                Integer.parseInt(normalized.substring(0, 2), 16),
                Integer.parseInt(normalized.substring(2, 4), 16),
                Integer.parseInt(normalized.substring(4, 6), 16)
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}

