package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.cli.command.CustomCommandRegistry;
import io.github.lnyocly.ai4j.tui.TuiConfigManager;
import org.jline.reader.Buffer;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SlashCommandControllerTest {

    @Test
    public void suggestRootCommandsIncludesBuiltInsAndSpacingRules() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-root");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        List<Candidate> candidates = controller.suggest("/", 1);

        assertContainsValue(candidates, "/help");
        assertContainsValue(candidates, "/cmd ");
        assertContainsValue(candidates, "/theme ");
        assertContainsValue(candidates, "/stream ");
        assertContainsValue(candidates, "/mcp ");
        assertContainsValue(candidates, "/providers");
        assertContainsValue(candidates, "/provider ");
        assertContainsValue(candidates, "/model ");
        assertContainsValue(candidates, "/skills ");
        assertContainsValue(candidates, "/agents ");
        assertContainsValue(candidates, "/process ");
        assertContainsValue(candidates, "/team");
    }

    @Test
    public void suggestCustomCommandNamesFromWorkspaceRegistry() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-cmd");
        Path commandsDir = workspace.resolve(".ai4j").resolve("commands");
        Files.createDirectories(commandsDir);
        Files.write(commandsDir.resolve("review.prompt"),
                "# Review auth flow\nCheck the auth flow.\n".getBytes(StandardCharsets.UTF_8));

        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        List<Candidate> candidates = controller.suggest("/cmd re", "/cmd re".length());

        assertContainsValue(candidates, "review");
    }

    @Test
    public void suggestThemesIncludesBuiltInThemeNames() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-theme");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        List<Candidate> candidates = controller.suggest("/theme a", "/theme a".length());

        assertContainsValue(candidates, "amber");
    }

    @Test
    public void suggestStreamOptionsIncludesOnAndOff() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-stream");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        List<Candidate> candidates = controller.suggest("/stream ", "/stream ".length());

        assertContainsValue(candidates, "on");
        assertContainsValue(candidates, "off");
    }

    @Test
    public void suggestSkillNamesFromSupplier() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-skills");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );
        controller.setSkillCandidateSupplier(() -> Arrays.asList("repo-review", "release-checklist"));

        List<Candidate> candidates = controller.suggest("/skills ", "/skills ".length());

        assertContainsValue(candidates, "repo-review");
        assertContainsValue(candidates, "release-checklist");
    }

    @Test
    public void suggestAgentNamesFromSupplier() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-agents");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );
        controller.setAgentCandidateSupplier(() -> Arrays.asList("reviewer", "planner"));

        List<Candidate> candidates = controller.suggest("/agents ", "/agents ".length());

        assertContainsValue(candidates, "reviewer");
        assertContainsValue(candidates, "planner");
    }

    @Test
    public void suggestExactExecutableArgumentCommandKeepsRootCandidateWithoutTrailingSpace() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-stream-exact");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        List<Candidate> candidates = controller.suggest("/stream", "/stream".length());

        assertContainsValue(candidates, "/stream ");
        assertTrue(!containsValue(candidates, "/stream on"));
        assertTrue(!containsValue(candidates, "/stream off"));
    }

    @Test
    public void suggestExactMcpCommandKeepsRootCandidateWithoutTrailingSpace() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-mcp-exact");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        List<Candidate> candidates = controller.suggest("/mcp", "/mcp".length());

        assertContainsValue(candidates, "/mcp ");
        assertTrue(!containsValue(candidates, "/mcp add "));
        assertTrue(!containsValue(candidates, "/mcp enable "));
    }

    @Test
    public void suggestMcpActionsAfterTrailingSpaceIncludesManagementCommands() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-mcp-actions");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        List<Candidate> candidates = controller.suggest("/mcp ", "/mcp ".length());

        assertContainsValue(candidates, "list ");
        assertContainsValue(candidates, "add ");
        assertContainsValue(candidates, "enable ");
        assertContainsValue(candidates, "pause ");
        assertContainsValue(candidates, "remove ");
    }

    @Test
    public void suggestMcpTransportOptionsAfterTransportFlag() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-mcp-transport");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        List<Candidate> candidates = controller.suggest("/mcp add --transport ", "/mcp add --transport ".length());

        assertContainsValue(candidates, "stdio");
        assertContainsValue(candidates, "sse");
        assertContainsValue(candidates, "http");
    }

    @Test
    public void suggestMcpServerNamesFromSupplier() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-mcp-server");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );
        controller.setMcpServerCandidateSupplier(() -> Arrays.asList("fetch", "time"));

        List<Candidate> candidates = controller.suggest("/mcp enable ", "/mcp enable ".length());

        assertContainsValue(candidates, "fetch");
        assertContainsValue(candidates, "time");
    }

    @Test
    public void suggestExactProviderCommandKeepsRootCandidateWithoutTrailingSpace() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-provider-exact");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        List<Candidate> candidates = controller.suggest("/provider", "/provider".length());

        assertContainsValue(candidates, "/provider ");
        assertTrue(!containsValue(candidates, "/provider use "));
    }

    @Test
    public void suggestProviderActionsAfterTrailingSpaceIncludesAddAndEdit() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-provider-actions");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        List<Candidate> candidates = controller.suggest("/provider ", "/provider ".length());

        assertContainsValue(candidates, "use ");
        assertContainsValue(candidates, "save ");
        assertContainsValue(candidates, "add ");
        assertContainsValue(candidates, "edit ");
    }

    @Test
    public void suggestProviderNamesFromProfileSupplier() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-provider-name");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );
        controller.setProfileCandidateSupplier(() -> Arrays.asList("openai-main", "zhipu-main"));

        List<Candidate> candidates = controller.suggest("/provider use ", "/provider use ".length());

        assertContainsValue(candidates, "openai-main");
        assertContainsValue(candidates, "zhipu-main");
    }

    @Test
    public void suggestProviderNamesWhenUseActionIsExactWithoutTrailingSpace() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-provider-use-exact");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );
        controller.setProfileCandidateSupplier(() -> Arrays.asList("openai-main", "zhipu-main"));

        List<Candidate> candidates = controller.suggest("/provider use", "/provider use".length());

        assertContainsValue(candidates, "/provider use openai-main");
        assertContainsValue(candidates, "/provider use zhipu-main");
    }

    @Test
    public void suggestProviderNamesWhenEditActionIsExactWithoutTrailingSpace() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-provider-edit-exact");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );
        controller.setProfileCandidateSupplier(() -> Arrays.asList("openai-main", "zhipu-main"));

        List<Candidate> candidates = controller.suggest("/provider edit", "/provider edit".length());

        assertContainsValue(candidates, "/provider edit openai-main");
        assertContainsValue(candidates, "/provider edit zhipu-main");
    }

    @Test
    public void suggestProviderDefaultNamesAndClearFromProfileSupplier() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-provider-default");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );
        controller.setProfileCandidateSupplier(() -> Arrays.asList("openai-main", "zhipu-main"));

        List<Candidate> candidates = controller.suggest("/provider default ", "/provider default ".length());

        assertContainsValue(candidates, "clear");
        assertContainsValue(candidates, "openai-main");
        assertContainsValue(candidates, "zhipu-main");
    }

    @Test
    public void suggestProviderMutationOptionsAfterProfileName() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-provider-mutation-options");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        List<Candidate> candidates = controller.suggest("/provider add zhipu-main ", "/provider add zhipu-main ".length());

        assertContainsValue(candidates, "--provider ");
        assertContainsValue(candidates, "--protocol ");
        assertContainsValue(candidates, "--model ");
        assertContainsValue(candidates, "--clear-api-key ");
    }

    @Test
    public void suggestProviderMutationProtocolValues() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-provider-mutation-protocol");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        List<Candidate> candidates = controller.suggest("/provider add zhipu-main --protocol ", "/provider add zhipu-main --protocol ".length());

        assertContainsValue(candidates, "chat");
        assertContainsValue(candidates, "responses");
    }

    @Test
    public void suggestExactModelCommandKeepsRootCandidateWithoutTrailingSpace() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-model-exact");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );
        controller.setModelCandidateSupplier(() -> Arrays.asList(
                new SlashCommandController.ModelCompletionCandidate("gpt-5-mini", "Current effective model"),
                new SlashCommandController.ModelCompletionCandidate("gpt-4.1", "Saved profile openai-main")
        ));

        List<Candidate> candidates = controller.suggest("/model", "/model".length());

        assertContainsValue(candidates, "/model ");
        assertTrue(!containsValue(candidates, "/model gpt-5-mini"));
        assertTrue(!containsValue(candidates, "/model reset"));
    }

    @Test
    public void suggestModelCandidatesWithTrailingSpace() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-model-space");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );
        controller.setModelCandidateSupplier(() -> Arrays.asList(
                new SlashCommandController.ModelCompletionCandidate("glm-4.7", "Current effective model"),
                new SlashCommandController.ModelCompletionCandidate("glm-4.7-plus", "Saved profile zhipu-main")
        ));

        List<Candidate> candidates = controller.suggest("/model ", "/model ".length());

        assertContainsValue(candidates, "glm-4.7");
        assertContainsValue(candidates, "glm-4.7-plus");
        assertContainsValue(candidates, "reset");
    }

    @Test
    public void suggestProcessSubcommandsWhenProcessCommandHasTrailingSpace() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-process");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        List<Candidate> candidates = controller.suggest("/process ", "/process ".length());

        assertContainsValue(candidates, "status ");
        assertContainsValue(candidates, "follow ");
    }

    @Test
    public void suggestProcessIdsFromActiveSessionSupplier() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-process-id");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );
        controller.setProcessCandidateSupplier(() -> Arrays.asList(
                new SlashCommandController.ProcessCompletionCandidate("proc-123", "running | live | mvn test"),
                new SlashCommandController.ProcessCompletionCandidate("proc-456", "exited | metadata-only | npm run build")
        ));

        List<Candidate> candidates = controller.suggest("/process status ", "/process status ".length());

        assertContainsValue(candidates, "proc-123");
        assertContainsValue(candidates, "proc-456");
    }

    @Test
    public void suggestProcessLimitsAfterSelectingProcessId() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-process-limit");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );
        controller.setProcessCandidateSupplier(() -> Arrays.asList(
                new SlashCommandController.ProcessCompletionCandidate("proc-123", "running | live | mvn test")
        ));

        List<Candidate> candidates = controller.suggest("/process follow proc-123 ", "/process follow proc-123 ".length());

        assertContainsValue(candidates, "800");
        assertContainsValue(candidates, "1600");
    }

    @Test
    public void suggestProcessWriteStopsCompletingAfterProcessId() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-process-write");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );
        controller.setProcessCandidateSupplier(() -> Arrays.asList(
                new SlashCommandController.ProcessCompletionCandidate("proc-123", "running | live | mvn test")
        ));

        List<Candidate> candidates = controller.suggest("/process write proc-123 ", "/process write proc-123 ".length());

        assertTrue("Expected no completion candidates after process write text position", candidates.isEmpty());
    }

    @Test
    public void resolveSlashMenuActionDoesNotInsertDuplicateSlashInsideSlashCommands() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-menu");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        assertTrue(controller.resolveSlashMenuAction("", 0) == SlashCommandController.SlashMenuAction.INSERT_AND_MENU);
        assertTrue(controller.resolveSlashMenuAction("/", 1) == SlashCommandController.SlashMenuAction.MENU_ONLY);
        assertTrue(controller.resolveSlashMenuAction("/process", "/process".length()) == SlashCommandController.SlashMenuAction.MENU_ONLY);
        assertTrue(controller.resolveSlashMenuAction("/process write proc-123 ", "/process write proc-123 ".length())
                == SlashCommandController.SlashMenuAction.INSERT_ONLY);
    }

    @Test
    public void resolveAcceptLineActionIgnoresBlankInput() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-enter-action");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        assertTrue(controller.resolveAcceptLineAction("") == SlashCommandController.EnterAction.IGNORE_EMPTY);
        assertTrue(controller.resolveAcceptLineAction("   ") == SlashCommandController.EnterAction.IGNORE_EMPTY);
        assertTrue(controller.resolveAcceptLineAction("/exit") == SlashCommandController.EnterAction.ACCEPT);
        assertTrue(controller.resolveAcceptLineAction("hello") == SlashCommandController.EnterAction.ACCEPT);
    }

    @Test
    public void openSlashMenuListsChoicesWithoutCompletingFirstCommand() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-open-menu");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        RecordingBuffer buffer = new RecordingBuffer("");
        List<String> widgetCalls = new ArrayList<String>();

        controller.openSlashMenu(recordingLineReader(buffer, widgetCalls));

        assertEquals("/", buffer.value());
        assertTrue(!widgetCalls.contains(LineReader.LIST_CHOICES));
        assertTrue(!widgetCalls.contains(LineReader.MENU_COMPLETE));
        SlashCommandController.PaletteSnapshot snapshot = controller.getPaletteSnapshot();
        assertTrue(snapshot.isOpen());
        assertEquals("/", snapshot.getQuery());
        assertEquals("/help", snapshot.getItems().get(snapshot.getSelectedIndex()).getValue());
    }

    @Test
    public void openSlashMenuKeepsExistingSlashPrefixWhileRefreshingChoices() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-open-existing");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        RecordingBuffer buffer = new RecordingBuffer("/re");
        List<String> widgetCalls = new ArrayList<String>();

        controller.openSlashMenu(recordingLineReader(buffer, widgetCalls));

        assertEquals("/re", buffer.value());
        assertTrue(!widgetCalls.contains(LineReader.LIST_CHOICES));
        assertTrue(!widgetCalls.contains(LineReader.MENU_COMPLETE));
        SlashCommandController.PaletteSnapshot snapshot = controller.getPaletteSnapshot();
        assertTrue(snapshot.isOpen());
        assertEquals("/re", snapshot.getQuery());
    }

    @Test
    public void movePaletteSelectionAdvancesWithoutTabBootstrap() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-open-move");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        RecordingBuffer buffer = new RecordingBuffer("");
        controller.openSlashMenu(recordingLineReader(buffer, new ArrayList<String>()));
        controller.movePaletteSelection(1);

        SlashCommandController.PaletteSnapshot snapshot = controller.getPaletteSnapshot();
        assertTrue(snapshot.isOpen());
        assertEquals("/status", snapshot.getItems().get(snapshot.getSelectedIndex()).getValue());
    }

    @Test
    public void acceptSlashSelectionReplacesBufferWithHighlightedCommand() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-accept");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        RecordingBuffer buffer = new RecordingBuffer("");
        List<String> widgetCalls = new ArrayList<String>();
        LineReader lineReader = recordingLineReader(buffer, widgetCalls);

        controller.openSlashMenu(lineReader);
        controller.movePaletteSelection(1);

        assertTrue(controller.acceptSlashSelection(lineReader, true));
        assertEquals("/status", buffer.value());
        assertTrue(!controller.getPaletteSnapshot().isOpen());
    }

    @Test
    public void acceptSlashSelectionKeepsCommandPrefixForArgumentCandidates() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-stream-accept");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        RecordingBuffer buffer = new RecordingBuffer("/stream ");
        LineReader lineReader = recordingLineReader(buffer, new ArrayList<String>());

        java.lang.reflect.Method updatePalette = SlashCommandController.class
                .getDeclaredMethod("updatePalette", String.class, List.class);
        updatePalette.setAccessible(true);
        updatePalette.invoke(controller, "/stream ", controller.suggest("/stream ", "/stream ".length()));
        controller.movePaletteSelection(1);

        assertTrue(controller.acceptSlashSelection(lineReader, true));
        assertEquals("/stream off", buffer.value());
        assertTrue(!controller.getPaletteSnapshot().isOpen());
    }

    @Test
    public void acceptSlashSelectionFromPartialCommandKeepsPaletteOpenForArgumentChoices() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-slash-stream-chain");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        RecordingBuffer buffer = new RecordingBuffer("/stre");
        LineReader lineReader = recordingLineReader(buffer, new ArrayList<String>());

        java.lang.reflect.Method updatePalette = SlashCommandController.class
                .getDeclaredMethod("updatePalette", String.class, List.class);
        updatePalette.setAccessible(true);
        updatePalette.invoke(controller, "/stre", controller.suggest("/stre", "/stre".length()));

        assertTrue(controller.acceptSlashSelection(lineReader, true));
        assertEquals("/stream ", buffer.value());
        SlashCommandController.PaletteSnapshot snapshot = controller.getPaletteSnapshot();
        assertTrue(snapshot.isOpen());
        assertEquals("/stream ", snapshot.getQuery());
        assertTrue(containsPaletteValue(snapshot, "on"));
        assertTrue(containsPaletteValue(snapshot, "off"));
    }

    @Test
    public void acceptLineExecutesExactOptionalCommandInsteadOfApplyingCandidate() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-enter-model-root");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );
        controller.setModelCandidateSupplier(() -> Arrays.asList(
                new SlashCommandController.ModelCompletionCandidate("glm-4.7", "Current effective model")
        ));

        RecordingBuffer buffer = new RecordingBuffer("/model");
        List<String> widgetCalls = new ArrayList<String>();
        LineReader lineReader = recordingLineReader(buffer, widgetCalls);

        updatePalette(controller, "/model");

        assertTrue(invokeAcceptLine(controller, lineReader));
        assertEquals("/model", buffer.value());
        assertTrue(widgetCalls.contains(LineReader.ACCEPT_LINE));
        assertTrue(!controller.getPaletteSnapshot().isOpen());
    }

    @Test
    public void acceptLineExecutesExactOptionalCommandWithTrailingSpace() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-enter-model-space");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );
        controller.setModelCandidateSupplier(() -> Arrays.asList(
                new SlashCommandController.ModelCompletionCandidate("glm-4.7", "Current effective model")
        ));

        RecordingBuffer buffer = new RecordingBuffer("/model ");
        List<String> widgetCalls = new ArrayList<String>();
        LineReader lineReader = recordingLineReader(buffer, widgetCalls);

        updatePalette(controller, "/model ");

        assertTrue(invokeAcceptLine(controller, lineReader));
        assertEquals("/model ", buffer.value());
        assertTrue(widgetCalls.contains(LineReader.ACCEPT_LINE));
        assertTrue(!controller.getPaletteSnapshot().isOpen());
    }

    @Test
    public void acceptLineAcceptsSelectionForIncompletePrefix() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-enter-resume-prefix");
        SlashCommandController controller = new SlashCommandController(
                new CustomCommandRegistry(workspace),
                new TuiConfigManager(workspace)
        );

        RecordingBuffer buffer = new RecordingBuffer("/res");
        List<String> widgetCalls = new ArrayList<String>();
        LineReader lineReader = recordingLineReader(buffer, widgetCalls);

        updatePalette(controller, "/res");

        assertTrue(invokeAcceptLine(controller, lineReader));
        assertEquals("/resume ", buffer.value());
        assertTrue(!widgetCalls.contains(LineReader.ACCEPT_LINE));
        assertTrue(controller.getPaletteSnapshot().isOpen());
    }

    private boolean invokeAcceptLine(SlashCommandController controller, LineReader lineReader) throws Exception {
        java.lang.reflect.Method method = SlashCommandController.class.getDeclaredMethod("acceptLine", LineReader.class);
        method.setAccessible(true);
        return Boolean.TRUE.equals(method.invoke(controller, lineReader));
    }

    private void updatePalette(SlashCommandController controller, String line) throws Exception {
        java.lang.reflect.Method updatePalette = SlashCommandController.class
                .getDeclaredMethod("updatePalette", String.class, List.class);
        updatePalette.setAccessible(true);
        updatePalette.invoke(controller, line, controller.suggest(line, line.length()));
    }

    private void assertContainsValue(List<Candidate> candidates, String value) {
        assertTrue("Expected candidate " + value, containsValue(candidates, value));
    }

    private boolean containsValue(List<Candidate> candidates, String value) {
        if (candidates == null) {
            return false;
        }
        for (Candidate candidate : candidates) {
            if (candidate != null && value.equals(candidate.value())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPaletteValue(SlashCommandController.PaletteSnapshot snapshot, String value) {
        if (snapshot == null || snapshot.getItems() == null) {
            return false;
        }
        for (SlashCommandController.PaletteItemSnapshot item : snapshot.getItems()) {
            if (item != null && value.equals(item.getValue())) {
                return true;
            }
        }
        return false;
    }

    private LineReader recordingLineReader(final RecordingBuffer buffer, final List<String> widgetCalls) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                String name = method.getName();
                if ("getBuffer".equals(name)) {
                    return buffer.proxy();
                }
                if ("callWidget".equals(name)) {
                    if (args != null && args.length > 0 && args[0] != null) {
                        widgetCalls.add(String.valueOf(args[0]));
                    }
                    return true;
                }
                if ("isSet".equals(name)) {
                    return false;
                }
                if ("getVariables".equals(name)) {
                    return null;
                }
                return null;
            }
        };
        return (LineReader) Proxy.newProxyInstance(
                LineReader.class.getClassLoader(),
                new Class[]{LineReader.class},
                handler
        );
    }

    private static final class RecordingBuffer {
        private final StringBuilder value;
        private int cursor;

        private RecordingBuffer(String initialValue) {
            this.value = new StringBuilder(initialValue == null ? "" : initialValue);
            this.cursor = this.value.length();
        }

        private Buffer proxy() {
            InvocationHandler handler = new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                    String name = method.getName();
                    if ("toString".equals(name)) {
                        return value.toString();
                    }
                    if ("cursor".equals(name)) {
                        return cursor;
                    }
                    if ("write".equals(name)) {
                        String appended = args != null && args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
                        value.insert(cursor, appended);
                        cursor += appended.length();
                        return null;
                    }
                    if ("length".equals(name)) {
                        return value.length();
                    }
                    if ("clear".equals(name)) {
                        value.setLength(0);
                        cursor = 0;
                        return true;
                    }
                    return null;
                }
            };
            return (Buffer) Proxy.newProxyInstance(
                    Buffer.class.getClassLoader(),
                    new Class[]{Buffer.class},
                    handler
            );
        }

        private String value() {
            return value.toString();
        }
    }
}
