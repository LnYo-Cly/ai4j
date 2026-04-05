package io.github.lnyocly.ai4j.tui;

public class TuiPaletteItem {

    private final String id;
    private final String group;
    private final String label;
    private final String detail;
    private final String command;

    public TuiPaletteItem(String id, String group, String label, String detail, String command) {
        this.id = id;
        this.group = group;
        this.label = label;
        this.detail = detail;
        this.command = command;
    }

    public String getId() {
        return id;
    }

    public String getGroup() {
        return group;
    }

    public String getLabel() {
        return label;
    }

    public String getDetail() {
        return detail;
    }

    public String getCommand() {
        return command;
    }
}
