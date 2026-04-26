package io.github.lnyocly.ai4j.tui;

import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class AppendOnlyTuiRuntimeTest {

    @Test
    public void shouldNotDuplicateLiveReasoningWhenAssistantEventArrives() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        AppendOnlyTuiRuntime runtime = new AppendOnlyTuiRuntime(terminal);

        runtime.render(screenModel(
                TuiAssistantViewModel.builder()
                        .phase(TuiAssistantPhase.THINKING)
                        .reasoningText("Inspecting the workspace")
                        .build()
        ));

        runtime.render(TuiScreenModel.builder()
                .cachedEvents(Arrays.asList(SessionEvent.builder()
                        .eventId("evt-1")
                        .type(SessionEventType.ASSISTANT_MESSAGE)
                        .timestamp(1L)
                        .payload(payload("kind", "reasoning", "output", "Inspecting the workspace"))
                        .build()))
                .assistantViewModel(TuiAssistantViewModel.builder()
                        .phase(TuiAssistantPhase.COMPLETE)
                        .build())
                .build());

        String output = stripAnsi(terminal.printed.toString());
        Assert.assertEquals(1, countOccurrences(output, "Inspecting the workspace"));
        Assert.assertTrue(output.contains("Thinking: Inspecting the workspace"));
    }

    @Test
    public void shouldAppendMissingAssistantSuffixWithoutPrintingANewBlock() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        AppendOnlyTuiRuntime runtime = new AppendOnlyTuiRuntime(terminal);

        runtime.render(screenModel(
                TuiAssistantViewModel.builder()
                        .phase(TuiAssistantPhase.GENERATING)
                        .text("Hello")
                        .build()
        ));

        runtime.render(TuiScreenModel.builder()
                .cachedEvents(Arrays.asList(SessionEvent.builder()
                        .eventId("evt-2")
                        .type(SessionEventType.ASSISTANT_MESSAGE)
                        .timestamp(2L)
                        .payload(payload("kind", "assistant", "output", "Hello world"))
                        .build()))
                .assistantViewModel(TuiAssistantViewModel.builder()
                        .phase(TuiAssistantPhase.COMPLETE)
                        .build())
                .build());

        String output = stripAnsi(terminal.printed.toString());
        Assert.assertTrue(output.contains("Hello world"));
        Assert.assertFalse(output.contains("Hello\n\n• world"));
    }

    @Test
    public void shouldNotRepeatLocalCommandOutputInFooterPreview() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        AppendOnlyTuiRuntime runtime = new AppendOnlyTuiRuntime(terminal);
        runtime.enter();

        runtime.render(TuiScreenModel.builder()
                .assistantOutput("No saved sessions found in ./.ai4j/memory-sessions")
                .assistantViewModel(TuiAssistantViewModel.builder()
                        .phase(TuiAssistantPhase.IDLE)
                        .build())
                .build());

        String output = stripAnsi(terminal.printed.toString());
        Assert.assertEquals(1, countOccurrences(output, "No saved sessions found in ./.ai4j/memory-sessions"));
    }

    @Test
    public void shouldKeepReasoningTextAndToolResultInTranscriptOrder() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        AppendOnlyTuiRuntime runtime = new AppendOnlyTuiRuntime(terminal);

        runtime.render(screenModel(
                TuiAssistantViewModel.builder()
                        .phase(TuiAssistantPhase.THINKING)
                        .reasoningText("Inspecting project files")
                        .build()
        ));

        runtime.render(TuiScreenModel.builder()
                .cachedEvents(Arrays.asList(
                        SessionEvent.builder()
                                .eventId("evt-r")
                                .type(SessionEventType.ASSISTANT_MESSAGE)
                                .timestamp(1L)
                                .payload(payload("kind", "reasoning", "output", "Inspecting project files"))
                                .build(),
                        SessionEvent.builder()
                                .eventId("evt-a")
                                .type(SessionEventType.ASSISTANT_MESSAGE)
                                .timestamp(2L)
                                .payload(payload("kind", "assistant", "output", "I will run the tests first."))
                                .build(),
                        SessionEvent.builder()
                                .eventId("evt-t")
                                .type(SessionEventType.TOOL_RESULT)
                                .timestamp(3L)
                                .payload(payload(
                                        "tool", "bash",
                                        "title", "mvn test",
                                        "detail", "exit=0",
                                        "previewLines", Arrays.asList("BUILD SUCCESS")))
                                .build()))
                .assistantViewModel(TuiAssistantViewModel.builder()
                        .phase(TuiAssistantPhase.COMPLETE)
                        .build())
                .build());

        String output = stripAnsi(terminal.printed.toString());
        int reasoningIndex = output.indexOf("Thinking: Inspecting project files");
        int textIndex = output.indexOf("I will run the tests first.");
        int toolIndex = output.indexOf("Ran mvn test");
        Assert.assertTrue(reasoningIndex >= 0);
        Assert.assertTrue(textIndex > reasoningIndex);
        Assert.assertTrue(toolIndex > textIndex);
    }

    @Test
    public void shouldNotRedrawFooterWhenOnlyTheSameAppendOnlyStatusIsRenderedAgain() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        AppendOnlyTuiRuntime runtime = new AppendOnlyTuiRuntime(terminal);
        runtime.enter();

        TuiScreenModel model = screenModel(TuiAssistantViewModel.builder()
                .phase(TuiAssistantPhase.THINKING)
                .phaseDetail("Streaming reasoning...")
                .updatedAtEpochMs(1000L)
                .build());

        runtime.render(model);
        String firstRender = terminal.printed.toString();

        runtime.render(model);

        Assert.assertEquals(firstRender, terminal.printed.toString());
    }

    @Test
    public void shouldRenderStableErrorFooterWithoutSpinnerPrefix() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        AppendOnlyTuiRuntime runtime = new AppendOnlyTuiRuntime(terminal);
        runtime.enter();

        runtime.render(screenModel(TuiAssistantViewModel.builder()
                .phase(TuiAssistantPhase.ERROR)
                .phaseDetail("Authentication failed")
                .updatedAtEpochMs(2000L)
                .build()));

        String output = stripAnsi(terminal.printed.toString());
        Assert.assertTrue(output.contains("Error"));
        Assert.assertFalse(output.contains("- Error"));
        Assert.assertFalse(output.contains("/ Error"));
        Assert.assertFalse(output.contains("| Error"));
        Assert.assertFalse(output.contains("\\ Error"));
    }

    @Test
    public void shouldRenderStableThinkingFooterWithBulletPrefix() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        AppendOnlyTuiRuntime runtime = new AppendOnlyTuiRuntime(terminal);
        runtime.enter();

        runtime.render(screenModel(TuiAssistantViewModel.builder()
                .phase(TuiAssistantPhase.THINKING)
                .phaseDetail("Thinking about: hello")
                .animationTick(2)
                .updatedAtEpochMs(2000L)
                .build()));

        String output = stripAnsi(terminal.printed.toString());
        Assert.assertTrue(output.contains("• Thinking"));
        Assert.assertTrue(output.contains(": hello"));
        Assert.assertTrue(containsSpinnerFrame(output));
        Assert.assertFalse(output.contains("- Thinking"));
        Assert.assertFalse(output.contains("/ Thinking"));
        Assert.assertFalse(output.contains("| Thinking"));
        Assert.assertFalse(output.contains("\\ Thinking"));
        Assert.assertFalse(output.contains("Thinking: Thinking about: hello"));
    }

    @Test
    public void shouldRenderBulletHeaderHintAndReadyFooter() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        AppendOnlyTuiRuntime runtime = new AppendOnlyTuiRuntime(terminal);
        runtime.enter();

        runtime.render(TuiScreenModel.builder()
                .assistantOutput("Ask AI4J to inspect this repository\nOpen the command palette with /\nReplay recent history with Ctrl+R")
                .assistantViewModel(TuiAssistantViewModel.builder()
                        .phase(TuiAssistantPhase.IDLE)
                        .build())
                .build());

        String output = stripAnsi(terminal.printed.toString());
        Assert.assertTrue(output.contains("• Type / for commands, Enter to send"));
        Assert.assertTrue(output.contains("• Ask AI4J to inspect this repository"));
        Assert.assertTrue(output.contains("• Open the command palette with /"));
        Assert.assertTrue(output.contains("• Replay recent history with Ctrl+R"));
        Assert.assertTrue(output.contains("• Ready"));
    }

    @Test
    public void shouldKeepInitialHintAsBulletLinesEvenWhenSessionEventsAlreadyExist() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        AppendOnlyTuiRuntime runtime = new AppendOnlyTuiRuntime(terminal);
        runtime.enter();

        runtime.render(TuiScreenModel.builder()
                .cachedEvents(Collections.singletonList(SessionEvent.builder()
                        .eventId("evt-session-created")
                        .type(SessionEventType.SESSION_CREATED)
                        .timestamp(1L)
                        .summary("session created")
                        .build()))
                .assistantOutput("Ask AI4J to inspect this repository\nOpen the command palette with /\nReplay recent history with Ctrl+R")
                .assistantViewModel(TuiAssistantViewModel.builder()
                        .phase(TuiAssistantPhase.IDLE)
                        .build())
                .build());

        String output = stripAnsi(terminal.printed.toString());
        Assert.assertTrue(output.contains("• Ask AI4J to inspect this repository"));
        Assert.assertTrue(output.contains("• Open the command palette with /"));
        Assert.assertTrue(output.contains("• Replay recent history with Ctrl+R"));
        Assert.assertFalse(output.contains("  Open the command palette with /"));
        Assert.assertFalse(output.contains("  Replay recent history with Ctrl+R"));
    }

    @Test
    public void shouldRenderPaletteWithBulletItemsAndHeader() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        AppendOnlyTuiRuntime runtime = new AppendOnlyTuiRuntime(terminal);
        runtime.enter();

        TuiInteractionState interaction = new TuiInteractionState();
        interaction.openSlashPalette(Arrays.asList(
                new TuiPaletteItem("status", "command", "/status", "Show current session status", "/status"),
                new TuiPaletteItem("session", "command", "/session", "Show current session metadata", "/session")
        ), "/s");

        runtime.render(TuiScreenModel.builder()
                .interactionState(interaction)
                .assistantViewModel(TuiAssistantViewModel.builder()
                        .phase(TuiAssistantPhase.IDLE)
                        .build())
                .build());

        String output = stripAnsi(terminal.printed.toString());
        Assert.assertTrue(output.contains("• Commands: /s"));
        Assert.assertTrue(output.contains("• /status  Show current session status"));
        Assert.assertTrue(output.contains("• /session  Show current session metadata"));
        Assert.assertFalse(output.contains("> /status"));
    }

    @Test
    public void shouldRenderProcessEventsAsStructuredTranscriptBlocks() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        AppendOnlyTuiRuntime runtime = new AppendOnlyTuiRuntime(terminal);

        runtime.render(TuiScreenModel.builder()
                .cachedEvents(Collections.singletonList(SessionEvent.builder()
                        .eventId("proc-1")
                        .type(SessionEventType.PROCESS_UPDATED)
                        .timestamp(1L)
                        .summary("process updated: proc_demo (RUNNING)")
                        .payload(payload(
                                "processId", "proc_demo",
                                "status", "RUNNING",
                                "command", "python demo.py",
                                "workingDirectory", "workspace"
                        ))
                        .build()))
                .assistantViewModel(TuiAssistantViewModel.builder()
                        .phase(TuiAssistantPhase.IDLE)
                        .build())
                .build());

        String output = stripAnsi(terminal.printed.toString());
        Assert.assertTrue(output.contains("• Process: proc_demo (RUNNING)"));
        Assert.assertTrue(output.contains("└ python demo.py"));
        Assert.assertTrue(output.contains("cwd workspace"));
        Assert.assertFalse(output.contains("• Process: process updated: proc_demo"));
    }

    @Test
    public void shouldReturnToColumnOneBeforeRedrawingFooterAfterLiveOutput() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        AppendOnlyTuiRuntime runtime = new AppendOnlyTuiRuntime(terminal);
        runtime.enter();

        TuiInteractionState interaction = new TuiInteractionState();
        interaction.replaceInputBuffer("status");

        runtime.render(TuiScreenModel.builder()
                .interactionState(interaction)
                .assistantViewModel(TuiAssistantViewModel.builder()
                        .phase(TuiAssistantPhase.IDLE)
                        .build())
                .build());

        runtime.render(TuiScreenModel.builder()
                .interactionState(interaction)
                .assistantViewModel(TuiAssistantViewModel.builder()
                        .phase(TuiAssistantPhase.GENERATING)
                        .text("Hello")
                        .build())
                .build());

        String raw = terminal.printed.toString();
        Assert.assertTrue(raw.contains("Hello\r\u001b[2K"));
    }

    @Test
    public void shouldRenderWorkingSpinnerForPendingToolStatus() {
        RecordingTerminalIO terminal = new RecordingTerminalIO();
        AppendOnlyTuiRuntime runtime = new AppendOnlyTuiRuntime(terminal);
        runtime.enter();

        runtime.render(screenModel(TuiAssistantViewModel.builder()
                .phase(TuiAssistantPhase.WAITING_TOOL_RESULT)
                .animationTick(4)
                .tools(Collections.singletonList(TuiAssistantToolView.builder()
                        .toolName("bash")
                        .title("mvn test")
                        .status("pending")
                        .build()))
                .build()));

        String output = stripAnsi(terminal.printed.toString());
        Assert.assertTrue(output.contains("• Working"));
        Assert.assertTrue(output.contains("Running mvn test"));
        Assert.assertTrue(containsSpinnerFrame(output));
    }

    private static TuiScreenModel screenModel(TuiAssistantViewModel assistantViewModel) {
        return TuiScreenModel.builder()
                .assistantViewModel(assistantViewModel)
                .build();
    }

    private static Map<String, Object> payload(Object... pairs) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        if (pairs == null) {
            return payload;
        }
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            payload.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return payload;
    }

    private static String stripAnsi(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]", "")
                .replace("\r", "");
    }

    private static int countOccurrences(String text, String needle) {
        if (text == null || needle == null || needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while (true) {
            index = text.indexOf(needle, index);
            if (index < 0) {
                return count;
            }
            count++;
            index += needle.length();
        }
    }

    private static boolean containsSpinnerFrame(String text) {
        return text != null && (text.contains("⠋")
                || text.contains("⠙")
                || text.contains("⠹")
                || text.contains("⠸")
                || text.contains("⠼")
                || text.contains("⠴")
                || text.contains("⠦")
                || text.contains("⠧")
                || text.contains("⠇")
                || text.contains("⠏"));
    }

    private static final class RecordingTerminalIO implements TerminalIO {

        private final StringBuilder printed = new StringBuilder();

        @Override
        public String readLine(String prompt) throws IOException {
            return null;
        }

        @Override
        public void print(String message) {
            printed.append(message == null ? "" : message);
        }

        @Override
        public void println(String message) {
            printed.append(message == null ? "" : message).append('\n');
        }

        @Override
        public void errorln(String message) {
        }

        @Override
        public boolean supportsAnsi() {
            return true;
        }
    }
}
