package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.cli.config.CliWorkspaceConfig;
import io.github.lnyocly.ai4j.cli.command.CodeCommand;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptionsParser;
import io.github.lnyocly.ai4j.cli.provider.CliProviderConfigManager;
import io.github.lnyocly.ai4j.cli.provider.CliProviderProfile;
import io.github.lnyocly.ai4j.cli.provider.CliProvidersConfig;
import io.github.lnyocly.ai4j.cli.runtime.CliToolApprovalDecorator;
import io.github.lnyocly.ai4j.cli.runtime.CodingCliSessionRunner;
import io.github.lnyocly.ai4j.cli.runtime.CodingCliTuiSupport;
import io.github.lnyocly.ai4j.cli.shell.JlineShellContext;
import io.github.lnyocly.ai4j.cli.shell.JlineShellTerminalIO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMemberSnapshot;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMessage;
import io.github.lnyocly.ai4j.agent.team.AgentTeamState;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTask;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskState;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTaskStatus;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.CodingAgents;
import io.github.lnyocly.ai4j.coding.CodingSessionState;
import io.github.lnyocly.ai4j.cli.factory.CodingCliAgentFactory;
import io.github.lnyocly.ai4j.cli.factory.CodingCliAgentFactory.PreparedCodingAgent;
import io.github.lnyocly.ai4j.cli.factory.CodingCliTuiFactory;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpConfig;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpConfigManager;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpRuntimeManager;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpServerDefinition;
import io.github.lnyocly.ai4j.cli.session.CodingSessionManager;
import io.github.lnyocly.ai4j.cli.session.DefaultCodingSessionManager;
import io.github.lnyocly.ai4j.cli.session.InMemoryCodingSessionStore;
import io.github.lnyocly.ai4j.cli.session.InMemorySessionEventStore;
import io.github.lnyocly.ai4j.cli.session.StoredCodingSession;
import io.github.lnyocly.ai4j.agent.team.FileAgentTeamMessageBus;
import io.github.lnyocly.ai4j.agent.team.FileAgentTeamStateStore;
import io.github.lnyocly.ai4j.coding.process.BashProcessStatus;
import io.github.lnyocly.ai4j.coding.process.StoredProcessSnapshot;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.tui.StreamsTerminalIO;
import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiInteractionState;
import io.github.lnyocly.ai4j.tui.TuiKeyStroke;
import io.github.lnyocly.ai4j.tui.TuiKeyType;
import io.github.lnyocly.ai4j.tui.AppendOnlyTuiRuntime;
import io.github.lnyocly.ai4j.tui.TuiConfig;
import io.github.lnyocly.ai4j.tui.TuiConfigManager;
import io.github.lnyocly.ai4j.tui.TuiRenderer;
import io.github.lnyocly.ai4j.tui.TuiRuntime;
import io.github.lnyocly.ai4j.tui.TuiScreenModel;
import io.github.lnyocly.ai4j.tui.TuiSessionView;
import io.github.lnyocly.ai4j.tui.TuiTheme;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.Assert;
import org.junit.Test;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.sse.EventSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CodeCommandTest {

    @Test
    public void test_interactive_mode_runs_until_exit() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-test");
        Files.write(workspace.resolve("sample.txt"), Collections.singletonList("hello-cli"), StandardCharsets.UTF_8);

        ByteArrayInputStream input = new ByteArrayInputStream(
                "Please inspect sample.txt\n/exit\n".getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--model", "fake-model", "--workspace", workspace.toString()),
                new StreamsTerminalIO(input, out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("[tool] read_file"));
        Assert.assertTrue(output.contains("Read result: hello-cli"));
        Assert.assertTrue(output.contains("Session closed."));
    }

    @Test
    public void test_interactive_compact_command() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-compact");
        ByteArrayInputStream input = new ByteArrayInputStream(
                ("say hello\n"
                        + "/save\n"
                        + "/session\n"
                        + "/theme\n"
                        + "/theme amber\n"
                        + "/sessions\n"
                        + "/events 20\n"
                        + "/checkpoint\n"
                        + "/processes\n"
                        + "/resume session-alpha\n"
                        + "/compact\n"
                        + "/compacts 10\n"
                        + "/status\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--model", "fake-model", "--workspace", workspace.toString(), "--session-id", "session-alpha"),
                new StreamsTerminalIO(input, out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("saved session: session-alpha"));
        Assert.assertTrue(output.contains("session"));
        Assert.assertTrue(output.contains("themes:"));
        Assert.assertTrue(output.contains("theme switched to: amber"));
        Assert.assertTrue(output.contains("sessions:"));
        Assert.assertTrue(output.contains("events:"));
        Assert.assertTrue(output.contains("checkpoint"));
        Assert.assertTrue(output.contains("processes:"));
        Assert.assertTrue(output.contains("resumed session: session-alpha"));
        Assert.assertTrue(output.contains("compact: mode=manual"));
        Assert.assertTrue(output.contains("compacts:"));
        Assert.assertTrue(output.contains("items="));
        Assert.assertTrue(output.contains("checkpointGoal=Continue the CLI session."));
        Assert.assertTrue(output.contains("compact=manual"));
        Assert.assertTrue(output.contains("Session closed."));
    }

    @Test
    public void test_resume_session_across_runs() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-resume");
        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int firstExit = command.run(
                Arrays.asList(
                        "--model", "fake-model",
                        "--workspace", workspace.toString(),
                        "--session-id", "resume-me",
                        "--prompt", "remember alpha"
                ),
                new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), new ByteArrayOutputStream())
        );
        Assert.assertEquals(0, firstExit);

        ByteArrayOutputStream resumedOut = new ByteArrayOutputStream();
        ByteArrayOutputStream resumedErr = new ByteArrayOutputStream();
        int resumedExit = command.run(
                Arrays.asList(
                        "--model", "fake-model",
                        "--workspace", workspace.toString(),
                        "--resume", "resume-me",
                        "--prompt", "what do you remember"
                ),
                new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), resumedOut, resumedErr)
        );

        String output = new String(resumedOut.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, resumedExit);
        Assert.assertTrue(output.contains("session=resume-me"));
        Assert.assertTrue(output.contains("history: remember alpha"));
    }

    @Test
    public void test_interactive_history_tree_and_fork() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-fork");
        ByteArrayInputStream input = new ByteArrayInputStream(
                ("remember alpha\n"
                        + "/save\n"
                        + "/fork session-beta\n"
                        + "what do you remember\n"
                        + "/history\n"
                        + "/tree\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--model", "fake-model", "--workspace", workspace.toString(), "--session-id", "session-alpha"),
                new StreamsTerminalIO(input, out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("forked session: session-beta <- session-alpha"));
        Assert.assertTrue(output.contains("history: remember alpha | what do you remember"));
        Assert.assertTrue(output.contains("history:"));
        Assert.assertTrue(output.contains("tree:"));
        Assert.assertTrue(output.contains("session-alpha"));
        Assert.assertTrue(output.contains("session-beta"));
    }

    @Test
    public void test_no_session_mode_avoids_file_session_store() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-no-session");
        Path sessionDir = workspace.resolve(".ai4j").resolve("sessions");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--model", "fake-model", "--workspace", workspace.toString(), "--no-session", "--prompt", "say hello"),
                new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("Echo: say hello"));
        Assert.assertFalse(Files.exists(sessionDir));
    }

    @Test
    public void test_custom_command_templates_are_listed_and_executed() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-custom-cmd");
        Path commandsDir = Files.createDirectories(workspace.resolve(".ai4j").resolve("commands"));
        Files.write(commandsDir.resolve("review.md"), Collections.singletonList(
                "# Review workspace\nReview the workspace at $WORKSPACE.\nFocus: $ARGUMENTS\nSession: $SESSION_ID"
        ), StandardCharsets.UTF_8);

        ByteArrayInputStream input = new ByteArrayInputStream(
                ("/commands\n"
                        + "/cmd review auth flow\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--model", "fake-model", "--workspace", workspace.toString()),
                new StreamsTerminalIO(input, out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("commands:"));
        Assert.assertTrue(output.contains("review"));
        Assert.assertTrue(output.contains("Echo: Review the workspace at " + workspace.toString()));
        Assert.assertTrue(output.contains("Focus: auth flow"));
    }

    @Test
    public void test_skills_command_lists_discovered_workspace_skills() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-skills");
        Path skillsDir = Files.createDirectories(workspace.resolve(".ai4j").resolve("skills").resolve("repo-review"));
        Files.write(skillsDir.resolve("SKILL.md"), Arrays.asList(
                "---",
                "name: repo-review",
                "description: Review repository changes safely.",
                "---",
                "",
                "# Repo Review",
                "Review repository changes safely."
        ), StandardCharsets.UTF_8);

        ByteArrayInputStream input = new ByteArrayInputStream(
                ("/skills\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--model", "fake-model", "--workspace", workspace.toString()),
                new StreamsTerminalIO(input, out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("skills:"));
        Assert.assertTrue(output.contains("count=1"));
        Assert.assertTrue(output.contains("repo-review"));
        Assert.assertTrue(output.contains("source=workspace"));
        Assert.assertTrue(output.contains("Review repository changes safely."));
    }

    @Test
    public void test_skills_command_with_name_prints_skill_detail_and_content() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-skill-detail");
        Path skillsDir = Files.createDirectories(workspace.resolve(".ai4j").resolve("skills").resolve("repo-review"));
        Files.write(skillsDir.resolve("SKILL.md"), Arrays.asList(
                "---",
                "name: repo-review",
                "description: Review repository changes safely.",
                "---",
                "",
                "# Repo Review",
                "Review repository changes safely.",
                "",
                "Use rg before grep."
        ), StandardCharsets.UTF_8);

        ByteArrayInputStream input = new ByteArrayInputStream(
                ("/skills repo-review\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--model", "fake-model", "--workspace", workspace.toString()),
                new StreamsTerminalIO(input, out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("skill:"));
        Assert.assertTrue(output.contains("name=repo-review"));
        Assert.assertTrue(output.contains("path="));
        Assert.assertTrue(output.contains("description=Review repository changes safely."));
        Assert.assertTrue(output.contains("roots="));
        Assert.assertFalse(output.contains("content:"));
        Assert.assertFalse(output.contains("# Repo Review"));
        Assert.assertFalse(output.contains("Use rg before grep."));
    }

    @Test
    public void test_safe_approval_mode_prompts_before_bash_exec() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-approval");
        Files.write(workspace.resolve("sample.txt"), Collections.singletonList("hello-cli"), StandardCharsets.UTF_8);

        ByteArrayInputStream input = new ByteArrayInputStream(
                ("run bash sample\n"
                        + "y\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--model", "fake-model", "--workspace", workspace.toString(), "--approval", "safe"),
                new StreamsTerminalIO(input, out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("Approval required for bash"));
        Assert.assertTrue(output.contains("type sample.txt"));
    }

    @Test
    public void test_one_shot_prompt_mode() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-oneshot");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--model", "fake-model", "--workspace", workspace.toString(), "--prompt", "say hello"),
                new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("Echo: say hello"));
        Assert.assertFalse(output.contains("assistant>"));
    }

    @Test
    public void test_one_shot_prompt_mode_does_not_duplicate_streamed_final_output() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-oneshot-stream");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--model", "fake-model", "--workspace", workspace.toString(), "--prompt", "chunked hello"),
                new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertEquals(1, countOccurrences(output, "Hello world from stream."));
    }

    @Test
    public void test_tui_mode_renders_tui_shell() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString(), "--prompt", "say hello"),
                new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("AI4J"));
        Assert.assertTrue(output.contains("fake-model"));
        Assert.assertFalse(output.contains("STATUS"));
        Assert.assertFalse(output.contains("HISTORY"));
        Assert.assertFalse(output.contains("TREE"));
        Assert.assertTrue(output.contains("Echo: say hello"));
    }

    @Test
    public void test_tui_mode_requests_approval_inline_for_safe_mode() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-approval");
        Files.write(workspace.resolve("sample.txt"), Collections.singletonList("hello-cli"), StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString(), "--approval", "safe", "--prompt", "run bash sample"),
                new StreamsTerminalIO(new ByteArrayInputStream("y\n".getBytes(StandardCharsets.UTF_8)), out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("Approval required for bash"));
        Assert.assertTrue(output.contains("type sample.txt"));
        Assert.assertTrue(output.contains("Approve? [y/N]"));
        Assert.assertTrue(output.contains("Approved"));
    }

    @Test
    public void test_tui_mode_rejection_keeps_rejected_block_without_failed_tool_card() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-approval-rejected");
        Files.write(workspace.resolve("sample.txt"), Collections.singletonList("hello-cli"), StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString(), "--approval", "safe", "--prompt", "run bash sample"),
                new StreamsTerminalIO(new ByteArrayInputStream("n\n".getBytes(StandardCharsets.UTF_8)), out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("Rejected"));
        Assert.assertFalse(output.contains("Tool failed"));
        Assert.assertFalse(output.contains("Command failed"));
    }

    @Test
    public void test_tui_mode_renders_tool_cards_for_bash_turns() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-bash");
        Files.write(workspace.resolve("sample.txt"), Collections.singletonList("hello-cli"), StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString(), "--prompt", "run bash sample"),
                new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("Ran type sample.txt"));
        Assert.assertFalse(output.contains("exit=0"));
        Assert.assertTrue(output.contains("hello-cli"));
        Assert.assertFalse(output.contains("stdout> hello-cli"));
    }

    @Test
    public void test_tui_mode_keeps_session_alive_when_apply_patch_fails() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-invalid-patch");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString(), "--prompt", "run invalid patch"),
                new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("Tool failed apply_patch"));
        Assert.assertTrue(output.contains("Unsupported patch line"));
        Assert.assertFalse(output.contains("Argument error:"));
    }

    @Test
    public void test_tui_mode_accepts_recoverable_apply_patch_headers() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-recoverable-patch");
        Files.write(workspace.resolve("sample.txt"), Collections.singletonList("value=1"), StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString(), "--prompt", "run recoverable patch"),
                new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertFalse(output.contains("Tool failed apply_patch"));
        Assert.assertEquals("value=2", new String(Files.readAllBytes(workspace.resolve("sample.txt")), StandardCharsets.UTF_8).trim());
    }

    @Test
    public void test_tui_mode_accepts_recoverable_unified_diff_apply_patch_headers() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-unified-patch");
        Files.write(workspace.resolve("sample.txt"), Collections.singletonList("value=1"), StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString(), "--prompt", "run unified diff patch"),
                new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertFalse(output.contains("Tool failed apply_patch"));
        Assert.assertEquals("value=2", new String(Files.readAllBytes(workspace.resolve("sample.txt")), StandardCharsets.UTF_8).trim());
    }

    @Test
    public void test_tui_mode_surfaces_invalid_bash_calls_without_hiding_tool_feedback() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-invalid-bash");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString(), "--prompt", "run invalid bash"),
                new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("Need to inspect the shell call."));
        Assert.assertTrue(output.contains("Command failed bash exec"));
        Assert.assertTrue(output.contains("bash exec requires a non-empty command"));
        Assert.assertTrue(output.contains("Tool error: bash exec requires a non-empty command"));
        Assert.assertFalse(output.contains("(empty command)"));
    }

    @Test
    public void test_tui_interactive_main_buffer_prints_reasoning_in_transcript_before_tool_feedback() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-interactive-reasoning");
        ByteArrayInputStream input = new ByteArrayInputStream(
                ("run invalid bash\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString()),
                new StreamsTerminalIO(input, out, err)
        );

        String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name()));
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("Thinking: Need to inspect the shell call."));
        Assert.assertTrue(output.contains("Command failed bash exec"));
        Assert.assertTrue(output.indexOf("Thinking: Need to inspect the shell call.")
                < output.indexOf("Command failed bash exec"));
    }

    @Test
    public void test_tui_mode_prints_main_buffer_status_without_fullscreen_redraw() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-spinner");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString(), "--prompt", "slow hello"),
                new RecordingInteractiveTerminal(out, err)
        );

        String rawOutput = new String(out.toByteArray(), StandardCharsets.UTF_8);
        String output = stripAnsi(rawOutput);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("Slow hello done."));
        Assert.assertFalse(rawOutput.matches("(?s).*\\r(?!\\n).*"));
        Assert.assertFalse(rawOutput.contains("\u001b[2K"));
    }

    @Test
    public void test_tui_mode_streams_chunked_main_buffer_output_without_duplicate_final_flush() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-stream");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString(), "--prompt", "chunked hello"),
                new RecordingInteractiveTerminal(out, err)
        );

        String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name()));
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("Hello world from stream."));
        Assert.assertEquals(1, countOccurrences(output, "Hello world from stream."));
        Assert.assertFalse(output.contains("Hello world from stream.Hello world from stream."));
    }

    @Test
    public void test_tui_mode_renders_fenced_code_blocks_in_streaming_transcript() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-code-block");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString(), "--prompt", "show code block"),
                new RecordingInteractiveTerminal(out, err)
        );

        String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name()));
        Assert.assertEquals(0, exitCode);
        Assert.assertFalse(output.contains("[java]"));
        Assert.assertFalse(output.contains("[code]"));
        Assert.assertFalse(output.contains("\u2063"));
        Assert.assertFalse(output.contains("\u200b"));
        Assert.assertTrue(output.contains("System.out.println(\"hi\");"));
        Assert.assertFalse(output.contains("  ╭"));
        Assert.assertFalse(output.contains("  ╰"));
    }

    @Test
    public void test_tui_mode_buffers_split_code_fence_chunks_without_leaking_backticks() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-split-fence");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString(), "--prompt", "show split fence"),
                new RecordingInteractiveTerminal(out, err)
        );

        String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name()));
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("Here is python:"));
        Assert.assertTrue(output.contains("print(\"Hello, World!\")"));
        Assert.assertTrue(output.contains("Done."));
        Assert.assertFalse(output.contains("```"));
        Assert.assertFalse(output.contains(System.lineSeparator() + "``"));
    }

    @Test
    public void test_tui_mode_does_not_duplicate_final_output_when_provider_rewrites_markdown() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-rewrite-final");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString(), "--prompt", "rewrite final markdown"),
                new RecordingInteractiveTerminal(out, err)
        );

        String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name()));
        Assert.assertEquals(0, exitCode);
        Assert.assertEquals(1, countOccurrences(output, "已经为你创建了"));
        Assert.assertTrue(output.contains("print(\"Hello, World!\")"));
    }

    @Test
    public void test_tui_mode_replays_missing_final_code_block_segment_when_streamed_text_is_incomplete() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-final-code-block");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString(), "--prompt", "final adds code block"),
                new RecordingInteractiveTerminal(out, err)
        );

        String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name()));
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("你可以通过以下命令运行它："));
        Assert.assertTrue(output.contains("python hello_world.py"));
        Assert.assertTrue(output.contains("这个程序会输出：Hello, World!"));
    }

    @Test
    public void test_stream_command_turns_streaming_off_for_current_session() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-stream-command");
        ByteArrayInputStream input = new ByteArrayInputStream(
                ("/stream off\n"
                        + "show code block\n"
                        + "/stream\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString()),
                new StreamsTerminalIO(input, out, err)
        );

        String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name()));
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("status=off"));
        Assert.assertTrue(output.contains("request=stream=false"));
        Assert.assertTrue(output.contains("renders as completed blocks"));
        Assert.assertFalse(output.contains("[java]"));
        Assert.assertFalse(output.contains("[code]"));
        Assert.assertFalse(output.contains("\u2063"));
        Assert.assertFalse(output.contains("\u200b"));
        Assert.assertTrue(output.contains("System.out.println(\"hi\");"));
    }

    @Test
    public void test_stream_command_is_on_by_default_for_new_session() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-stream-default-on");
        ByteArrayInputStream input = new ByteArrayInputStream(
                ("/stream\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString()),
                new StreamsTerminalIO(input, out, err)
        );

        String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name()));
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("status=on"));
        Assert.assertTrue(output.contains("request=stream=true"));
        Assert.assertTrue(output.contains("stream incrementally"));
    }

    @Test
    public void test_stream_command_turns_request_streaming_on_for_current_session() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-stream-command-on");
        ByteArrayInputStream input = new ByteArrayInputStream(
                ("/stream on\n"
                        + "record request mode\n"
                        + "/stream\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString()),
                new StreamsTerminalIO(input, out, err)
        );

        String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name()));
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("status=on"));
        Assert.assertTrue(output.contains("request=stream=true"));
        Assert.assertTrue(output.contains("mode=createStream"));
    }

    @Test
    public void test_cli_stream_option_false_uses_non_streaming_model_request() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-stream-off-request");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--model", "fake-model", "--workspace", workspace.toString(), "--stream", "false", "--prompt", "record request mode"),
                new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("mode=create"));
        Assert.assertFalse(output.contains("mode=createStream"));
    }

    @Test
    public void test_cli_stream_option_uses_streaming_model_request() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-stream-option");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--model", "fake-model", "--workspace", workspace.toString(), "--stream", "true", "--prompt", "record request mode"),
                new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("mode=createStream"));
    }

    @Test
    public void test_stream_off_does_not_duplicate_completed_assistant_output() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-stream-off-duplicate");
        ByteArrayInputStream input = new ByteArrayInputStream(
                ("/stream off\n"
                        + "chunked hello\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString()),
                new StreamsTerminalIO(input, out, err)
        );

        String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name()));
        Assert.assertEquals(0, exitCode);
        Assert.assertEquals(1, countOccurrences(output, "Hello world from stream."));
    }

    @Test
    public void test_provider_and_model_commands_switch_runtime_and_persist_configs() throws Exception {
        Path home = Files.createTempDirectory("ai4j-cli-provider-home");
        Path workspace = Files.createTempDirectory("ai4j-cli-provider-workspace");
        String previousUserHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            CliProviderConfigManager manager = new CliProviderConfigManager(workspace);
            CliProvidersConfig providersConfig = CliProvidersConfig.builder()
                    .defaultProfile("openai-main")
                    .build();
            providersConfig.getProfiles().put("openai-main", CliProviderProfile.builder()
                    .provider("openai")
                    .protocol("chat")
                    .model("fake-model")
                    .apiKey("openai-main-key")
                    .build());
            providersConfig.getProfiles().put("zhipu-main", CliProviderProfile.builder()
                    .provider("zhipu")
                    .protocol("chat")
                    .model("glm-4.7")
                    .baseUrl("https://open.bigmodel.cn/api/coding/paas/v4")
                    .apiKey("zhipu-main-key")
                    .build());
            manager.saveProvidersConfig(providersConfig);

            ByteArrayInputStream input = new ByteArrayInputStream(
                    ("/provider save openai-local\n"
                            + "/provider default openai-local\n"
                            + "/provider use zhipu-main\n"
                            + "/status\n"
                            + "/model glm-4.7-plus\n"
                            + "/status\n"
                            + "/provider default clear\n"
                            + "/model reset\n"
                            + "/status\n"
                            + "/exit\n").getBytes(StandardCharsets.UTF_8)
            );
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();

            CodeCommand command = new CodeCommand(
                    new FakeCodingCliAgentFactory(),
                    Collections.<String, String>emptyMap(),
                    new Properties(),
                    workspace
            );

            int exitCode = command.run(
                    Arrays.asList(
                            "--ui", "tui",
                            "--workspace", workspace.toString(),
                            "--provider", "openai",
                            "--protocol", "chat",
                            "--model", "fake-model",
                            "--api-key", "openai-cli-key"
                    ),
                    new StreamsTerminalIO(input, out, err)
            );

            String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name()));
            Assert.assertEquals(0, exitCode);
            Assert.assertTrue(output.contains("provider saved: openai-local"));
            Assert.assertTrue(output.contains("provider default: openai-local"));
            Assert.assertTrue(output.contains("provider=zhipu, protocol=chat, model=glm-4.7"));
            Assert.assertTrue(output.contains("modelOverride=glm-4.7-plus"));
            Assert.assertTrue(output.contains("provider=zhipu, protocol=chat, model=glm-4.7-plus"));
            Assert.assertTrue(output.contains("provider default cleared"));
            Assert.assertTrue(output.contains("modelOverride=(none)"));

            CliWorkspaceConfig workspaceConfig = manager.loadWorkspaceConfig();
            Assert.assertEquals("zhipu-main", workspaceConfig.getActiveProfile());
            Assert.assertNull(workspaceConfig.getModelOverride());

            CliProvidersConfig savedProviders = manager.loadProvidersConfig();
            Assert.assertNull(savedProviders.getDefaultProfile());
            CliProviderProfile savedProfile = manager.getProfile("openai-local");
            Assert.assertNotNull(savedProfile);
            Assert.assertEquals("openai", savedProfile.getProvider());
            Assert.assertEquals("fake-model", savedProfile.getModel());
            Assert.assertEquals("openai-cli-key", savedProfile.getApiKey());
        } finally {
            restoreUserHome(previousUserHome);
        }
    }

    @Test
    public void test_provider_add_and_edit_persist_explicit_protocols() throws Exception {
        Path home = Files.createTempDirectory("ai4j-cli-provider-add-home");
        Path workspace = Files.createTempDirectory("ai4j-cli-provider-add-workspace");
        String previousUserHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            CliProviderConfigManager manager = new CliProviderConfigManager(workspace);

            ByteArrayInputStream input = new ByteArrayInputStream(
                    ("/provider add zhipu-added --provider zhipu --model glm-4.7 --base-url https://open.bigmodel.cn/api/coding/paas/v4 --api-key added-key\n"
                            + "/provider edit zhipu-added --model glm-4.7-plus\n"
                            + "/provider use zhipu-added\n"
                            + "/status\n"
                            + "/exit\n").getBytes(StandardCharsets.UTF_8)
            );
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();

            CodeCommand command = new CodeCommand(
                    new FakeCodingCliAgentFactory(),
                    Collections.<String, String>emptyMap(),
                    new Properties(),
                    workspace
            );

            int exitCode = command.run(
                    Arrays.asList(
                            "--ui", "tui",
                            "--workspace", workspace.toString(),
                            "--provider", "openai",
                            "--protocol", "chat",
                            "--model", "fake-model",
                            "--api-key", "openai-cli-key"
                    ),
                    new StreamsTerminalIO(input, out, err)
            );

            String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name()));
            Assert.assertEquals(0, exitCode);
            Assert.assertTrue(output.contains("provider added: zhipu-added"));
            Assert.assertTrue(output.contains("provider updated: zhipu-added"));
            Assert.assertTrue(output.contains("provider=zhipu, protocol=chat, model=glm-4.7-plus"));

            CliProviderProfile savedProfile = manager.getProfile("zhipu-added");
            Assert.assertNotNull(savedProfile);
            Assert.assertEquals("zhipu", savedProfile.getProvider());
            Assert.assertEquals("chat", savedProfile.getProtocol());
            Assert.assertEquals("glm-4.7-plus", savedProfile.getModel());
            Assert.assertEquals("https://open.bigmodel.cn/api/coding/paas/v4", savedProfile.getBaseUrl());
            Assert.assertEquals("added-key", savedProfile.getApiKey());
        } finally {
            restoreUserHome(previousUserHome);
        }
    }

    @Test
    public void test_experimental_command_persists_workspace_flags() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-experimental-workspace");
        CliProviderConfigManager manager = new CliProviderConfigManager(workspace);

        ByteArrayInputStream input = new ByteArrayInputStream(
                ("/experimental\n"
                        + "/experimental subagent off\n"
                        + "/experimental agent-teams off\n"
                        + "/experimental\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList(
                        "--ui", "tui",
                        "--workspace", workspace.toString(),
                        "--provider", "openai",
                        "--protocol", "chat",
                        "--model", "fake-model",
                        "--api-key", "openai-cli-key"
                ),
                new StreamsTerminalIO(input, out, err)
        );

        Assert.assertEquals(0, exitCode);

        CliWorkspaceConfig workspaceConfig = manager.loadWorkspaceConfig();
        Assert.assertEquals(Boolean.FALSE, workspaceConfig.getExperimentalSubagentsEnabled());
        Assert.assertEquals(Boolean.FALSE, workspaceConfig.getExperimentalAgentTeamsEnabled());
    }

    @Test
    public void test_team_management_commands_render_persisted_team_state() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-team-management");
        seedPersistedTeamState(workspace, "experimental-delivery-team");

        ByteArrayInputStream input = new ByteArrayInputStream(
                ("/team list\n"
                        + "/team status experimental-delivery-team\n"
                        + "/team messages experimental-delivery-team 5\n"
                        + "/team resume experimental-delivery-team\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--model", "fake-model", "--workspace", workspace.toString()),
                new StreamsTerminalIO(input, out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("teams:"));
        Assert.assertTrue(output.contains("experimental-delivery-team"));
        Assert.assertTrue(output.contains("team status:"));
        Assert.assertTrue(output.contains("objective=Deliver a travel planner demo."));
        Assert.assertTrue(output.contains("team messages:"));
        Assert.assertTrue(output.contains("Define the backend contract first."));
        Assert.assertTrue(output.contains("team resumed: experimental-delivery-team"));
        Assert.assertTrue(output.contains("team board:"));
        Assert.assertTrue(output.contains("lane Backend"));
    }

    @Test
    public void test_mcp_commands_persist_configs_and_render_statuses() throws Exception {
        Path home = Files.createTempDirectory("ai4j-cli-mcp-home");
        Path workspace = Files.createTempDirectory("ai4j-cli-mcp-workspace");
        String previousUserHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            CliMcpConfigManager manager = new CliMcpConfigManager(workspace);
            CliMcpConfig globalConfig = CliMcpConfig.builder().build();
            globalConfig.getMcpServers().put("fetch", CliMcpServerDefinition.builder()
                    .type("sse")
                    .url("https://mcp.api-inference.modelscope.net/1e1a663049b340/sse")
                    .build());
            manager.saveGlobalConfig(globalConfig);

            ByteArrayInputStream input = new ByteArrayInputStream(
                    ("/mcp\n"
                            + "/mcp enable fetch\n"
                            + "/mcp pause fetch\n"
                            + "/mcp resume fetch\n"
                            + "/mcp add --transport http bing-cn-mcp-server https://mcp.api-inference.modelscope.net/0904773a8c2045/mcp\n"
                            + "/mcp enable bing-cn-mcp-server\n"
                            + "/mcp remove bing-cn-mcp-server\n"
                            + "/exit\n").getBytes(StandardCharsets.UTF_8)
            );
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();

            CodeCommand command = new CodeCommand(
                    new FakeCodingCliAgentFactory(),
                    Collections.<String, String>emptyMap(),
                    new Properties(),
                    workspace
            );

            int exitCode = command.run(
                    Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString()),
                    new StreamsTerminalIO(input, out, err)
            );

            String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name()));
            Assert.assertEquals(0, exitCode);
            Assert.assertTrue(output, output.contains("fetch | type=sse | state=disabled | workspace=disabled | paused=no | tools=0"));
            Assert.assertTrue(output, output.contains("fetch | type=sse | state=configured | workspace=enabled | paused=no | tools=0"));
            Assert.assertTrue(output, output.contains("fetch | type=sse | state=paused | workspace=enabled | paused=yes | tools=0"));
            Assert.assertTrue(output, output.contains("mcp added: bing-cn-mcp-server"));
            Assert.assertTrue(output, output.contains("mcp removed: bing-cn-mcp-server"));

            CliWorkspaceConfig workspaceConfig = manager.loadWorkspaceConfig();
            Assert.assertEquals(Collections.singletonList("fetch"), workspaceConfig.getEnabledMcpServers());

            CliMcpConfig savedGlobal = manager.loadGlobalConfig();
            Assert.assertTrue(savedGlobal.getMcpServers().containsKey("fetch"));
            Assert.assertFalse(savedGlobal.getMcpServers().containsKey("bing-cn-mcp-server"));
        } finally {
            restoreUserHome(previousUserHome);
        }
    }

    @Test
    public void test_startup_warns_for_unavailable_mcp_servers_without_aborting_session() throws Exception {
        Path home = Files.createTempDirectory("ai4j-cli-mcp-warning-home");
        Path workspace = Files.createTempDirectory("ai4j-cli-mcp-warning-workspace");
        String previousUserHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            CliMcpConfigManager manager = new CliMcpConfigManager(workspace);
            CliMcpConfig globalConfig = CliMcpConfig.builder().build();
            globalConfig.getMcpServers().put("broken", CliMcpServerDefinition.builder()
                    .type("sse")
                    .build());
            manager.saveGlobalConfig(globalConfig);

            CliWorkspaceConfig workspaceConfig = new CliWorkspaceConfig();
            workspaceConfig.setEnabledMcpServers(Arrays.asList("broken", "missing"));
            manager.saveWorkspaceConfig(workspaceConfig);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();

            CodeCommand command = new CodeCommand(
                    new ConfiguredRuntimeCodingCliAgentFactory(),
                    Collections.<String, String>emptyMap(),
                    new Properties(),
                    workspace
            );

            int exitCode = command.run(
                    Arrays.asList("--model", "fake-model", "--workspace", workspace.toString(), "--prompt", "say hello"),
                    new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), out, err)
            );

            String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name()));
            Assert.assertEquals(0, exitCode);
            Assert.assertTrue(output.contains("Warning: MCP unavailable: broken (sse transport requires url)"));
            Assert.assertTrue(output.contains("Warning: MCP unavailable: missing (workspace references undefined MCP server)"));
            Assert.assertTrue(output.contains("Echo: say hello"));
        } finally {
            restoreUserHome(previousUserHome);
        }
    }

    @Test
    public void test_stream_off_collapses_blank_reasoning_lines_in_main_buffer() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-stream-off-reasoning");
        ByteArrayInputStream input = new ByteArrayInputStream(
                ("/stream off\n"
                        + "sparse reasoning\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString()),
                new StreamsTerminalIO(input, out, err)
        );

        String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name()));
        String continuationBlankLine = System.lineSeparator() + "          " + System.lineSeparator();
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("Thinking: First line."));
        Assert.assertTrue(output.contains("Second line."));
        Assert.assertFalse(output.contains(continuationBlankLine));
    }

    @Test
    public void test_tui_mode_uses_text_commands_in_non_alternate_screen_mode() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-main-buffer-commands");
        ByteArrayInputStream input = new ByteArrayInputStream(
                ("/commands\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString()),
                new StreamsTerminalIO(input, out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("Commands"));
        Assert.assertTrue(output.contains("(none)"));
        Assert.assertFalse(output.contains("Exit session"));
        Assert.assertFalse(output.contains("commands:"));
    }

    @Test
    public void test_append_only_tui_keeps_error_state_when_stream_finishes_with_blank_output() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-stream-error");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                new AppendOnlyPromptTuiFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString(), "--prompt", "stream auth fail"),
                new RecordingInteractiveTerminal(out, err)
        );

        String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name()));
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("Error: Invalid API key"));
        Assert.assertFalse(output.contains("Done"));
    }

    @Test
    public void test_append_only_tui_slash_enter_applies_selection_without_immediate_dispatch() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-slash-enter");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                new AppendOnlyPromptTuiFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString()),
                new KeyedRecordingInteractiveTerminal(out, err, Arrays.asList(
                        TuiKeyStroke.character("/"),
                        TuiKeyStroke.character("s"),
                        TuiKeyStroke.of(TuiKeyType.ENTER),
                        TuiKeyStroke.of(TuiKeyType.ESCAPE),
                        TuiKeyStroke.character("/"),
                        TuiKeyStroke.character("e"),
                        TuiKeyStroke.character("x"),
                        TuiKeyStroke.character("i"),
                        TuiKeyStroke.character("t"),
                        TuiKeyStroke.of(TuiKeyType.ENTER)
                ))
        );

        String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name()));
        Assert.assertEquals(0, exitCode);
        Assert.assertFalse(output.contains("status:"));
    }

    @Test
    public void test_append_only_tui_typing_slash_keeps_a_single_input_render() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-single-slash");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                new AppendOnlyPromptTuiFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString()),
                new KeyedRecordingInteractiveTerminal(out, err, Collections.singletonList(
                        TuiKeyStroke.character("/")
                ))
        );

        String output = stripAnsi(out.toString(StandardCharsets.UTF_8.name())).replace("\r", "");
        Assert.assertEquals(0, exitCode);
        Assert.assertEquals(1, countOccurrences(output, "> /"));
        Assert.assertEquals(1, countOccurrences(output, "• Commands"));
    }

    @Test
    public void test_replay_command_groups_recent_turns() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-replay");
        ByteArrayInputStream input = new ByteArrayInputStream(
                ("first message\n"
                        + "second message\n"
                        + "/replay 20\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--model", "fake-model", "--workspace", workspace.toString()),
                new StreamsTerminalIO(input, out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("replay:"));
        Assert.assertTrue(output.contains("first message"));
        Assert.assertFalse(output.contains("you> first message"));
        Assert.assertTrue(output.contains("Echo: second message"));
        Assert.assertFalse(output.contains("assistant> Echo: second message"));
    }

    @Test
    public void test_replay_command_hides_reasoning_prefix() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-replay-reasoning");
        ByteArrayInputStream input = new ByteArrayInputStream(
                ("reason first\n"
                        + "/replay 20\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--model", "fake-model", "--workspace", workspace.toString()),
                new StreamsTerminalIO(input, out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("replay:"));
        Assert.assertTrue(output.contains("Need to inspect the request first."));
        Assert.assertTrue(output.contains("Done reasoning."));
        Assert.assertFalse(output.contains("assistant> Done reasoning."));
        Assert.assertFalse(output.contains("reasoning> "));
    }

    @Test
    public void test_process_status_and_follow_commands_with_restored_snapshot() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-process-status");
        seedStoredSession(workspace, "process-seeded", "proc_demo", "[stdout] ready\n[stdout] waiting\n");

        ByteArrayInputStream input = new ByteArrayInputStream(
                ("/resume process-seeded\n"
                        + "/process status proc_demo\n"
                        + "/process follow proc_demo 200\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--model", "fake-model", "--workspace", workspace.toString()),
                new StreamsTerminalIO(input, out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("resumed session: process-seeded"));
        Assert.assertTrue(output.contains("process status:"));
        Assert.assertTrue(output.contains("id=proc_demo"));
        Assert.assertTrue(output.contains("mode=metadata-only"));
        Assert.assertTrue(output.contains("process follow:"));
        Assert.assertTrue(output.contains("ready"));
        Assert.assertTrue(output.contains("waiting"));
    }

    @Test
    public void test_tui_mode_uses_replay_command_in_non_alternate_screen_mode() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-tui-replay");
        ByteArrayInputStream input = new ByteArrayInputStream(
                ("first\n"
                        + "second\n"
                        + "/replay 20\n"
                        + "/exit\n").getBytes(StandardCharsets.UTF_8)
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString()),
                new StreamsTerminalIO(input, out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("Replay"));
        Assert.assertTrue(output.contains("first"));
        Assert.assertFalse(output.contains("history\n"));
        Assert.assertTrue(output.contains("Echo: second"));
    }

    @Test
    public void test_default_non_alternate_screen_terminal_uses_main_buffer_mode() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-main-buffer-default");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        RecordingInteractiveTerminal terminal = new RecordingInteractiveTerminal(out, err);

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString()),
                terminal
        );

        String output = out.toString(StandardCharsets.UTF_8.name());
        Assert.assertEquals(0, exitCode);
        Assert.assertEquals(1, terminal.getReadLineCalls());
        Assert.assertTrue(output.contains("AI4J  fake-model"));
        Assert.assertFalse(output.contains("Interactive TUI input is unavailable"));
    }

    @Test
    public void test_main_buffer_mode_defers_next_prompt_until_output_starts() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-main-buffer-async");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ScriptedInteractiveTerminal terminal = new ScriptedInteractiveTerminal(
                out,
                err,
                Arrays.asList("slow hello", "/exit")
        );

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> future = executor.submit(new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    return command.run(
                            Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString()),
                            terminal
                    );
                }
            });

            long deadline = System.currentTimeMillis() + 300L;
            while (System.currentTimeMillis() < deadline && terminal.getReadLineCalls() < 2) {
                Thread.sleep(20L);
            }

            Assert.assertEquals("expected prompt activation to stay deferred until output actually begins", 1, terminal.getReadLineCalls());
            Assert.assertEquals(0, future.get(3L, TimeUnit.SECONDS).intValue());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void test_tui_factory_allows_custom_renderer_and_runtime() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-custom-tui");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        RecordingTuiFactory tuiFactory = new RecordingTuiFactory();

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                tuiFactory,
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString(), "--prompt", "say hello"),
                new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), out, err)
        );

        String output = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("CUSTOM-TUI"));
        Assert.assertTrue(output.contains("fake-model"));
        Assert.assertTrue(tuiFactory.rendered);
    }

    @Test
    public void test_tui_mode_keeps_raw_palette_when_alternate_screen_is_disabled() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-raw-tui");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        RecordingInteractiveTerminal terminal = new RecordingInteractiveTerminal(out, err);
        RawInteractiveTuiFactory tuiFactory = new RawInteractiveTuiFactory(Arrays.asList(
                TuiKeyStroke.of(TuiKeyType.CTRL_P),
                TuiKeyStroke.character("e"),
                TuiKeyStroke.character("x"),
                TuiKeyStroke.character("i"),
                TuiKeyStroke.character("t"),
                TuiKeyStroke.of(TuiKeyType.ENTER)
        ));

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                tuiFactory,
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString()),
                terminal
        );

        String output = out.toString(StandardCharsets.UTF_8.name());
        Assert.assertEquals(0, exitCode);
        Assert.assertEquals(0, terminal.getReadLineCalls());
        Assert.assertTrue(output.contains("RAW-TUI"));
    }

    @Test
    public void test_raw_tui_escape_interrupts_active_turn() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-raw-tui-interrupt");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        final Deque<TuiKeyStroke> keys = new ArrayDeque<TuiKeyStroke>(Arrays.asList(
                TuiKeyStroke.character("s"),
                TuiKeyStroke.character("l"),
                TuiKeyStroke.character("o"),
                TuiKeyStroke.character("w"),
                TuiKeyStroke.character(" "),
                TuiKeyStroke.character("h"),
                TuiKeyStroke.character("e"),
                TuiKeyStroke.character("l"),
                TuiKeyStroke.character("l"),
                TuiKeyStroke.character("o"),
                TuiKeyStroke.of(TuiKeyType.ENTER),
                TuiKeyStroke.of(TuiKeyType.ESCAPE)
        ));
        RecordingInteractiveTerminal terminal = new RecordingInteractiveTerminal(out, err) {
            @Override
            public boolean isInputClosed() {
                return keys.isEmpty();
            }
        };

        CodingCliTuiFactory tuiFactory = new CodingCliTuiFactory() {
            @Override
            public CodingCliTuiSupport create(CodeCommandOptions options,
                                              TerminalIO terminal,
                                              TuiConfigManager configManager) {
                TuiConfig config = new TuiConfig();
                config.setUseAlternateScreen(false);
                TuiTheme theme = new TuiTheme();
                TuiRenderer renderer = new TuiRenderer() {
                    @Override
                    public int getMaxEvents() {
                        return 10;
                    }

                    @Override
                    public String getThemeName() {
                        return "raw-interrupt";
                    }

                    @Override
                    public void updateTheme(TuiConfig config, TuiTheme theme) {
                    }

                    @Override
                    public String render(TuiScreenModel screenModel) {
                        return "RAW phase="
                                + (screenModel == null || screenModel.getAssistantViewModel() == null
                                ? ""
                                : screenModel.getAssistantViewModel().getPhase())
                                + " detail="
                                + (screenModel == null || screenModel.getAssistantViewModel() == null
                                ? ""
                                : String.valueOf(screenModel.getAssistantViewModel().getPhaseDetail()));
                    }
                };
                TuiRuntime runtime = new TuiRuntime() {
                    @Override
                    public boolean supportsRawInput() {
                        return true;
                    }

                    @Override
                    public void enter() {
                    }

                    @Override
                    public void exit() {
                    }

                    @Override
                    public TuiKeyStroke readKeyStroke(long timeoutMs) {
                        return keys.isEmpty() ? null : keys.removeFirst();
                    }

                    @Override
                    public void render(TuiScreenModel screenModel) {
                        terminal.println(renderer.render(screenModel));
                    }
                };
                return new CodingCliTuiSupport(config, theme, renderer, runtime);
            }
        };

        CodeCommand command = new CodeCommand(
                new FakeCodingCliAgentFactory(),
                tuiFactory,
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );

        int exitCode = command.run(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString()),
                terminal
        );

        String output = out.toString(StandardCharsets.UTF_8.name());
        Assert.assertEquals(0, exitCode);
        Assert.assertTrue(output.contains("Conversation interrupted by user."));
        Assert.assertFalse(output.contains("Slow hello done."));
    }

    @Test
    public void test_main_buffer_jline_user_interrupt_suppresses_final_output() throws Exception {
        String previous = System.getProperty("ai4j.tui.main-buffer");
        System.setProperty("ai4j.tui.main-buffer", "true");

        Path workspace = Files.createTempDirectory("ai4j-cli-main-buffer-interrupt");
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .dumb(true)
                .streams(input, output)
                .encoding(StandardCharsets.UTF_8)
                .build();
        ScriptedLineReaderHandler handler = new ScriptedLineReaderHandler(Arrays.asList("slow hello", "/exit"));
        LineReader lineReader = (LineReader) Proxy.newProxyInstance(
                LineReader.class.getClassLoader(),
                new Class<?>[]{LineReader.class},
                handler
        );
        JlineShellContext shellContext = newJlineShellContext(terminal, lineReader);
        JlineShellTerminalIO terminalIO = new JlineShellTerminalIO(shellContext, null);

        Properties properties = new Properties();
        CodeCommandOptions options = new CodeCommandOptionsParser().parse(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString()),
                Collections.<String, String>emptyMap(),
                properties,
                workspace
        );
        CodingSessionManager sessionManager = new DefaultCodingSessionManager(
                new InMemoryCodingSessionStore(workspace.resolve(".ai4j").resolve("sessions")),
                new InMemorySessionEventStore()
        );
        TuiInteractionState interactionState = new TuiInteractionState();
        FakeCodingCliAgentFactory agentFactory = new FakeCodingCliAgentFactory();
        CodingCliAgentFactory.PreparedCodingAgent prepared = agentFactory.prepare(options, terminalIO, interactionState);
        CodingCliSessionRunner runner = new CodingCliSessionRunner(
                prepared.getAgent(),
                prepared.getProtocol(),
                options,
                terminalIO,
                sessionManager,
                interactionState,
                new RecordingTuiFactory(),
                null,
                agentFactory,
                Collections.<String, String>emptyMap(),
                properties
        );

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> future = executor.submit(new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    return runner.run();
                }
            });

            String turnId = null;
            for (int attempt = 0; attempt < 40; attempt++) {
                Thread.sleep(50L);
                turnId = (String) readPrivateField(runner, "activeMainBufferTurnId");
                if (turnId != null) {
                    break;
                }
            }
            Assert.assertNotNull(turnId);
            invokePrivateMethod(runner, "interruptActiveMainBufferTurn", new Class<?>[]{String.class}, turnId);

            int exitCode = future.get(5, TimeUnit.SECONDS);
            String rendered = output.toString(StandardCharsets.UTF_8.name());
            Assert.assertEquals(0, exitCode);
            Assert.assertTrue(rendered.contains("Conversation interrupted by user."));
            Assert.assertFalse(rendered.contains("Slow hello done."));
            Assert.assertEquals(2, handler.getReadLineCalls());
        } finally {
            executor.shutdownNow();
            terminalIO.close();
            shellContext.close();
            restoreProperty("ai4j.tui.main-buffer", previous);
        }
    }

    @Test
    public void test_main_buffer_jline_user_interrupt_cancels_active_chat_stream() throws Exception {
        String previous = System.getProperty("ai4j.tui.main-buffer");
        System.setProperty("ai4j.tui.main-buffer", "true");

        Path workspace = Files.createTempDirectory("ai4j-cli-main-buffer-chat-cancel");
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .dumb(true)
                .streams(input, output)
                .encoding(StandardCharsets.UTF_8)
                .build();
        ScriptedLineReaderHandler handler = new ScriptedLineReaderHandler(Arrays.asList("slow chat stream", "/exit"));
        LineReader lineReader = (LineReader) Proxy.newProxyInstance(
                LineReader.class.getClassLoader(),
                new Class<?>[]{LineReader.class},
                handler
        );
        JlineShellContext shellContext = newJlineShellContext(terminal, lineReader);
        JlineShellTerminalIO terminalIO = new JlineShellTerminalIO(shellContext, null);

        Properties properties = new Properties();
        CodeCommandOptions options = new CodeCommandOptionsParser().parse(
                Arrays.asList("--ui", "tui", "--model", "fake-model", "--workspace", workspace.toString()),
                Collections.<String, String>emptyMap(),
                properties,
                workspace
        );
        CodingSessionManager sessionManager = new DefaultCodingSessionManager(
                new InMemoryCodingSessionStore(workspace.resolve(".ai4j").resolve("sessions")),
                new InMemorySessionEventStore()
        );
        TuiInteractionState interactionState = new TuiInteractionState();
        CountDownLatch cancelled = new CountDownLatch(1);
        CodingCliAgentFactory agentFactory = new CustomModelCodingCliAgentFactory(
                new ChatModelClient(new BlockingChatService(cancelled))
        );
        CodingCliAgentFactory.PreparedCodingAgent prepared = agentFactory.prepare(options, terminalIO, interactionState);
        CodingCliSessionRunner runner = new CodingCliSessionRunner(
                prepared.getAgent(),
                prepared.getProtocol(),
                options,
                terminalIO,
                sessionManager,
                interactionState,
                new RecordingTuiFactory(),
                null,
                agentFactory,
                Collections.<String, String>emptyMap(),
                properties
        );

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> future = executor.submit(new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    return runner.run();
                }
            });

            String turnId = null;
            for (int attempt = 0; attempt < 40; attempt++) {
                Thread.sleep(50L);
                turnId = (String) readPrivateField(runner, "activeMainBufferTurnId");
                if (turnId != null) {
                    break;
                }
            }
            Assert.assertNotNull(turnId);
            invokePrivateMethod(runner, "interruptActiveMainBufferTurn", new Class<?>[]{String.class}, turnId);

            int exitCode = future.get(5, TimeUnit.SECONDS);
            String rendered = output.toString(StandardCharsets.UTF_8.name());
            Assert.assertEquals(0, exitCode);
            Assert.assertTrue(cancelled.await(1, TimeUnit.SECONDS));
            Assert.assertTrue(rendered.contains("Conversation interrupted by user."));
            Assert.assertFalse(rendered.contains("slow chat stream completed"));
            Assert.assertEquals(2, handler.getReadLineCalls());
        } finally {
            executor.shutdownNow();
            terminalIO.close();
            shellContext.close();
            restoreProperty("ai4j.tui.main-buffer", previous);
        }
    }

    private void seedStoredSession(Path workspace,
                                   String sessionId,
                                   String processId,
                                   String previewLogs) throws Exception {
        Path sessionsDir = Files.createDirectories(workspace.resolve(".ai4j").resolve("sessions"));
        StoredCodingSession storedSession = StoredCodingSession.builder()
                .sessionId(sessionId)
                .rootSessionId(sessionId)
                .provider("zhipu")
                .protocol("chat")
                .model("fake-model")
                .workspace(workspace.toString())
                .summary("seeded session")
                .memoryItemCount(1)
                .processCount(1)
                .activeProcessCount(0)
                .restoredProcessCount(1)
                .createdAtEpochMs(System.currentTimeMillis())
                .updatedAtEpochMs(System.currentTimeMillis())
                .state(CodingSessionState.builder()
                        .sessionId(sessionId)
                        .workspaceRoot(workspace.toString())
                        .processCount(1)
                        .processSnapshots(Collections.singletonList(StoredProcessSnapshot.builder()
                                .processId(processId)
                                .command("npm run dev")
                                .workingDirectory(workspace.toString())
                                .status(BashProcessStatus.STOPPED)
                                .startedAt(System.currentTimeMillis())
                                .endedAt(System.currentTimeMillis())
                                .lastLogOffset(previewLogs.length())
                                .lastLogPreview(previewLogs)
                                .restored(false)
                                .controlAvailable(true)
                                .build()))
                        .build())
                .build();
        Files.write(
                sessionsDir.resolve(sessionId + ".json"),
                JSON.toJSONString(storedSession).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static final class FakeCodingCliAgentFactory implements CodingCliAgentFactory {

        @Override
        public PreparedCodingAgent prepare(CodeCommandOptions options) {
            return prepare(options, null);
        }

        @Override
        public PreparedCodingAgent prepare(CodeCommandOptions options, TerminalIO terminal) {
            return prepare(options, terminal, null);
        }

        @Override
        public PreparedCodingAgent prepare(CodeCommandOptions options,
                                           TerminalIO terminal,
                                           TuiInteractionState interactionState) {
            return new PreparedCodingAgent(
                    CodingAgents.builder()
                            .modelClient(new FakeModelClient())
                            .model(options.getModel())
                            .workspaceContext(WorkspaceContext.builder().rootPath(options.getWorkspace()).build())
                            .agentOptions(AgentOptions.builder().stream(options.isStream()).build())
                            .codingOptions(CodingAgentOptions.builder()
                                    .toolExecutorDecorator(new CliToolApprovalDecorator(options.getApprovalMode(), terminal, interactionState))
                                    .build())
                            .build(),
                    options.getProtocol() == null ? CliProtocol.CHAT : options.getProtocol()
            );
        }
    }

    private static final class CustomModelCodingCliAgentFactory implements CodingCliAgentFactory {

        private final AgentModelClient modelClient;

        private CustomModelCodingCliAgentFactory(AgentModelClient modelClient) {
            this.modelClient = modelClient;
        }

        @Override
        public PreparedCodingAgent prepare(CodeCommandOptions options) {
            return prepare(options, null);
        }

        @Override
        public PreparedCodingAgent prepare(CodeCommandOptions options, TerminalIO terminal) {
            return prepare(options, terminal, null);
        }

        @Override
        public PreparedCodingAgent prepare(CodeCommandOptions options,
                                           TerminalIO terminal,
                                           TuiInteractionState interactionState) {
            return new PreparedCodingAgent(
                    CodingAgents.builder()
                            .modelClient(modelClient)
                            .model(options.getModel())
                            .workspaceContext(WorkspaceContext.builder().rootPath(options.getWorkspace()).build())
                            .agentOptions(AgentOptions.builder().stream(options.isStream()).build())
                            .codingOptions(CodingAgentOptions.builder()
                                    .toolExecutorDecorator(new CliToolApprovalDecorator(options.getApprovalMode(), terminal, interactionState))
                                    .build())
                            .build(),
                    options.getProtocol() == null ? CliProtocol.CHAT : options.getProtocol()
            );
        }
    }

    private static final class ConfiguredRuntimeCodingCliAgentFactory implements CodingCliAgentFactory {

        @Override
        public PreparedCodingAgent prepare(CodeCommandOptions options) throws Exception {
            return prepare(options, null);
        }

        @Override
        public PreparedCodingAgent prepare(CodeCommandOptions options, TerminalIO terminal) throws Exception {
            return prepare(options, terminal, null);
        }

        @Override
        public PreparedCodingAgent prepare(CodeCommandOptions options,
                                           TerminalIO terminal,
                                           TuiInteractionState interactionState) throws Exception {
            CliMcpRuntimeManager runtimeManager = CliMcpRuntimeManager.initialize(
                    java.nio.file.Paths.get(options.getWorkspace()),
                    Collections.<String>emptySet()
            );
            return new PreparedCodingAgent(
                    CodingAgents.builder()
                            .modelClient(new FakeModelClient())
                            .model(options.getModel())
                            .workspaceContext(WorkspaceContext.builder().rootPath(options.getWorkspace()).build())
                            .agentOptions(AgentOptions.builder().stream(options.isStream()).build())
                            .codingOptions(CodingAgentOptions.builder()
                                    .toolExecutorDecorator(new CliToolApprovalDecorator(options.getApprovalMode(), terminal, interactionState))
                                    .build())
                            .build(),
                    options.getProtocol() == null ? CliProtocol.CHAT : options.getProtocol(),
                    runtimeManager
            );
        }
    }

    private static final class BlockingChatService implements IChatService {

        private final CountDownLatch cancelled;

        private BlockingChatService(CountDownLatch cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void chatCompletionStream(String baseUrl,
                                         String apiKey,
                                         ChatCompletion chatCompletion,
                                         SseListener eventSourceListener) throws Exception {
            eventSourceListener.onOpen(new EventSource() {
                @Override
                public Request request() {
                    return new Request.Builder().url("http://localhost/test").build();
                }

                @Override
                public void cancel() {
                    cancelled.countDown();
                }
            }, new okhttp3.Response.Builder()
                    .request(new Request.Builder().url("http://localhost/test").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .build());

            if (!eventSourceListener.getCountDownLatch().await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("stream was not released");
            }
        }

        @Override
        public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) throws Exception {
            chatCompletionStream(null, null, chatCompletion, eventSourceListener);
        }
    }

    private static final class RecordingTuiFactory implements CodingCliTuiFactory {

        private boolean rendered;

        @Override
        public CodingCliTuiSupport create(CodeCommandOptions options,
                                          TerminalIO terminal,
                                          TuiConfigManager configManager) {
            TuiConfig config = new TuiConfig();
            config.setUseAlternateScreen(false);
            TuiTheme theme = new TuiTheme();
            TuiRenderer renderer = new TuiRenderer() {
                @Override
                public int getMaxEvents() {
                    return 10;
                }

                @Override
                public String getThemeName() {
                    return "recording";
                }

                @Override
                public void updateTheme(TuiConfig config, TuiTheme theme) {
                }

                @Override
                public String render(TuiScreenModel screenModel) {
                    rendered = true;
                    return "CUSTOM-TUI model="
                            + (screenModel == null || screenModel.getRenderContext() == null
                            ? ""
                            : screenModel.getRenderContext().getModel());
                }
            };
            TuiRuntime runtime = new TuiRuntime() {
                @Override
                public boolean supportsRawInput() {
                    return false;
                }

                @Override
                public void enter() {
                }

                @Override
                public void exit() {
                }

                @Override
                public io.github.lnyocly.ai4j.tui.TuiKeyStroke readKeyStroke(long timeoutMs) {
                    return null;
                }

                @Override
                public void render(TuiScreenModel screenModel) {
                    terminal.println(renderer.render(screenModel));
                }
            };
            return new CodingCliTuiSupport(config, theme, renderer, runtime);
        }
    }

    private static final class AppendOnlyPromptTuiFactory implements CodingCliTuiFactory {

        @Override
        public CodingCliTuiSupport create(CodeCommandOptions options,
                                          TerminalIO terminal,
                                          TuiConfigManager configManager) {
            TuiConfig config = new TuiConfig();
            config.setUseAlternateScreen(false);
            TuiTheme theme = new TuiTheme();
            TuiRenderer renderer = new TuiSessionView(config, theme, terminal != null && terminal.supportsAnsi());
            TuiRuntime runtime = new AppendOnlyTuiRuntime(terminal);
            return new CodingCliTuiSupport(config, theme, renderer, runtime);
        }
    }

    private static final class RawInteractiveTuiFactory implements CodingCliTuiFactory {

        private final Deque<TuiKeyStroke> keys;

        private RawInteractiveTuiFactory(List<TuiKeyStroke> keys) {
            this.keys = new ArrayDeque<TuiKeyStroke>(keys == null ? Collections.<TuiKeyStroke>emptyList() : keys);
        }

        @Override
        public CodingCliTuiSupport create(CodeCommandOptions options,
                                          TerminalIO terminal,
                                          TuiConfigManager configManager) {
            TuiConfig config = new TuiConfig();
            config.setUseAlternateScreen(false);
            TuiTheme theme = new TuiTheme();
            TuiRenderer renderer = new TuiRenderer() {
                @Override
                public int getMaxEvents() {
                    return 10;
                }

                @Override
                public String getThemeName() {
                    return "raw-interactive";
                }

                @Override
                public void updateTheme(TuiConfig config, TuiTheme theme) {
                }

                @Override
                public String render(TuiScreenModel screenModel) {
                    return "RAW-TUI";
                }
            };
            TuiRuntime runtime = new TuiRuntime() {
                @Override
                public boolean supportsRawInput() {
                    return true;
                }

                @Override
                public void enter() {
                }

                @Override
                public void exit() {
                }

                @Override
                public TuiKeyStroke readKeyStroke(long timeoutMs) {
                    return keys.isEmpty() ? null : keys.removeFirst();
                }

                @Override
                public void render(TuiScreenModel screenModel) {
                    terminal.println(renderer.render(screenModel));
                }
            };
            return new CodingCliTuiSupport(config, theme, renderer, runtime);
        }
    }

    private static class RecordingInteractiveTerminal implements TerminalIO {

        private final ByteArrayOutputStream out;
        private final ByteArrayOutputStream err;
        private int readLineCalls;

        private RecordingInteractiveTerminal(ByteArrayOutputStream out, ByteArrayOutputStream err) {
            this.out = out;
            this.err = err;
        }

        @Override
        public String readLine(String prompt) throws IOException {
            readLineCalls++;
            return "/exit";
        }

        @Override
        public void print(String message) {
            write(out, message);
        }

        @Override
        public void println(String message) {
            write(out, (message == null ? "" : message) + System.lineSeparator());
        }

        @Override
        public void errorln(String message) {
            write(err, (message == null ? "" : message) + System.lineSeparator());
        }

        @Override
        public boolean supportsAnsi() {
            return true;
        }

        @Override
        public boolean supportsRawInput() {
            return true;
        }

        protected int getReadLineCalls() {
            return readLineCalls;
        }

        private void write(ByteArrayOutputStream stream, String text) {
            byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
            stream.write(bytes, 0, bytes.length);
        }
    }

    private static final class KeyedRecordingInteractiveTerminal extends RecordingInteractiveTerminal {

        private final Deque<TuiKeyStroke> keys;

        private KeyedRecordingInteractiveTerminal(ByteArrayOutputStream out,
                                                  ByteArrayOutputStream err,
                                                  List<TuiKeyStroke> keys) {
            super(out, err);
            this.keys = new ArrayDeque<TuiKeyStroke>(keys == null ? Collections.<TuiKeyStroke>emptyList() : keys);
        }

        @Override
        public TuiKeyStroke readKeyStroke(long timeoutMs) {
            return keys.isEmpty() ? null : keys.removeFirst();
        }

        @Override
        public boolean isInputClosed() {
            return keys.isEmpty();
        }
    }

    private static final class ScriptedInteractiveTerminal extends RecordingInteractiveTerminal {

        private final Deque<String> lines;

        private ScriptedInteractiveTerminal(ByteArrayOutputStream out,
                                            ByteArrayOutputStream err,
                                            List<String> lines) {
            super(out, err);
            this.lines = new ArrayDeque<String>(lines == null ? Collections.<String>emptyList() : lines);
        }

        @Override
        public synchronized String readLine(String prompt) throws IOException {
            super.readLine(prompt);
            return lines.isEmpty() ? null : lines.removeFirst();
        }
    }

    private JlineShellContext newJlineShellContext(Terminal terminal, LineReader lineReader) throws Exception {
        Constructor<JlineShellContext> constructor = JlineShellContext.class
                .getDeclaredConstructor(Terminal.class, LineReader.class, org.jline.utils.Status.class);
        constructor.setAccessible(true);
        return constructor.newInstance(terminal, lineReader, null);
    }

    private void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private Object readPrivateField(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private Object invokePrivateMethod(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static final class ScriptedLineReaderHandler implements InvocationHandler {

        private final Deque<String> scriptedLines;
        private int readLineCalls;

        private ScriptedLineReaderHandler(List<String> scriptedLines) {
            this.scriptedLines = new ArrayDeque<String>(scriptedLines == null
                    ? Collections.<String>emptyList()
                    : scriptedLines);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("readLine".equals(name)) {
                readLineCalls++;
                return scriptedLines.isEmpty() ? null : scriptedLines.removeFirst();
            }
            if ("isReading".equals(name)) {
                return Boolean.FALSE;
            }
            if ("callWidget".equals(name)) {
                return Boolean.TRUE;
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == (args == null || args.length == 0 ? null : args[0]);
            }
            if ("toString".equals(name)) {
                return "ScriptedLineReader";
            }
            return defaultValue(method.getReturnType());
        }

        private int getReadLineCalls() {
            return readLineCalls;
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType == null || Void.TYPE.equals(returnType)) {
                return null;
            }
            if (Boolean.TYPE.equals(returnType)) {
                return Boolean.FALSE;
            }
            if (Character.TYPE.equals(returnType)) {
                return Character.valueOf('\0');
            }
            if (Byte.TYPE.equals(returnType)) {
                return Byte.valueOf((byte) 0);
            }
            if (Short.TYPE.equals(returnType)) {
                return Short.valueOf((short) 0);
            }
            if (Integer.TYPE.equals(returnType)) {
                return Integer.valueOf(0);
            }
            if (Long.TYPE.equals(returnType)) {
                return Long.valueOf(0L);
            }
            if (Float.TYPE.equals(returnType)) {
                return Float.valueOf(0F);
            }
            if (Double.TYPE.equals(returnType)) {
                return Double.valueOf(0D);
            }
            return null;
        }
    }

    private static final class FakeModelClient implements AgentModelClient {

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            if (prompt != null && prompt.getSystemPrompt() != null
                    && prompt.getSystemPrompt().contains("context checkpoint summaries")) {
                return AgentModelResult.builder()
                        .outputText("## Goal\nContinue the CLI session.\n"
                                + "## Constraints & Preferences\n- Preserve workspace details.\n"
                                + "## Progress\n### Done\n- [x] Session was compacted.\n"
                                + "### In Progress\n- [ ] Continue from the latest context.\n"
                                + "### Blocked\n- (none)\n"
                                + "## Key Decisions\n- **Compaction**: Older messages were summarized.\n"
                                + "## Next Steps\n1. Continue the requested coding work.\n"
                                + "## Critical Context\n- Keep using the saved session state.")
                        .build();
            }
            String toolOutput = findLastToolOutput(prompt);
            if (toolOutput != null) {
                if (toolOutput.startsWith("TOOL_ERROR:")) {
                    JSONObject error = parseObject(toolOutput.substring("TOOL_ERROR:".length()).trim());
                    String message = error == null ? toolOutput : firstNonBlank(error.getString("error"), toolOutput);
                    return AgentModelResult.builder().outputText("Tool error: " + message).build();
                }
                JSONObject json = parseObject(toolOutput);
                String content = json == null ? "" : firstNonBlank(json.getString("content"), json.getString("stdout"));
                return AgentModelResult.builder().outputText("Read result: " + content).build();
            }

            String userInput = findLastUserText(prompt);
            if (userInput != null && userInput.toLowerCase().contains("what do you remember")) {
                return AgentModelResult.builder().outputText("history: " + findAllUserText(prompt)).build();
            }
            if (userInput != null && userInput.toLowerCase().contains("reason first")) {
                return AgentModelResult.builder()
                        .reasoningText("Need to inspect the request first.")
                        .outputText("Done reasoning.")
                        .build();
            }
            if (userInput != null && userInput.toLowerCase().contains("sparse reasoning")) {
                return AgentModelResult.builder()
                        .reasoningText("First line.\n\n\n\nSecond line.")
                        .outputText("Done sparse reasoning.")
                        .build();
            }
            if (userInput != null && userInput.toLowerCase().contains("slow hello")) {
                return AgentModelResult.builder().outputText("Slow hello done.").build();
            }
            if (userInput != null && userInput.toLowerCase().contains("chunked hello")) {
                return AgentModelResult.builder().outputText("Hello world from stream.").build();
            }
            if (userInput != null && userInput.toLowerCase().contains("show code block")) {
                return AgentModelResult.builder()
                        .outputText("Here is a code sample:\n```java\nSystem.out.println(\"hi\");\n```\nDone.")
                        .build();
            }
            if (userInput != null && userInput.toLowerCase().contains("show split fence")) {
                return AgentModelResult.builder()
                        .outputText("Here is python:\n```python\nprint(\"Hello, World!\")\n```\nDone.")
                        .build();
            }
            if (userInput != null && userInput.toLowerCase().contains("rewrite final markdown")) {
                return AgentModelResult.builder()
                        .outputText("已经为你创建了 `hello.py` 文件并成功运行！\n\n```python\nprint(\"Hello, World!\")\n```\n\n运行结果：\nHello, World!")
                        .build();
            }
            if (userInput != null && userInput.toLowerCase().contains("final adds code block")) {
                return AgentModelResult.builder()
                        .outputText("已经为你创建了一个Python的Hello World程序！文件名为 hello_world.py。\n\n你可以通过以下命令运行它：\n\n```bash\npython hello_world.py\n```\n\n这个程序会输出：`Hello, World!`")
                        .build();
            }
            if (userInput != null && userInput.toLowerCase().contains("record request mode")) {
                return AgentModelResult.builder().outputText("mode=create").build();
            }
            if (userInput != null && userInput.toLowerCase().contains("run bash")) {
                return AgentModelResult.builder()
                        .toolCalls(Collections.singletonList(AgentToolCall.builder()
                                .callId("call-bash")
                                .name("bash")
                                .arguments("{\"action\":\"exec\",\"command\":\"type sample.txt\"}")
                                .build()))
                        .build();
            }
            if (userInput != null && userInput.toLowerCase().contains("run invalid patch")) {
                return AgentModelResult.builder()
                        .toolCalls(Collections.singletonList(AgentToolCall.builder()
                                .callId("call-invalid-patch")
                                .name("apply_patch")
                                .arguments("{\"patch\":\"*** Begin Patch\\n*** Unknown: calculator.py\\n*** End Patch\"}")
                                .build()))
                        .build();
            }
            if (userInput != null && userInput.toLowerCase().contains("run recoverable patch")) {
                return AgentModelResult.builder()
                        .toolCalls(Collections.singletonList(AgentToolCall.builder()
                                .callId("call-recoverable-patch")
                                .name("apply_patch")
                                .arguments("{\"patch\":\"*** Begin Patch\\n*** Update File:/sample.txt\\n@@\\n-value=1\\n+value=2\\n*** End Patch\"}")
                                .build()))
                        .build();
            }
            if (userInput != null && userInput.toLowerCase().contains("run unified diff patch")) {
                return AgentModelResult.builder()
                        .toolCalls(Collections.singletonList(AgentToolCall.builder()
                                .callId("call-unified-diff-patch")
                                .name("apply_patch")
                                .arguments("{\"patch\":\"*** Begin Patch\\n*** Update File:/sample.txt\\n--- a/sample.txt\\n+++ b/sample.txt\\n@@\\n-value=1\\n+value=2\\n*** End Patch\"}")
                                .build()))
                        .build();
            }
            if (userInput != null && userInput.toLowerCase().contains("run invalid bash")) {
                return AgentModelResult.builder()
                        .reasoningText("Need to inspect the shell call.")
                        .toolCalls(Collections.singletonList(AgentToolCall.builder()
                                .callId("call-invalid-bash")
                                .name("bash")
                                .arguments("{\"action\":\"exec\"}")
                                .build()))
                        .build();
            }
            if (userInput != null && userInput.toLowerCase().contains("sample")) {
                return AgentModelResult.builder()
                        .toolCalls(Collections.singletonList(AgentToolCall.builder()
                                .callId("call-read-file")
                                .name("read_file")
                                .arguments("{\"path\":\"sample.txt\"}")
                                .build()))
                        .build();
            }
            return AgentModelResult.builder().outputText("Echo: " + userInput).build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            String userInput = findLastUserText(prompt);
            if (userInput != null && userInput.toLowerCase().contains("slow hello")) {
                AgentModelResult result = AgentModelResult.builder().outputText("Slow hello done.").build();
                if (listener != null) {
                    try {
                        Thread.sleep(450L);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return result;
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        return result;
                    }
                    listener.onDeltaText("Slow hello done.");
                    listener.onComplete(result);
                }
                return result;
            }
            if (userInput != null && userInput.toLowerCase().contains("chunked hello")) {
                AgentModelResult result = AgentModelResult.builder().outputText("Hello world from stream.").build();
                if (listener != null) {
                    listener.onDeltaText("Hello ");
                    listener.onDeltaText("world ");
                    listener.onDeltaText("from stream.");
                    listener.onComplete(result);
                }
                return result;
            }
            if (userInput != null && userInput.toLowerCase().contains("record request mode")) {
                AgentModelResult result = AgentModelResult.builder().outputText("mode=createStream").build();
                if (listener != null) {
                    listener.onDeltaText("mode=createStream");
                    listener.onComplete(result);
                }
                return result;
            }
            if (userInput != null && userInput.toLowerCase().contains("show code block")) {
                String codeBlock = "Here is a code sample:\n```java\nSystem.out.println(\"hi\");\n```\nDone.";
                AgentModelResult result = AgentModelResult.builder().outputText(codeBlock).build();
                if (listener != null) {
                    listener.onDeltaText("Here is a code sample:\n");
                    listener.onDeltaText("```");
                    listener.onDeltaText("java\nSystem.out.println(\"hi\");\n");
                    listener.onDeltaText("```\nDone.");
                    listener.onComplete(result);
                }
                return result;
            }
            if (userInput != null && userInput.toLowerCase().contains("show split fence")) {
                String codeBlock = "Here is python:\n```python\nprint(\"Hello, World!\")\n```\nDone.";
                AgentModelResult result = AgentModelResult.builder().outputText(codeBlock).build();
                if (listener != null) {
                    listener.onDeltaText("Here is python:\n``");
                    listener.onDeltaText("`python\nprint(\"Hello, World!\")\n");
                    listener.onDeltaText("```\nDone.");
                    listener.onComplete(result);
                }
                return result;
            }
            if (userInput != null && userInput.toLowerCase().contains("rewrite final markdown")) {
                String rewritten = "已经为你创建了 `hello.py` 文件并成功运行！\n\n```python\nprint(\"Hello, World!\")\n```\n\n运行结果：\nHello, World!";
                AgentModelResult result = AgentModelResult.builder().outputText(rewritten).build();
                if (listener != null) {
                    listener.onDeltaText("已经为你创建了 hello.py 文件并成功运行！\n\n");
                    listener.onDeltaText("```python\nprint(\"Hello, World!\")\n```\n");
                    listener.onDeltaText("\n运行结果：\nHello, World!");
                    listener.onComplete(result);
                }
                return result;
            }
            if (userInput != null && userInput.toLowerCase().contains("final adds code block")) {
                String finalText = "已经为你创建了一个Python的Hello World程序！文件名为 hello_world.py。\n\n你可以通过以下命令运行它：\n\n```bash\npython hello_world.py\n```\n\n这个程序会输出：`Hello, World!`";
                AgentModelResult result = AgentModelResult.builder().outputText(finalText).build();
                if (listener != null) {
                    listener.onDeltaText("已经为你创建了一个Python的Hello World程序！文件名为 hello_world.py。\n\n");
                    listener.onDeltaText("你可以通过以下命令运行它：\n\n");
                    listener.onDeltaText("\n\n这个程序会输出：Hello, World!");
                    listener.onComplete(result);
                }
                return result;
            }
            if (userInput != null && userInput.toLowerCase().contains("stream auth fail")) {
                AgentModelResult result = AgentModelResult.builder().outputText("").build();
                if (listener != null) {
                    listener.onError(new RuntimeException("Invalid API key"));
                    listener.onComplete(result);
                }
                return result;
            }
            AgentModelResult result = create(prompt);
            if (listener != null) {
                if (result.getReasoningText() != null && !result.getReasoningText().isEmpty()) {
                    listener.onReasoningDelta(result.getReasoningText());
                }
                if (result.getOutputText() != null && !result.getOutputText().isEmpty()) {
                    listener.onDeltaText(result.getOutputText());
                }
                if (result.getToolCalls() != null) {
                    for (AgentToolCall call : result.getToolCalls()) {
                        listener.onToolCall(call);
                    }
                }
                listener.onComplete(result);
            }
            return result;
        }

        private String findLastUserText(AgentPrompt prompt) {
            if (prompt == null || prompt.getItems() == null) {
                return "";
            }
            List<Object> items = prompt.getItems();
            for (int i = items.size() - 1; i >= 0; i--) {
                Object item = items.get(i);
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<?, ?> map = (Map<?, ?>) item;
                if (!"message".equals(map.get("type")) || !"user".equals(map.get("role"))) {
                    continue;
                }
                Object content = map.get("content");
                if (!(content instanceof List)) {
                    continue;
                }
                List<?> parts = (List<?>) content;
                for (Object part : parts) {
                    if (!(part instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> partMap = (Map<?, ?>) part;
                    if ("input_text".equals(partMap.get("type"))) {
                        Object text = partMap.get("text");
                        return text == null ? "" : String.valueOf(text);
                    }
                }
            }
            return "";
        }

        private String findAllUserText(AgentPrompt prompt) {
            if (prompt == null || prompt.getItems() == null) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            List<Object> items = prompt.getItems();
            for (Object item : items) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<?, ?> map = (Map<?, ?>) item;
                if (!"message".equals(map.get("type")) || !"user".equals(map.get("role"))) {
                    continue;
                }
                Object content = map.get("content");
                if (!(content instanceof List)) {
                    continue;
                }
                List<?> parts = (List<?>) content;
                for (Object part : parts) {
                    if (!(part instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> partMap = (Map<?, ?>) part;
                    if (!"input_text".equals(partMap.get("type"))) {
                        continue;
                    }
                    Object text = partMap.get("text");
                    if (text == null) {
                        continue;
                    }
                    String value = String.valueOf(text);
                    if (builder.length() > 0) {
                        builder.append(" | ");
                    }
                    builder.append(value);
                }
            }
            return builder.toString();
        }

        private String findLastToolOutput(AgentPrompt prompt) {
            if (prompt == null || prompt.getItems() == null) {
                return null;
            }
            List<Object> items = prompt.getItems();
            for (int i = items.size() - 1; i >= 0; i--) {
                Object item = items.get(i);
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<?, ?> map = (Map<?, ?>) item;
                if ("function_call_output".equals(map.get("type"))) {
                    Object output = map.get("output");
                    return output == null ? null : String.valueOf(output);
                }
            }
            return null;
        }

        private JSONObject parseObject(String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            try {
                return JSON.parseObject(raw);
            } catch (Exception ignored) {
                return null;
            }
        }

        private String firstNonBlank(String... values) {
            if (values == null) {
                return "";
            }
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
            return "";
        }
    }

    private static void seedPersistedTeamState(Path workspace, String teamId) throws Exception {
        Path teamRoot = workspace.resolve(".ai4j").resolve("teams");
        long now = System.currentTimeMillis();

        AgentTeamTask backendTask = AgentTeamTask.builder()
                .id("backend")
                .memberId("backend")
                .task("Define travel destination API")
                .build();
        AgentTeamTaskState backendState = AgentTeamTaskState.builder()
                .taskId("backend")
                .task(backendTask)
                .status(AgentTeamTaskStatus.IN_PROGRESS)
                .claimedBy("backend")
                .phase("running")
                .detail("Drafting OpenAPI paths and payloads.")
                .percent(Integer.valueOf(40))
                .heartbeatCount(2)
                .updatedAtEpochMs(now)
                .build();

        AgentTeamTask qaTask = AgentTeamTask.builder()
                .id("qa")
                .memberId("qa")
                .task("Prepare verification checklist")
                .build();
        AgentTeamTaskState qaState = AgentTeamTaskState.builder()
                .taskId("qa")
                .task(qaTask)
                .status(AgentTeamTaskStatus.PENDING)
                .phase("planned")
                .percent(Integer.valueOf(0))
                .updatedAtEpochMs(now - 1000L)
                .build();

        AgentTeamMessage message = AgentTeamMessage.builder()
                .id("msg-1")
                .fromMemberId("architect")
                .toMemberId("backend")
                .type("contract.note")
                .taskId("backend")
                .content("Define the backend contract first.")
                .createdAt(now)
                .build();

        AgentTeamState state = AgentTeamState.builder()
                .teamId(teamId)
                .objective("Deliver a travel planner demo.")
                .members(Arrays.asList(
                        AgentTeamMemberSnapshot.builder().id("architect").name("Architect").description("System design").build(),
                        AgentTeamMemberSnapshot.builder().id("backend").name("Backend").description("API implementation").build(),
                        AgentTeamMemberSnapshot.builder().id("qa").name("QA").description("Verification").build()
                ))
                .taskStates(Arrays.asList(backendState, qaState))
                .messages(Arrays.asList(message))
                .lastOutput("Architecture and backend work are in progress.")
                .lastRounds(3)
                .lastRunStartedAt(now - 5000L)
                .lastRunCompletedAt(0L)
                .updatedAt(now)
                .runActive(true)
                .build();

        new FileAgentTeamStateStore(teamRoot.resolve("state")).save(state);
        new FileAgentTeamMessageBus(teamRoot.resolve("mailbox").resolve(teamId + ".jsonl"))
                .restore(Arrays.asList(message));
    }

    private static int countSpinnerFrames(String output) {
        int matches = 0;
        String[] frames = new String[]{"- thinking...", "\\ thinking...", "| thinking...", "/ thinking..."};
        for (String frame : frames) {
            if (output.contains(frame)) {
                matches++;
            }
        }
        return matches;
    }

    private static String stripAnsi(String value) {
        return value == null ? "" : value.replaceAll("\\u001B\\[[0-?]*[ -/]*[@-~]", "");
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

    private static void restoreUserHome(String value) {
        if (value == null) {
            System.clearProperty("user.home");
        } else {
            System.setProperty("user.home", value);
        }
    }
}
