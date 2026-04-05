package io.github.lnyocly.ai4j.tui;

public class TuiKeyStroke {

    private final TuiKeyType type;
    private final String text;

    private TuiKeyStroke(TuiKeyType type, String text) {
        this.type = type == null ? TuiKeyType.UNKNOWN : type;
        this.text = text;
    }

    public static TuiKeyStroke of(TuiKeyType type) {
        return new TuiKeyStroke(type, null);
    }

    public static TuiKeyStroke character(String text) {
        return new TuiKeyStroke(TuiKeyType.CHARACTER, text);
    }

    public TuiKeyType getType() {
        return type;
    }

    public String getText() {
        return text;
    }
}
