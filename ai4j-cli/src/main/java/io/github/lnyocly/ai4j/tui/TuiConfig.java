package io.github.lnyocly.ai4j.tui;

public class TuiConfig {

    private String theme = "default";
    private boolean denseMode;
    private boolean showTimestamps = true;
    private boolean showFooter = true;
    private int maxEvents = 10;
    private boolean useAlternateScreen;

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public boolean isDenseMode() {
        return denseMode;
    }

    public void setDenseMode(boolean denseMode) {
        this.denseMode = denseMode;
    }

    public boolean isShowTimestamps() {
        return showTimestamps;
    }

    public void setShowTimestamps(boolean showTimestamps) {
        this.showTimestamps = showTimestamps;
    }

    public boolean isShowFooter() {
        return showFooter;
    }

    public void setShowFooter(boolean showFooter) {
        this.showFooter = showFooter;
    }

    public int getMaxEvents() {
        return maxEvents;
    }

    public void setMaxEvents(int maxEvents) {
        this.maxEvents = maxEvents;
    }

    public boolean isUseAlternateScreen() {
        return useAlternateScreen;
    }

    public void setUseAlternateScreen(boolean useAlternateScreen) {
        this.useAlternateScreen = useAlternateScreen;
    }
}

