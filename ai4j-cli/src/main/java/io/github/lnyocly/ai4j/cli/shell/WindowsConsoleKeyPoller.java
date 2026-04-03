package io.github.lnyocly.ai4j.cli.shell;

import com.sun.jna.Library;
import com.sun.jna.Native;

public final class WindowsConsoleKeyPoller {

    private static final int VK_ESCAPE = 0x1B;
    private static final User32 USER32 = loadUser32();

    private boolean escapeDown;

    boolean isSupported() {
        return USER32 != null;
    }

    void resetEscapeState() {
        if (!isSupported()) {
            return;
        }
        short state = USER32.GetAsyncKeyState(VK_ESCAPE);
        escapeDown = isDown(state);
    }

    boolean pollEscapePressed() {
        if (!isSupported()) {
            return false;
        }
        short state = USER32.GetAsyncKeyState(VK_ESCAPE);
        boolean down = isDown(state);
        boolean pressed = wasPressedSinceLastPoll(state) || (down && !escapeDown);
        escapeDown = down;
        return pressed;
    }

    private static boolean isDown(short state) {
        return (state & 0x8000) != 0;
    }

    private static boolean wasPressedSinceLastPoll(short state) {
        return (state & 0x0001) != 0;
    }

    private static User32 loadUser32() {
        if (!isWindows()) {
            return null;
        }
        try {
            return Native.load("user32", User32.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase().contains("win");
    }

    interface User32 extends Library {
        short GetAsyncKeyState(int vKey);
    }
}

