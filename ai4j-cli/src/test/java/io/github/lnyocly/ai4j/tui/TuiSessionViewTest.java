package io.github.lnyocly.ai4j.tui;

import io.github.lnyocly.ai4j.coding.CodingSessionSnapshot;
import io.github.lnyocly.ai4j.coding.process.BashProcessInfo;
import io.github.lnyocly.ai4j.coding.process.BashProcessLogChunk;
import io.github.lnyocly.ai4j.coding.process.BashProcessStatus;
import io.github.lnyocly.ai4j.coding.session.CodingSessionDescriptor;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;
import io.github.lnyocly.ai4j.coding.session.SessionEventType;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TuiSessionViewTest {

    @Test
    public void shouldRenderReplayViewerOverlay() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);
        TuiInteractionState state = new TuiInteractionState();
        view.setCachedReplay(Arrays.asList(
                "you> first",
                "assistant> second",
                "",
                "you> third"
        ));
        state.openReplayViewer();
        state.moveReplayScroll(2);

        String rendered = view.render(null, TuiRenderContext.builder().model("glm-4.5-flash").build(), state);
        Assert.assertTrue(rendered.contains("History"));
        Assert.assertTrue(rendered.contains("• third"));
        Assert.assertTrue(rendered.contains("↑/↓ scroll  Esc close"));
        Assert.assertFalse(rendered.contains("USER_MESSAGE"));
        Assert.assertFalse(rendered.contains("you>"));
        Assert.assertFalse(rendered.contains("assistant>"));
        Assert.assertTrue(rendered.contains("third"));
    }

    @Test
    public void shouldRenderTeamBoardOverlay() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);
        TuiInteractionState state = new TuiInteractionState();
        java.util.List<String> boardLines = Arrays.asList(
                "summary tasks=2 running=1 completed=1 failed=0 blocked=0 members=2",
                "",
                "lane reviewer",
                "  [running/heartbeat 15%] Review this patch (review)",
                "    Heartbeat from reviewer.",
                "    heartbeats: 2",
                "  messages:",
                "    - [task.assigned] system -> reviewer | task=review | Review this patch",
                "",
                "lane planner",
                "  [completed/completed 100%] Build final summary (plan)"
        );
        state.openTeamBoard();
        state.moveTeamBoardScroll(1);

        String rendered = view.render(TuiScreenModel.builder()
                .interactionState(state)
                .cachedTeamBoard(boardLines)
                .build());

        Assert.assertTrue(rendered.contains("Team Board"));
        Assert.assertTrue(rendered.contains("lane reviewer"));
        Assert.assertTrue(rendered.contains("running/heartbeat 15%"));
        Assert.assertTrue(rendered.contains("task.assigned"));
        Assert.assertTrue(rendered.contains("↑/↓ scroll  Esc close"));
    }

    @Test
    public void shouldRenderProcessInspectorOverlay() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);
        TuiInteractionState state = new TuiInteractionState();
        state.openProcessInspector("proc_demo");
        state.appendProcessInput("npm run dev");
        view.setProcessInspector(
                BashProcessInfo.builder()
                        .processId("proc_demo")
                        .command("npm run dev")
                        .workingDirectory(".")
                        .status(BashProcessStatus.RUNNING)
                        .controlAvailable(true)
                        .build(),
                BashProcessLogChunk.builder()
                        .processId("proc_demo")
                        .offset(0L)
                        .nextOffset(20L)
                        .content("[stdout] ready\n")
                        .status(BashProcessStatus.RUNNING)
                        .build()
        );

        String rendered = view.render(null, TuiRenderContext.builder().model("glm-4.5-flash").build(), state);
        Assert.assertTrue(rendered.contains("Process proc_demo"));
        Assert.assertTrue(rendered.contains("status running"));
        Assert.assertTrue(rendered.contains("proc_demo"));
        Assert.assertTrue(rendered.contains("stdin> npm run dev"));
        Assert.assertTrue(rendered.contains("ready"));
    }

    @Test
    public void shouldRenderSlashCommandOverlay() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);
        TuiInteractionState state = new TuiInteractionState();
        state.replaceInputBuffer("/re");
        state.openSlashPalette(Arrays.asList(
                new TuiPaletteItem("replay", "command", "/replay", "Replay recent turns", "/replay 20"),
                new TuiPaletteItem("resume", "command", "/resume", "Resume a saved session", "/resume")
        ), "/re");

        String rendered = view.render(TuiScreenModel.builder()
                .interactionState(state)
                .build());

        int composerIndex = rendered.indexOf("> /re");
        int replayIndex = rendered.lastIndexOf("/replay 20");

        Assert.assertTrue(rendered.contains("> /replay 20"));
        Assert.assertTrue(rendered.contains("/replay 20"));
        Assert.assertTrue(rendered.contains("/resume"));
        Assert.assertTrue(replayIndex > composerIndex);
    }

    @Test
    public void shouldScrollSlashCommandWindowWithSelection() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);
        TuiInteractionState state = new TuiInteractionState();
        state.replaceInputBuffer("/");
        state.openSlashPalette(Arrays.asList(
                new TuiPaletteItem("one", "command", "/one", "One", "/one"),
                new TuiPaletteItem("two", "command", "/two", "Two", "/two"),
                new TuiPaletteItem("three", "command", "/three", "Three", "/three"),
                new TuiPaletteItem("four", "command", "/four", "Four", "/four"),
                new TuiPaletteItem("five", "command", "/five", "Five", "/five"),
                new TuiPaletteItem("six", "command", "/six", "Six", "/six"),
                new TuiPaletteItem("seven", "command", "/seven", "Seven", "/seven"),
                new TuiPaletteItem("eight", "command", "/eight", "Eight", "/eight"),
                new TuiPaletteItem("nine", "command", "/nine", "Nine", "/nine"),
                new TuiPaletteItem("ten", "command", "/ten", "Ten", "/ten")
        ), "/");
        for (int i = 0; i < 8; i++) {
            state.movePaletteSelection(1);
        }

        String rendered = view.render(TuiScreenModel.builder()
                .interactionState(state)
                .build());

        Assert.assertTrue(rendered.contains("> /nine"));
        Assert.assertTrue(rendered.contains("/ten"));
        Assert.assertTrue(rendered.contains("..."));
        Assert.assertFalse(rendered.contains("> /one"));
    }

    @Test
    public void shouldRenderStructuredAssistantTrace() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);

        String rendered = view.render(TuiScreenModel.builder()
                .assistantViewModel(TuiAssistantViewModel.builder()
                        .phase(TuiAssistantPhase.WAITING_TOOL_RESULT)
                        .step(1)
                        .phaseDetail("Running command...")
                        .reasoningText("Thinking through the workspace.")
                        .text("Scanning the workspace.\nPreparing the next action.")
                        .tools(Arrays.asList(
                                TuiAssistantToolView.builder()
                                        .toolName("bash")
                                        .status("done")
                                        .title("$ type sample.txt")
                                        .detail("exit=0 | cwd=workspace")
                                        .previewLines(Arrays.asList("hello-cli", "done"))
                                        .build()))
                        .build())
                .build());

        Assert.assertTrue(rendered.contains("running command..."));
        Assert.assertTrue(rendered.contains("Thinking through the workspace."));
        Assert.assertTrue(rendered.contains("Scanning the workspace."));
        Assert.assertTrue(rendered.contains("• Ran type sample.txt"));
        Assert.assertTrue(rendered.contains("└ hello-cli"));
        Assert.assertFalse(rendered.contains("exit=0 | cwd=workspace"));
    }

    @Test
    public void shouldRenderMinimalHeader() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);
        TuiInteractionState state = new TuiInteractionState();
        state.openReplayViewer();

        String rendered = view.render(TuiScreenModel.builder()
                .descriptor(CodingSessionDescriptor.builder()
                        .sessionId("session-alpha")
                        .rootSessionId("session-alpha")
                        .provider("zhipu")
                        .protocol("chat")
                        .model("glm-4.5-flash")
                        .workspace("workspace")
                        .build())
                .snapshot(CodingSessionSnapshot.builder()
                        .sessionId("session-alpha")
                        .estimatedContextTokens(512)
                        .lastCompactMode("manual")
                        .build())
                .cachedReplay(Arrays.asList(
                        "you> first",
                        "assistant> second"
                ))
                .interactionState(state)
                .build());

        Assert.assertTrue(rendered.contains("AI4J"));
        Assert.assertTrue(rendered.contains("glm-4.5-flash"));
        Assert.assertTrue(rendered.contains("workspace"));
        Assert.assertFalse(rendered.contains("focus="));
        Assert.assertFalse(rendered.contains("overlay="));
    }

    @Test
    public void shouldRenderStartupTipsWhenIdle() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);

        String rendered = view.render(TuiScreenModel.builder()
                .build());

        Assert.assertTrue(rendered.contains("Ask AI4J to inspect this repository"));
        Assert.assertTrue(rendered.contains("Type `/` for commands"));
        Assert.assertTrue(rendered.contains("Ctrl+R"));
        Assert.assertFalse(rendered.contains("SYSTEM"));
    }

    @Test
    public void shouldNotDuplicateAssistantOutputWhenLatestEventMatchesLiveText() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("output", "Line one\nLine two");

        String rendered = view.render(TuiScreenModel.builder()
                .cachedEvents(Collections.singletonList(SessionEvent.builder()
                        .type(SessionEventType.ASSISTANT_MESSAGE)
                        .summary("Line one Line two")
                        .payload(payload)
                        .build()))
                .assistantViewModel(TuiAssistantViewModel.builder()
                        .phase(TuiAssistantPhase.COMPLETE)
                        .text("Line one\nLine two")
                        .build())
                .build());

        Assert.assertFalse(rendered.contains("assistant>"));
        Assert.assertEquals(1, countOccurrences(rendered, "Line one"));
        Assert.assertTrue(rendered.contains("Line two"));
    }

    @Test
    public void shouldRenderAssistantMarkdownCodeBlocks() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);

        String rendered = view.render(TuiScreenModel.builder()
                .assistantViewModel(TuiAssistantViewModel.builder()
                        .phase(TuiAssistantPhase.COMPLETE)
                        .text("## Files\n- src/main/java\n> note\nUse `rg` and **fast path**\n```java\nSystem.out.println(\"hi\");\n```")
                        .build())
                .build());

        Assert.assertFalse(rendered.contains("assistant>"));
        Assert.assertTrue(rendered.contains("## Files"));
        Assert.assertTrue(rendered.contains("- src/main/java"));
        Assert.assertTrue(rendered.contains("> note"));
        Assert.assertTrue(rendered.contains("Use rg and fast path"));
        Assert.assertTrue(rendered.contains("System.out.println(\"hi\");"));
        Assert.assertFalse(rendered.contains("+-- code: java"));
        Assert.assertFalse(rendered.contains("| System.out.println(\"hi\");"));
    }

    @Test
    public void shouldRenderTranscriptInEventOrderWithPersistentToolEntries() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);
        Map<String, Object> toolCall = new HashMap<String, Object>();
        toolCall.put("tool", "bash");
        toolCall.put("callId", "call-bash");
        toolCall.put("arguments", "{\"action\":\"exec\",\"command\":\"type sample.txt\"}");

        Map<String, Object> toolResult = new HashMap<String, Object>();
        toolResult.put("tool", "bash");
        toolResult.put("callId", "call-bash");
        toolResult.put("arguments", "{\"action\":\"exec\",\"command\":\"type sample.txt\"}");
        toolResult.put("output", "{\"exitCode\":0,\"workingDirectory\":\"workspace\",\"stdout\":\"hello-cli\"}");

        String rendered = view.render(TuiScreenModel.builder()
                .cachedEvents(Arrays.asList(
                        SessionEvent.builder()
                                .type(SessionEventType.USER_MESSAGE)
                                .payload(Collections.<String, Object>singletonMap("input", "inspect sample"))
                                .build(),
                        SessionEvent.builder()
                                .type(SessionEventType.ASSISTANT_MESSAGE)
                                .payload(Collections.<String, Object>singletonMap("output", "I will inspect the file first."))
                                .build(),
                        SessionEvent.builder()
                                .type(SessionEventType.TOOL_CALL)
                                .payload(toolCall)
                                .build(),
                        SessionEvent.builder()
                                .type(SessionEventType.TOOL_RESULT)
                                .payload(toolResult)
                                .build(),
                        SessionEvent.builder()
                                .type(SessionEventType.ASSISTANT_MESSAGE)
                                .payload(Collections.<String, Object>singletonMap("output", "The file contains hello-cli."))
                                .build()))
                .build());

        int firstAssistant = rendered.indexOf("I will inspect the file first.");
        int toolResultIndex = rendered.indexOf("• Ran type sample.txt");
        int secondAssistant = rendered.indexOf("The file contains hello-cli.");

        Assert.assertTrue(firstAssistant >= 0);
        Assert.assertTrue(rendered.contains("• inspect sample\n\n• I will inspect the file first."));
        Assert.assertFalse(rendered.contains("you> inspect sample"));
        Assert.assertFalse(rendered.contains("• Running type sample.txt"));
        Assert.assertTrue(toolResultIndex > firstAssistant);
        Assert.assertTrue(secondAssistant > toolResultIndex);
        Assert.assertTrue(rendered.contains("└ hello-cli"));
        Assert.assertFalse(rendered.contains("exit=0"));
    }

    @Test
    public void shouldPreferPersistedToolMetadataWhenArgumentsAreClipped() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);
        Map<String, Object> toolCall = new HashMap<String, Object>();
        toolCall.put("tool", "bash");
        toolCall.put("callId", "call-python");
        toolCall.put("arguments", "{\"action\":\"exec\"");
        toolCall.put("title", "$ python -c \"print(1)\"");
        toolCall.put("detail", "Running command...");
        toolCall.put("previewLines", Collections.singletonList("cwd> workspace"));

        Map<String, Object> toolResult = new HashMap<String, Object>();
        toolResult.put("tool", "bash");
        toolResult.put("callId", "call-python");
        toolResult.put("arguments", "{\"action\":\"exec\"");
        toolResult.put("output", "{\"exitCode\":0");
        toolResult.put("title", "$ python -c \"print(1)\"");
        toolResult.put("detail", "exit=0 | cwd=workspace");
        toolResult.put("previewLines", Collections.singletonList("1"));

        String rendered = view.render(TuiScreenModel.builder()
                .cachedEvents(Arrays.asList(
                        SessionEvent.builder()
                                .type(SessionEventType.TOOL_CALL)
                                .payload(toolCall)
                                .build(),
                        SessionEvent.builder()
                                .type(SessionEventType.TOOL_RESULT)
                                .payload(toolResult)
                                .build()))
                .build());

        Assert.assertFalse(rendered.contains("• Running python -c \"print(1)\""));
        Assert.assertTrue(rendered.contains("• Ran python -c \"print(1)\""));
        Assert.assertFalse(rendered.contains("exit=0 | cwd=workspace"));
        Assert.assertTrue(rendered.contains("└ 1"));
        Assert.assertFalse(rendered.contains("(empty command)"));
    }

    @Test
    public void shouldIndentMultilineToolPreviewOutput() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);
        Map<String, Object> toolResult = new HashMap<String, Object>();
        toolResult.put("tool", "bash");
        toolResult.put("callId", "call-date");
        toolResult.put("title", "$ date /t && time /t");
        toolResult.put("previewLines", Collections.singletonList("2026/03/21 Sat\n21:18"));

        String rendered = view.render(TuiScreenModel.builder()
                .cachedEvents(Collections.singletonList(SessionEvent.builder()
                        .type(SessionEventType.TOOL_RESULT)
                        .payload(toolResult)
                        .build()))
                .build());

        Assert.assertTrue(rendered.contains("• Ran date /t && time /t"));
        Assert.assertTrue(rendered.contains("└ 2026/03/21 Sat"));
        Assert.assertTrue(rendered.contains("\n    21:18"));
    }

    @Test
    public void shouldAllowScrollingFullTranscriptWithoutTruncatingAssistantLines() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);
        TuiInteractionState state = new TuiInteractionState();
        StringBuilder assistantText = new StringBuilder();
        for (int i = 1; i <= 30; i++) {
            if (assistantText.length() > 0) {
                assistantText.append('\n');
            }
            assistantText.append("line ").append(i);
        }

        ArrayList<SessionEvent> events = new ArrayList<SessionEvent>();
        events.add(SessionEvent.builder()
                .type(SessionEventType.ASSISTANT_MESSAGE)
                .payload(Collections.<String, Object>singletonMap("output", assistantText.toString()))
                .build());

        String bottomRendered = view.render(TuiScreenModel.builder()
                .cachedEvents(events)
                .interactionState(state)
                .build());
        Assert.assertTrue(bottomRendered.contains("line 30"));
        Assert.assertFalse(bottomRendered.contains("          ..."));

        state.moveTranscriptScroll(6);
        String scrolledRendered = view.render(TuiScreenModel.builder()
                .cachedEvents(events)
                .interactionState(state)
                .build());

        Assert.assertTrue(scrolledRendered.contains("line 1"));
        Assert.assertTrue(scrolledRendered.contains("line 24"));
        Assert.assertFalse(scrolledRendered.contains("line 30"));
        Assert.assertFalse(scrolledRendered.contains("assistant>"));
    }

    @Test
    public void shouldUseTerminalHeightToSizeTranscriptViewport() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);
        TuiInteractionState state = new TuiInteractionState();
        StringBuilder assistantText = new StringBuilder();
        for (int i = 1; i <= 30; i++) {
            if (assistantText.length() > 0) {
                assistantText.append('\n');
            }
            assistantText.append("line ").append(i);
        }

        ArrayList<SessionEvent> events = new ArrayList<SessionEvent>();
        events.add(SessionEvent.builder()
                .type(SessionEventType.ASSISTANT_MESSAGE)
                .payload(Collections.<String, Object>singletonMap("output", assistantText.toString()))
                .build());

        TuiRenderContext context = TuiRenderContext.builder()
                .model("glm-4.5-flash")
                .terminalRows(14)
                .build();

        String bottomRendered = view.render(TuiScreenModel.builder()
                .cachedEvents(events)
                .interactionState(state)
                .renderContext(context)
                .build());

        Assert.assertTrue(bottomRendered.contains("line 23"));
        Assert.assertTrue(bottomRendered.contains("line 30"));
        Assert.assertFalse(bottomRendered.contains("line 22"));

        state.moveTranscriptScroll(3);
        String scrolledRendered = view.render(TuiScreenModel.builder()
                .cachedEvents(events)
                .interactionState(state)
                .renderContext(context)
                .build());

        Assert.assertTrue(scrolledRendered.contains("line 20"));
        Assert.assertTrue(scrolledRendered.contains("line 27"));
        Assert.assertFalse(scrolledRendered.contains("line 19"));
        Assert.assertFalse(scrolledRendered.contains("line 28"));
    }

    @Test
    public void shouldRenderLiveToolImmediatelyEvenWhenHistoryContainsOlderToolEvents() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);
        Map<String, Object> oldToolCall = new HashMap<String, Object>();
        oldToolCall.put("tool", "bash");
        oldToolCall.put("callId", "old-call");
        oldToolCall.put("arguments", "{\"action\":\"exec\",\"command\":\"pwd\"}");

        String rendered = view.render(TuiScreenModel.builder()
                .cachedEvents(Collections.singletonList(SessionEvent.builder()
                        .type(SessionEventType.TOOL_CALL)
                        .payload(oldToolCall)
                        .build()))
                        .assistantViewModel(TuiAssistantViewModel.builder()
                                .phase(TuiAssistantPhase.WAITING_TOOL_RESULT)
                                .tools(Collections.singletonList(TuiAssistantToolView.builder()
                                        .callId("current-call")
                                        .toolName("bash")
                                        .status("pending")
                                        .title("$ date")
                                        .detail("Running command...")
                                        .build()))
                        .build())
                .build());

        Assert.assertTrue(rendered.contains("• Running date"));
        Assert.assertFalse(rendered.contains("running command `date`"));
    }

    @Test
    public void shouldShowInvalidBashCallWithoutEmptyCommandFallback() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);
        Map<String, Object> toolResult = new HashMap<String, Object>();
        toolResult.put("tool", "bash");
        toolResult.put("callId", "call-bash-invalid");
        toolResult.put("arguments", "{\"action\":\"exec\"}");
        toolResult.put("output", "TOOL_ERROR: {\"error\":\"bash exec requires a non-empty command\"}");

        String rendered = view.render(TuiScreenModel.builder()
                .cachedEvents(Collections.singletonList(SessionEvent.builder()
                        .type(SessionEventType.TOOL_RESULT)
                        .payload(toolResult)
                        .build()))
                .build());

        Assert.assertTrue(rendered.contains("• Command failed bash exec"));
        Assert.assertTrue(rendered.contains("bash exec requires a non-empty command"));
        Assert.assertFalse(rendered.contains("(empty command)"));
    }

    @Test
    public void shouldHighlightAnsiCodeBlocks() {
        TuiTheme theme = new TuiTheme();
        theme.setText("#eeeeee");
        theme.setMuted("#778899");
        theme.setBrand("#3366ff");
        theme.setAccent("#ff9900");
        theme.setSuccess("#33cc99");
        theme.setPanelBorder("#445566");
        theme.setCodeBackground("#111827");
        theme.setCodeBorder("#445566");
        theme.setCodeText("#eeeeee");
        theme.setCodeKeyword("#3366ff");
        theme.setCodeString("#33cc99");
        theme.setCodeComment("#778899");
        theme.setCodeNumber("#ff9900");

        TuiSessionView view = new TuiSessionView(new TuiConfig(), theme, true);

        String rendered = view.render(TuiScreenModel.builder()
                .assistantViewModel(TuiAssistantViewModel.builder()
                        .phase(TuiAssistantPhase.COMPLETE)
                        .text("```java\npublic class Demo { String value = \"hi\"; int count = 42; // note\n}\n```")
                        .build())
                .build());

        Assert.assertFalse(rendered.contains("+-- code: java"));
        Assert.assertTrue(rendered.contains("\u001b[1;38;2;51;102;255;48;2;17;24;39mpublic"));
        Assert.assertTrue(rendered.contains("\u001b[38;2;51;204;153;48;2;17;24;39m\"hi\""));
        Assert.assertTrue(rendered.contains("\u001b[38;2;255;153;0;48;2;17;24;39m42"));
        Assert.assertTrue(rendered.contains("\u001b[38;2;119;136;153;48;2;17;24;39m// note"));
    }

    @Test
    public void shouldRenderCodexStyleToolLabelsWithoutMetadataNoise() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);

        Map<String, Object> statusResult = new HashMap<String, Object>();
        statusResult.put("tool", "bash");
        statusResult.put("callId", "call-status");
        statusResult.put("arguments", "{\"action\":\"status\",\"processId\":\"proc_demo\"}");
        statusResult.put("output", "{\"processId\":\"proc_demo\",\"status\":\"RUNNING\",\"command\":\"python demo.py\",\"workingDirectory\":\"workspace\"}");

        Map<String, Object> logsResult = new HashMap<String, Object>();
        logsResult.put("tool", "bash");
        logsResult.put("callId", "call-logs");
        logsResult.put("arguments", "{\"action\":\"logs\",\"processId\":\"proc_demo\"}");
        logsResult.put("output", "{\"content\":\"line 1\\nline 2\",\"status\":\"RUNNING\"}");

        Map<String, Object> writeResult = new HashMap<String, Object>();
        writeResult.put("tool", "bash");
        writeResult.put("callId", "call-write");
        writeResult.put("arguments", "{\"action\":\"write\",\"processId\":\"proc_demo\",\"input\":\"status\\n\"}");
        writeResult.put("output", "{\"bytesWritten\":7,\"process\":{\"processId\":\"proc_demo\",\"status\":\"RUNNING\"}}");

        Map<String, Object> patchResult = new HashMap<String, Object>();
        patchResult.put("tool", "apply_patch");
        patchResult.put("callId", "call-patch");
        patchResult.put("arguments", "{\"patch\":\"*** Begin Patch\\n*** End Patch\\n\"}");
        patchResult.put("output", "{\"filesChanged\":0,\"operationsApplied\":0,\"changedFiles\":[]}");

        String rendered = view.render(TuiScreenModel.builder()
                .cachedEvents(Arrays.asList(
                        SessionEvent.builder().type(SessionEventType.TOOL_RESULT).payload(statusResult).build(),
                        SessionEvent.builder().type(SessionEventType.TOOL_RESULT).payload(logsResult).build(),
                        SessionEvent.builder().type(SessionEventType.TOOL_RESULT).payload(writeResult).build(),
                        SessionEvent.builder().type(SessionEventType.TOOL_RESULT).payload(patchResult).build()))
                .build());

        Assert.assertTrue(rendered.contains("• Checked proc_demo"));
        Assert.assertTrue(rendered.contains("└ python demo.py"));
        Assert.assertTrue(rendered.contains("• Read logs proc_demo"));
        Assert.assertTrue(rendered.contains("└ line 1"));
        Assert.assertTrue(rendered.contains("• Wrote to proc_demo"));
        Assert.assertTrue(rendered.contains("• Applied patch"));
        Assert.assertFalse(rendered.contains("cwd>"));
        Assert.assertFalse(rendered.contains("process="));
        Assert.assertFalse(rendered.contains("bytes=7"));
        Assert.assertFalse(rendered.contains("files=0 | ops=0"));
        Assert.assertFalse(rendered.contains("(no changed files)"));
    }

    @Test
    public void shouldRenderTeamMessageEventsAsNotes() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("taskId", "review");
        payload.put("content", "Please double-check the auth diff.");

        String rendered = view.render(TuiScreenModel.builder()
                .cachedEvents(Collections.singletonList(SessionEvent.builder()
                        .type(SessionEventType.TEAM_MESSAGE)
                        .summary("Team message reviewer -> lead [peer.ask]")
                        .payload(payload)
                        .build()))
                .build());

        Assert.assertTrue(rendered.contains("Team message reviewer -> lead [peer.ask]"));
        Assert.assertTrue(rendered.contains("task: review"));
        Assert.assertTrue(rendered.contains("Please double-check the auth diff."));
    }

    @Test
    public void shouldRenderTeamTaskProgressMetadata() {
        TuiSessionView view = new TuiSessionView(new TuiConfig(), new TuiTheme(), false);
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("title", "Team task review");
        payload.put("detail", "Heartbeat from reviewer.");
        payload.put("memberName", "Reviewer");
        payload.put("status", "running");
        payload.put("phase", "heartbeat");
        payload.put("percent", Integer.valueOf(15));
        payload.put("heartbeatCount", Integer.valueOf(2));

        String rendered = view.render(TuiScreenModel.builder()
                .cachedEvents(Collections.singletonList(SessionEvent.builder()
                        .type(SessionEventType.TASK_UPDATED)
                        .summary("Team task review [running]")
                        .payload(payload)
                        .build()))
                .build());

        Assert.assertTrue(rendered.contains("Team task review [running]"));
        Assert.assertTrue(rendered.contains("Heartbeat from reviewer."));
        Assert.assertTrue(rendered.contains("member: Reviewer"));
        Assert.assertTrue(rendered.contains("status: running | phase: heartbeat | progress: 15%"));
        Assert.assertTrue(rendered.contains("heartbeats: 2"));
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while (text != null && needle != null && (index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
