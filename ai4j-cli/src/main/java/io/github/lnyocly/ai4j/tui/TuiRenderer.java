package io.github.lnyocly.ai4j.tui;

public interface TuiRenderer {

    int getMaxEvents();

    String getThemeName();

    void updateTheme(TuiConfig config, TuiTheme theme);

    String render(TuiScreenModel screenModel);
}
