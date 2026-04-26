package io.github.lnyocly.ai4j.tui;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class TuiInteractionStateTest {

    @Test
    public void shouldConsumeInputSilentlyWithoutTriggeringRender() {
        TuiInteractionState state = new TuiInteractionState();
        AtomicInteger renders = new AtomicInteger();
        state.setRenderCallback(new Runnable() {
            @Override
            public void run() {
                renders.incrementAndGet();
            }
        });

        state.appendInput("hello");
        Assert.assertEquals(1, renders.get());

        String value = state.consumeInputBufferSilently();
        Assert.assertEquals("hello", value);
        Assert.assertEquals(1, renders.get());
        Assert.assertEquals("", state.getInputBuffer());
    }

    @Test
    public void shouldSkipRenderWhenTranscriptScrollAlreadyAtZero() {
        TuiInteractionState state = new TuiInteractionState();
        AtomicInteger renders = new AtomicInteger();
        state.setRenderCallback(new Runnable() {
            @Override
            public void run() {
                renders.incrementAndGet();
            }
        });

        state.resetTranscriptScroll();
        Assert.assertEquals(0, renders.get());

        state.moveTranscriptScroll(1);
        Assert.assertEquals(1, renders.get());

        state.resetTranscriptScroll();
        Assert.assertEquals(2, renders.get());
    }

    @Test
    public void shouldNormalizeSlashPaletteQueryWithoutLeadingSlash() {
        TuiInteractionState state = new TuiInteractionState();
        state.openSlashPalette(Arrays.asList(
                new TuiPaletteItem("status", "command", "/status", "Show current session status", "/status"),
                new TuiPaletteItem("session", "command", "/session", "Show current session metadata", "/session")
        ), "/s");

        Assert.assertEquals("s", state.getPaletteQuery());
        Assert.assertEquals(2, state.getPaletteItems().size());
    }

    @Test
    public void shouldAppendSlashInputAndOpenPaletteInSingleRender() {
        TuiInteractionState state = new TuiInteractionState();
        AtomicInteger renders = new AtomicInteger();
        state.setRenderCallback(new Runnable() {
            @Override
            public void run() {
                renders.incrementAndGet();
            }
        });

        state.appendInputAndSyncSlashPalette("/", Arrays.asList(
                new TuiPaletteItem("help", "command", "/help", "Show help", "/help"),
                new TuiPaletteItem("status", "command", "/status", "Show current session status", "/status")
        ));

        Assert.assertEquals(1, renders.get());
        Assert.assertEquals("/", state.getInputBuffer());
        Assert.assertTrue(state.isPaletteOpen());
        Assert.assertEquals(TuiInteractionState.PaletteMode.SLASH, state.getPaletteMode());
        Assert.assertEquals("", state.getPaletteQuery());
    }

    @Test
    public void shouldReplaceSlashSelectionAndClosePaletteInSingleRender() {
        TuiInteractionState state = new TuiInteractionState();
        AtomicInteger renders = new AtomicInteger();
        state.setRenderCallback(new Runnable() {
            @Override
            public void run() {
                renders.incrementAndGet();
            }
        });
        state.openSlashPalette(Arrays.asList(
                new TuiPaletteItem("status", "command", "/status", "Show current session status", "/status")
        ), "/s");
        renders.set(0);

        state.replaceInputBufferAndClosePalette("/status");

        Assert.assertEquals(1, renders.get());
        Assert.assertEquals("/status", state.getInputBuffer());
        Assert.assertFalse(state.isPaletteOpen());
    }
}
