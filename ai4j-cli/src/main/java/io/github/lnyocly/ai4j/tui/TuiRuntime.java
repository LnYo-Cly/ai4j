package io.github.lnyocly.ai4j.tui;

import java.io.IOException;

public interface TuiRuntime {

    boolean supportsRawInput();

    void enter();

    void exit();

    TuiKeyStroke readKeyStroke(long timeoutMs) throws IOException;

    void render(TuiScreenModel screenModel);
}
