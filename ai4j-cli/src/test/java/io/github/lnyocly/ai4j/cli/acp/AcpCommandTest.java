package io.github.lnyocly.ai4j.cli.acp;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentDefinition;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMember;
import io.github.lnyocly.ai4j.agent.team.AgentTeamPlan;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTask;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.cli.ApprovalMode;
import io.github.lnyocly.ai4j.cli.CliJson;
import io.github.lnyocly.ai4j.cli.CliProtocol;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptionsParser;
import io.github.lnyocly.ai4j.cli.factory.CodingCliAgentFactory;
import io.github.lnyocly.ai4j.cli.runtime.CodingTaskSessionEventBridge;
import io.github.lnyocly.ai4j.cli.session.DefaultCodingSessionManager;
import io.github.lnyocly.ai4j.cli.session.FileCodingSessionStore;
import io.github.lnyocly.ai4j.cli.session.FileSessionEventStore;
import io.github.lnyocly.ai4j.cli.session.StoredCodingSession;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.CodingAgents;
import io.github.lnyocly.ai4j.coding.CodingSessionState;
import io.github.lnyocly.ai4j.coding.definition.CodingSessionMode;
import io.github.lnyocly.ai4j.coding.session.CodingSessionLink;
import io.github.lnyocly.ai4j.coding.task.CodingTask;
import io.github.lnyocly.ai4j.coding.task.CodingTaskProgress;
import io.github.lnyocly.ai4j.coding.task.CodingTaskStatus;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AcpCommandTest {

    @Test
    public void test_initialize_new_prompt_and_load_history() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-main");
        CodeCommandOptions options = parseOptions(workspace, "--model", "fake-model");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String input = line(request(1, "initialize", params("protocolVersion", 1)))
                + line(request(2, "session/new", params(
                "sessionId", "acp-session",
                "cwd", workspace.toString()
        )))
                + line(request(3, "session/prompt", params(
                "sessionId", "acp-session",
                "prompt", textPrompt("hello from acp")
        )));

        int exitCode = new AcpJsonRpcServer(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                out,
                err,
                options,
                provider()
        ).run();

        Assert.assertEquals(0, exitCode);

        List<ObjectNode> messages = parseLines(out);
        Assert.assertTrue(hasResponse(messages, 1));
        Assert.assertTrue(hasResponse(messages, 2));
        Assert.assertTrue(hasResponse(messages, 3));
        ObjectNode newSessionResult = responseResult(messages, 2);
        Assert.assertEquals("acp-session", CliJson.getString(newSessionResult, "sessionId"));
        Assert.assertTrue(containsConfigOption(CliJson.getArray(newSessionResult, "configOptions"), "mode"));
        Assert.assertTrue(containsConfigOption(CliJson.getArray(newSessionResult, "configOptions"), "model"));
        Assert.assertEquals("safe", CliJson.getString(CliJson.getObject(newSessionResult, "modes"), "currentModeId"));
        ObjectNode availableCommands = findSessionUpdate(messages, "available_commands_update");
        Assert.assertNotNull(availableCommands);
        ArrayNode available = CliJson.getArray(availableCommands, "availableCommands");
        Assert.assertNotNull(available);
        Assert.assertFalse(available.isEmpty());
        Assert.assertTrue(containsAvailableCommand(available, "status"));
        Assert.assertTrue(containsAvailableCommand(available, "team"));
        Assert.assertTrue(containsAvailableCommand(available, "mcp"));
        Assert.assertTrue(hasSessionUpdate(messages, "user_message_chunk", "hello from acp"));
        Assert.assertTrue(hasSessionUpdate(messages, "agent_message_chunk", "Echo: hello from acp"));
        Assert.assertEquals("end_turn", CliJson.getString(responseResult(messages, 3), "stopReason"));

        ByteArrayOutputStream loadOut = new ByteArrayOutputStream();
        ByteArrayOutputStream loadErr = new ByteArrayOutputStream();
        String loadInput = line(request(1, "initialize", params("protocolVersion", 1)))
                + line(request(2, "session/list", params("cwd", workspace.toString())))
                + line(request(3, "session/load", params(
                "sessionId", "acp-session",
                "cwd", workspace.toString()
        )));

        int loadExit = new AcpJsonRpcServer(
                new ByteArrayInputStream(loadInput.getBytes(StandardCharsets.UTF_8)),
                loadOut,
                loadErr,
                options,
                provider()
        ).run();

        Assert.assertEquals(0, loadExit);
        List<ObjectNode> loadMessages = parseLines(loadOut);
        Assert.assertEquals("acp-session", CliJson.getString(responseResult(loadMessages, 3), "sessionId"));
        Assert.assertTrue(containsConfigOption(CliJson.getArray(responseResult(loadMessages, 3), "configOptions"), "mode"));
        Assert.assertTrue(containsConfigOption(CliJson.getArray(responseResult(loadMessages, 3), "configOptions"), "model"));
        ArrayNode sessions = CliJson.getArray(responseResult(loadMessages, 2), "sessions");
        Assert.assertNotNull(sessions);
        Assert.assertFalse(sessions.isEmpty());
        ObjectNode loadAvailableCommands = findSessionUpdate(loadMessages, "available_commands_update");
        Assert.assertNotNull(loadAvailableCommands);
        Assert.assertTrue(containsAvailableCommand(CliJson.getArray(loadAvailableCommands, "availableCommands"), "status"));
        Assert.assertTrue(hasSessionUpdate(loadMessages, "user_message_chunk", "hello from acp"));
        Assert.assertTrue(hasSessionUpdate(loadMessages, "agent_message_chunk", "Echo: hello from acp"));
    }

    @Test
    public void test_slash_command_prompt_is_handled_without_model_round_trip() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-slash");
        CodeCommandOptions options = parseOptions(workspace, "--model", "fake-model");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String input = line(request(1, "initialize", params("protocolVersion", 1)))
                + line(request(2, "session/new", params(
                "sessionId", "slash-session",
                "cwd", workspace.toString()
        )))
                + line(request(3, "session/prompt", params(
                "sessionId", "slash-session",
                "prompt", textPrompt("/status")
        )));

        int exitCode = new AcpJsonRpcServer(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                out,
                err,
                options,
                provider()
        ).run();

        Assert.assertEquals(0, exitCode);

        List<ObjectNode> messages = parseLines(out);
        Assert.assertEquals("end_turn", CliJson.getString(responseResult(messages, 3), "stopReason"));
        Assert.assertTrue(hasSessionUpdate(messages, "user_message_chunk", "/status"));
        Assert.assertTrue(containsSessionUpdatePrefix(messages, "agent_message_chunk", "status:"));
        Assert.assertFalse(hasSessionUpdate(messages, "agent_message_chunk", "Echo: /status"));
    }

    @Test
    public void test_provider_and_model_slash_commands_rebind_runtime() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-provider-model");
        Path fakeHome = Files.createTempDirectory("ai4j-acp-provider-home");
        String originalUserHome = System.getProperty("user.home");
        PipedOutputStream clientToServer = new PipedOutputStream();
        PipedInputStream serverInput = new PipedInputStream(clientToServer);
        PipedOutputStream serverToClient = new PipedOutputStream();
        PipedInputStream clientInput = new PipedInputStream(serverToClient);
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        try {
            System.setProperty("user.home", fakeHome.toString());
            CodeCommandOptions options = parseOptions(workspace, "--provider", "openai", "--model", "fake-model");
            final AcpJsonRpcServer server = new AcpJsonRpcServer(
                    serverInput,
                    serverToClient,
                    err,
                    options,
                    runtimeEchoProvider()
            );
            Thread serverThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    server.run();
                }
            });
            serverThread.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput, StandardCharsets.UTF_8));
            List<ObjectNode> messages = new ArrayList<ObjectNode>();

            clientToServer.write(line(request(1, "initialize", params("protocolVersion", 1))).getBytes(StandardCharsets.UTF_8));
            clientToServer.write(line(request(2, "session/new", params(
                    "sessionId", "provider-model-session",
                    "cwd", workspace.toString()
            ))).getBytes(StandardCharsets.UTF_8));
            clientToServer.flush();
            readUntilResponse(reader, messages, 1);
            readUntilResponse(reader, messages, 2);
            readUntilSessionUpdate(reader, messages, "available_commands_update");

            ObjectNode availableCommands = findSessionUpdate(messages, "available_commands_update");
            Assert.assertNotNull(availableCommands);
            ArrayNode available = CliJson.getArray(availableCommands, "availableCommands");
            Assert.assertTrue(containsAvailableCommand(available, "providers"));
            Assert.assertTrue(containsAvailableCommand(available, "provider"));
            Assert.assertTrue(containsAvailableCommand(available, "model"));

            sendPrompt(clientToServer, 3, "provider-model-session",
                    "/provider add zhipu-main --provider zhipu --model glm-4.7 --base-url https://open.bigmodel.cn/api/coding/paas/v4");
            readUntilResponse(reader, messages, 3);

            sendPrompt(clientToServer, 4, "provider-model-session", "/provider use zhipu-main");
            readUntilResponse(reader, messages, 4);

            sendPrompt(clientToServer, 5, "provider-model-session", "/model glm-4.7-plus");
            readUntilResponse(reader, messages, 5);

            sendPrompt(clientToServer, 6, "provider-model-session", "hello from switched runtime");
            readUntilResponse(reader, messages, 6);

            Assert.assertEquals("end_turn", CliJson.getString(responseResult(messages, 6), "stopReason"));
            Assert.assertTrue(hasSessionUpdate(messages, "agent_message_chunk", "Echo[zhipu/glm-4.7-plus]: hello from switched runtime"));

            ObjectNode workspaceConfig = CliJson.parseObject(Files.readAllBytes(workspace.resolve(".ai4j").resolve("workspace.json")));
            Assert.assertEquals("zhipu-main", CliJson.getString(workspaceConfig, "activeProfile"));
            Assert.assertEquals("glm-4.7-plus", CliJson.getString(workspaceConfig, "modelOverride"));

            ObjectNode providersConfig = CliJson.parseObject(Files.readAllBytes(fakeHome.resolve(".ai4j").resolve("providers.json")));
            Assert.assertNotNull(CliJson.getObject(CliJson.getObject(providersConfig, "profiles"), "zhipu-main"));

            clientToServer.close();
            serverThread.join(5000L);
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    public void test_set_config_option_updates_model_and_emits_config_update() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-set-config");
        Path fakeHome = Files.createTempDirectory("ai4j-acp-set-config-home");
        String originalUserHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", fakeHome.toString());
            Files.createDirectories(fakeHome.resolve(".ai4j"));
            Files.write(fakeHome.resolve(".ai4j").resolve("providers.json"),
                    ("{\n" +
                            "  \"profiles\": {\n" +
                            "    \"openai-alt\": {\n" +
                            "      \"provider\": \"openai\",\n" +
                            "      \"model\": \"fake-model-2\"\n" +
                            "    }\n" +
                            "  }\n" +
                            "}").getBytes(StandardCharsets.UTF_8));

            CodeCommandOptions options = parseOptions(workspace, "--provider", "openai", "--model", "fake-model");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            String input = line(request(1, "initialize", params("protocolVersion", 1)))
                    + line(request(2, "session/new", params(
                    "sessionId", "config-session",
                    "cwd", workspace.toString()
            )))
                    + line(request(3, "session/set_config_option", params(
                    "sessionId", "config-session",
                    "configId", "model",
                    "value", "fake-model-2"
            )))
                    + line(request(4, "session/prompt", params(
                    "sessionId", "config-session",
                    "prompt", textPrompt("hello from set_config_option")
            )));

            int exitCode = new AcpJsonRpcServer(
                    new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                    out,
                    err,
                    options,
                    runtimeEchoProvider()
            ).run();

            Assert.assertEquals(0, exitCode);

            List<ObjectNode> messages = parseLines(out);
            Assert.assertTrue(containsConfigOptionValue(CliJson.getArray(responseResult(messages, 3), "configOptions"), "model", "fake-model-2"));
            ObjectNode configUpdate = findSessionUpdate(messages, "config_option_update");
            Assert.assertNotNull(configUpdate);
            Assert.assertTrue(containsConfigOptionValue(CliJson.getArray(configUpdate, "configOptions"), "model", "fake-model-2"));
            Assert.assertTrue(hasSessionUpdate(messages, "agent_message_chunk", "Echo[openai/fake-model-2]: hello from set_config_option"));
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    public void test_set_config_option_mode_rebinds_permission_behavior_for_factory_bound_decorator() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-mode-config-factory-bound");
        PipedOutputStream clientToServer = new PipedOutputStream();
        PipedInputStream serverInput = new PipedInputStream(clientToServer);
        PipedOutputStream serverToClient = new PipedOutputStream();
        PipedInputStream clientInput = new PipedInputStream(serverToClient);
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommandOptions options = parseOptions(workspace, "--model", "fake-model");
        final AcpJsonRpcServer server = new AcpJsonRpcServer(
                serverInput,
                serverToClient,
                err,
                options,
                factoryBoundApprovalProvider()
        );

        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                server.run();
            }
        });
        serverThread.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput, StandardCharsets.UTF_8));
        List<ObjectNode> messages = new ArrayList<ObjectNode>();

        clientToServer.write(line(request(1, "initialize", params("protocolVersion", 1))).getBytes(StandardCharsets.UTF_8));
        clientToServer.write(line(request(2, "session/new", params(
                "sessionId", "factory-bound-mode-session",
                "cwd", workspace.toString()
        ))).getBytes(StandardCharsets.UTF_8));
        clientToServer.flush();
        readUntilResponse(reader, messages, 1);
        readUntilResponse(reader, messages, 2);
        readUntilSessionUpdate(reader, messages, "available_commands_update");

        clientToServer.write(line(request(3, "session/set_config_option", params(
                "sessionId", "factory-bound-mode-session",
                "configId", "mode",
                "value", "manual"
        ))).getBytes(StandardCharsets.UTF_8));
        clientToServer.flush();
        readUntilResponse(reader, messages, 3);
        readUntilSessionUpdate(reader, messages, "current_mode_update");
        readUntilSessionUpdate(reader, messages, "config_option_update");

        sendPrompt(clientToServer, 4, "factory-bound-mode-session", "run bash now");
        boolean sawPermissionRequest = false;
        boolean sawPromptResponse = false;
        for (int i = 0; i < 40; i++) {
            String messageLine = reader.readLine();
            Assert.assertNotNull(messageLine);
            ObjectNode message = CliJson.parseObject(messageLine);
            messages.add(message);
            if ("session/request_permission".equals(CliJson.getString(message, "method"))) {
                sawPermissionRequest = true;
                Object requestId = CliJson.toPlainValue(message.get("id"));
                clientToServer.write(line(response(requestId, params(
                        "outcome", params(
                                "outcome", "selected",
                                "optionId", "allow_once"
                        )
                ))).getBytes(StandardCharsets.UTF_8));
                clientToServer.flush();
                continue;
            }
            if (hasIntId(message, 4)) {
                sawPromptResponse = true;
                break;
            }
        }

        clientToServer.close();
        serverThread.join(5000L);

        Assert.assertTrue(sawPermissionRequest);
        Assert.assertTrue(sawPromptResponse);
    }

    @Test
    public void test_set_config_option_rejects_model_not_present_in_select_options() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-set-config-invalid");
        CodeCommandOptions options = parseOptions(workspace, "--provider", "openai", "--model", "fake-model");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String input = line(request(1, "initialize", params("protocolVersion", 1)))
                + line(request(2, "session/new", params(
                "sessionId", "invalid-config-session",
                "cwd", workspace.toString()
        )))
                + line(request(3, "session/set_config_option", params(
                "sessionId", "invalid-config-session",
                "configId", "model",
                "value", "not-listed-model"
        )));

        int exitCode = new AcpJsonRpcServer(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                out,
                err,
                options,
                runtimeEchoProvider()
        ).run();

        Assert.assertEquals(0, exitCode);

        List<ObjectNode> messages = parseLines(out);
        ObjectNode errorResponse = findError(messages, 3);
        Assert.assertNotNull(errorResponse);
        Assert.assertTrue(CliJson.getString(CliJson.getObject(errorResponse, "error"), "message").contains("Unsupported model"));
    }

    @Test
    public void test_set_mode_switches_permission_behavior_and_emits_mode_updates() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-set-mode");
        PipedOutputStream clientToServer = new PipedOutputStream();
        PipedInputStream serverInput = new PipedInputStream(clientToServer);
        PipedOutputStream serverToClient = new PipedOutputStream();
        PipedInputStream clientInput = new PipedInputStream(serverToClient);
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        CodeCommandOptions options = parseOptions(workspace, "--model", "fake-model");
        final AcpJsonRpcServer server = new AcpJsonRpcServer(
                serverInput,
                serverToClient,
                err,
                options,
                provider()
        );

        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                server.run();
            }
        });
        serverThread.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput, StandardCharsets.UTF_8));
        List<ObjectNode> messages = new ArrayList<ObjectNode>();

        clientToServer.write(line(request(1, "initialize", params("protocolVersion", 1))).getBytes(StandardCharsets.UTF_8));
        clientToServer.write(line(request(2, "session/new", params(
                "sessionId", "mode-session",
                "cwd", workspace.toString()
        ))).getBytes(StandardCharsets.UTF_8));
        clientToServer.flush();
        readUntilResponse(reader, messages, 1);
        readUntilResponse(reader, messages, 2);
        readUntilSessionUpdate(reader, messages, "available_commands_update");

        clientToServer.write(line(request(3, "session/set_mode", params(
                "sessionId", "mode-session",
                "modeId", "manual"
        ))).getBytes(StandardCharsets.UTF_8));
        clientToServer.flush();
        readUntilResponse(reader, messages, 3);
        readUntilSessionUpdate(reader, messages, "current_mode_update");
        readUntilSessionUpdate(reader, messages, "config_option_update");

        clientToServer.write(line(request(4, "session/prompt", params(
                "sessionId", "mode-session",
                "prompt", textPrompt("run bash now")
        ))).getBytes(StandardCharsets.UTF_8));
        clientToServer.flush();

        boolean sawPermissionRequest = false;
        boolean sawPromptResponse = false;
        for (int i = 0; i < 30; i++) {
            String messageLine = reader.readLine();
            Assert.assertNotNull(messageLine);
            ObjectNode message = CliJson.parseObject(messageLine);
            messages.add(message);
            if ("session/request_permission".equals(CliJson.getString(message, "method"))) {
                sawPermissionRequest = true;
                Object requestId = CliJson.toPlainValue(message.get("id"));
                clientToServer.write(line(response(requestId, params(
                        "outcome", params(
                                "outcome", "selected",
                                "optionId", "allow_once"
                        )
                ))).getBytes(StandardCharsets.UTF_8));
                clientToServer.flush();
                continue;
            }
            if (hasIntId(message, 4)) {
                sawPromptResponse = true;
                break;
            }
        }

        clientToServer.close();
        serverThread.join(5000L);

        Assert.assertTrue(sawPermissionRequest);
        Assert.assertTrue(sawPromptResponse);
        ObjectNode currentModeUpdate = findSessionUpdate(messages, "current_mode_update");
        Assert.assertNotNull(currentModeUpdate);
        Assert.assertEquals("manual", CliJson.getString(currentModeUpdate, "currentModeId"));
        ObjectNode configUpdate = findSessionUpdate(messages, "config_option_update");
        Assert.assertNotNull(configUpdate);
        Assert.assertTrue(containsConfigOptionValue(CliJson.getArray(configUpdate, "configOptions"), "mode", "manual"));
    }

    @Test
    public void test_set_mode_during_active_prompt_applies_to_next_turn() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-set-mode-active");
        PipedOutputStream clientToServer = new PipedOutputStream();
        PipedInputStream serverInput = new PipedInputStream(clientToServer);
        PipedOutputStream serverToClient = new PipedOutputStream();
        PipedInputStream clientInput = new PipedInputStream(serverToClient);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CountDownLatch promptStarted = new CountDownLatch(1);
        CountDownLatch releasePrompt = new CountDownLatch(1);

        CodeCommandOptions options = parseOptions(workspace, "--model", "fake-model");
        final AcpJsonRpcServer server = new AcpJsonRpcServer(
                serverInput,
                serverToClient,
                err,
                options,
                blockingProvider(promptStarted, releasePrompt)
        );

        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                server.run();
            }
        });
        serverThread.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput, StandardCharsets.UTF_8));
        List<ObjectNode> messages = new ArrayList<ObjectNode>();

        clientToServer.write(line(request(1, "initialize", params("protocolVersion", 1))).getBytes(StandardCharsets.UTF_8));
        clientToServer.write(line(request(2, "session/new", params(
                "sessionId", "mode-active-session",
                "cwd", workspace.toString()
        ))).getBytes(StandardCharsets.UTF_8));
        clientToServer.flush();
        readUntilResponse(reader, messages, 1);
        readUntilResponse(reader, messages, 2);
        readUntilSessionUpdate(reader, messages, "available_commands_update");

        sendPrompt(clientToServer, 3, "mode-active-session", "hold mode switch");
        Assert.assertTrue(promptStarted.await(5, TimeUnit.SECONDS));

        clientToServer.write(line(request(4, "session/set_mode", params(
                "sessionId", "mode-active-session",
                "modeId", "manual"
        ))).getBytes(StandardCharsets.UTF_8));
        clientToServer.flush();
        readUntilResponse(reader, messages, 4);
        readUntilSessionUpdate(reader, messages, "current_mode_update");
        readUntilSessionUpdate(reader, messages, "config_option_update");

        releasePrompt.countDown();
        readUntilResponse(reader, messages, 3);

        sendPrompt(clientToServer, 5, "mode-active-session", "run bash now");
        boolean sawPermissionRequest = false;
        boolean sawPromptResponse = false;
        for (int i = 0; i < 40; i++) {
            String messageLine = reader.readLine();
            Assert.assertNotNull(messageLine);
            ObjectNode message = CliJson.parseObject(messageLine);
            messages.add(message);
            if ("session/request_permission".equals(CliJson.getString(message, "method"))) {
                sawPermissionRequest = true;
                Object requestId = CliJson.toPlainValue(message.get("id"));
                clientToServer.write(line(response(requestId, params(
                        "outcome", params(
                                "outcome", "selected",
                                "optionId", "allow_once"
                        )
                ))).getBytes(StandardCharsets.UTF_8));
                clientToServer.flush();
                continue;
            }
            if (hasIntId(message, 5)) {
                sawPromptResponse = true;
                break;
            }
        }

        clientToServer.close();
        serverThread.join(5000L);

        Assert.assertTrue(sawPermissionRequest);
        Assert.assertTrue(sawPromptResponse);
        Assert.assertTrue(hasSessionUpdate(messages, "agent_message_chunk", "Echo: hold mode switch"));
    }

    @Test
    public void test_set_config_option_during_active_prompt_applies_to_next_turn() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-set-config-active");
        Path fakeHome = Files.createTempDirectory("ai4j-acp-set-config-active-home");
        String originalUserHome = System.getProperty("user.home");
        PipedOutputStream clientToServer = new PipedOutputStream();
        PipedInputStream serverInput = new PipedInputStream(clientToServer);
        PipedOutputStream serverToClient = new PipedOutputStream();
        PipedInputStream clientInput = new PipedInputStream(serverToClient);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CountDownLatch promptStarted = new CountDownLatch(1);
        CountDownLatch releasePrompt = new CountDownLatch(1);

        try {
            System.setProperty("user.home", fakeHome.toString());
            Files.createDirectories(fakeHome.resolve(".ai4j"));
            Files.write(fakeHome.resolve(".ai4j").resolve("providers.json"),
                    ("{\n" +
                            "  \"profiles\": {\n" +
                            "    \"openai-alt\": {\n" +
                            "      \"provider\": \"openai\",\n" +
                            "      \"model\": \"fake-model-2\"\n" +
                            "    }\n" +
                            "  }\n" +
                            "}").getBytes(StandardCharsets.UTF_8));

            CodeCommandOptions options = parseOptions(workspace, "--provider", "openai", "--model", "fake-model");
            final AcpJsonRpcServer server = new AcpJsonRpcServer(
                    serverInput,
                    serverToClient,
                    err,
                    options,
                    blockingRuntimeEchoProvider(promptStarted, releasePrompt)
            );

            Thread serverThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    server.run();
                }
            });
            serverThread.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput, StandardCharsets.UTF_8));
            List<ObjectNode> messages = new ArrayList<ObjectNode>();

            clientToServer.write(line(request(1, "initialize", params("protocolVersion", 1))).getBytes(StandardCharsets.UTF_8));
            clientToServer.write(line(request(2, "session/new", params(
                    "sessionId", "config-active-session",
                    "cwd", workspace.toString()
            ))).getBytes(StandardCharsets.UTF_8));
            clientToServer.flush();
            readUntilResponse(reader, messages, 1);
            readUntilResponse(reader, messages, 2);
            readUntilSessionUpdate(reader, messages, "available_commands_update");

            sendPrompt(clientToServer, 3, "config-active-session", "hold config switch");
            Assert.assertTrue(promptStarted.await(5, TimeUnit.SECONDS));

            clientToServer.write(line(request(4, "session/set_config_option", params(
                    "sessionId", "config-active-session",
                    "configId", "model",
                    "value", "fake-model-2"
            ))).getBytes(StandardCharsets.UTF_8));
            clientToServer.flush();
            readUntilResponse(reader, messages, 4);
            readUntilSessionUpdate(reader, messages, "config_option_update");

            releasePrompt.countDown();
            readUntilResponse(reader, messages, 3);

            sendPrompt(clientToServer, 5, "config-active-session", "after deferred model switch");
            readUntilResponse(reader, messages, 5);

            clientToServer.close();
            serverThread.join(5000L);

            Assert.assertTrue(containsConfigOptionValue(CliJson.getArray(responseResult(messages, 4), "configOptions"), "model", "fake-model-2"));
            Assert.assertTrue(hasSessionUpdate(messages, "agent_message_chunk", "Echo[openai/fake-model]: hold config switch"));
            Assert.assertTrue(hasSessionUpdate(messages, "agent_message_chunk", "Echo[openai/fake-model-2]: after deferred model switch"));
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    public void test_load_history_replays_delegate_task_events_as_tool_updates() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-task-history");
        CodeCommandOptions options = parseOptions(workspace, "--model", "fake-model");
        String sessionId = "task-history-session";
        seedDelegateTaskHistory(workspace, sessionId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String input = line(request(1, "initialize", params("protocolVersion", 1)))
                + line(request(2, "session/load", params(
                "sessionId", sessionId,
                "cwd", workspace.toString()
        )));

        int exitCode = new AcpJsonRpcServer(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                out,
                err,
                options,
                provider()
        ).run();

        Assert.assertEquals(0, exitCode);

        List<ObjectNode> messages = parseLines(out);
        Assert.assertEquals(sessionId, CliJson.getString(responseResult(messages, 2), "sessionId"));

        ObjectNode toolCall = findSessionUpdate(messages, "tool_call");
        Assert.assertNotNull(toolCall);
        Assert.assertEquals("task-1", CliJson.getString(toolCall, "toolCallId"));
        Assert.assertEquals("Delegate plan", CliJson.getString(toolCall, "title"));
        Assert.assertEquals("other", CliJson.getString(toolCall, "kind"));
        Assert.assertEquals("pending", CliJson.getString(toolCall, "status"));
        ObjectNode toolCallRawInput = CliJson.getObject(toolCall, "rawInput");
        Assert.assertEquals("task-1", CliJson.getString(toolCallRawInput, "taskId"));
        Assert.assertEquals("plan", CliJson.getString(toolCallRawInput, "definition"));
        Assert.assertEquals("delegate-session-1", CliJson.getString(toolCallRawInput, "childSessionId"));
        Assert.assertEquals("fork", CliJson.getString(toolCallRawInput, "sessionMode"));

        ObjectNode toolCallUpdate = findSessionUpdate(messages, "tool_call_update");
        Assert.assertNotNull(toolCallUpdate);
        Assert.assertEquals("task-1", CliJson.getString(toolCallUpdate, "toolCallId"));
        Assert.assertEquals("completed", CliJson.getString(toolCallUpdate, "status"));
        ArrayNode content = CliJson.getArray(toolCallUpdate, "content");
        Assert.assertNotNull(content);
        Assert.assertFalse(content.isEmpty());
        Assert.assertEquals("content", CliJson.getString(CliJson.getObject(content, 0), "type"));
        Assert.assertEquals("delegate plan ready", CliJson.getString(CliJson.getObject(CliJson.getObject(content, 0), "content"), "text"));
        Assert.assertEquals("delegate plan ready", CliJson.getString(CliJson.getObject(toolCallUpdate, "rawOutput"), "text"));
    }

    @Test
    public void test_subagent_handoff_is_streamed_as_live_tool_updates() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-subagent-live");
        CodeCommandOptions options = parseOptions(workspace, "--model", "fake-model");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String input = line(request(1, "initialize", params("protocolVersion", 1)))
                + line(request(2, "session/new", params(
                "sessionId", "subagent-session",
                "cwd", workspace.toString()
        )))
                + line(request(3, "session/prompt", params(
                "sessionId", "subagent-session",
                "prompt", textPrompt("run subagent review")
        )));

        int exitCode = new AcpJsonRpcServer(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                out,
                err,
                options,
                subagentProvider()
        ).run();

        Assert.assertEquals(0, exitCode);

        List<ObjectNode> messages = parseLines(out);
        Assert.assertEquals("end_turn", CliJson.getString(responseResult(messages, 3), "stopReason"));

        ObjectNode handoffCall = findSessionUpdateByToolCallId(messages, "tool_call", "handoff:review-call");
        Assert.assertNotNull(handoffCall);
        Assert.assertEquals("Subagent reviewer", CliJson.getString(handoffCall, "title"));
        Assert.assertEquals("pending", CliJson.getString(handoffCall, "status"));
        Assert.assertEquals("reviewer", CliJson.getString(CliJson.getObject(handoffCall, "rawInput"), "subagent"));

        ObjectNode handoffUpdate = findSessionUpdateByToolCallId(messages, "tool_call_update", "handoff:review-call");
        Assert.assertNotNull(handoffUpdate);
        Assert.assertEquals("completed", CliJson.getString(handoffUpdate, "status"));
        Assert.assertEquals("review-ready", CliJson.getString(CliJson.getObject(handoffUpdate, "rawOutput"), "text"));
    }

    @Test
    public void test_team_subagent_tasks_are_streamed_as_live_tool_updates() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-team-live");
        CodeCommandOptions options = parseOptions(workspace, "--model", "fake-model");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String input = line(request(1, "initialize", params("protocolVersion", 1)))
                + line(request(2, "session/new", params(
                "sessionId", "team-session",
                "cwd", workspace.toString()
        )))
                + line(request(3, "session/prompt", params(
                "sessionId", "team-session",
                "prompt", textPrompt("run team review")
        )));

        int exitCode = new AcpJsonRpcServer(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                out,
                err,
                options,
                teamSubagentProvider()
        ).run();

        Assert.assertEquals(0, exitCode);

        List<ObjectNode> messages = parseLines(out);
        Assert.assertEquals("end_turn", CliJson.getString(responseResult(messages, 3), "stopReason"));

        ObjectNode taskCall = findSessionUpdateByToolCallId(messages, "tool_call", "team-task:review");
        Assert.assertNotNull(taskCall);
        Assert.assertEquals("Team task review", CliJson.getString(taskCall, "title"));
        Assert.assertEquals("pending", CliJson.getString(taskCall, "status"));
        ObjectNode taskCallRawInput = CliJson.getObject(taskCall, "rawInput");
        Assert.assertEquals("reviewer", CliJson.getString(taskCallRawInput, "memberId"));
        Assert.assertEquals("reviewer", CliJson.getString(taskCallRawInput, "memberName"));
        Assert.assertEquals("Review this patch", CliJson.getString(taskCallRawInput, "task"));
        Assert.assertEquals(Integer.valueOf(0), CliJson.getInteger(taskCallRawInput, "percent"));

        ObjectNode taskRunningUpdate = findSessionUpdateByToolCallIdAndStatus(messages, "tool_call_update", "team-task:review", "in_progress");
        Assert.assertNotNull(taskRunningUpdate);
        ObjectNode taskRunningRawOutput = CliJson.getObject(taskRunningUpdate, "rawOutput");
        Assert.assertEquals("Assigned to Reviewer.", CliJson.getString(taskRunningRawOutput, "text"));
        Assert.assertEquals("running", CliJson.getString(taskRunningRawOutput, "phase"));
        Assert.assertEquals(Integer.valueOf(15), CliJson.getInteger(taskRunningRawOutput, "percent"));
        Assert.assertTrue(CliJson.getString(CliJson.getObject(CliJson.getObject(CliJson.getArray(taskRunningUpdate, "content"), 0), "content"), "text")
                .contains("running 15%"));

        ObjectNode taskUpdate = findSessionUpdateByToolCallId(messages, "tool_call_update", "team-task:review");
        Assert.assertNotNull(taskUpdate);
        Assert.assertEquals("completed", CliJson.getString(taskUpdate, "status"));
        ObjectNode taskUpdateRawOutput = CliJson.getObject(taskUpdate, "rawOutput");
        Assert.assertEquals("team-review-ready", CliJson.getString(taskUpdateRawOutput, "text"));
        Assert.assertEquals("completed", CliJson.getString(taskUpdateRawOutput, "phase"));
        Assert.assertEquals(Integer.valueOf(100), CliJson.getInteger(taskUpdateRawOutput, "percent"));

        ObjectNode teamMessage = findSessionUpdateByToolCallIdAndRawOutputType(messages, "team-task:review", "team_message");
        Assert.assertNotNull(teamMessage);
        Assert.assertEquals("tool_call_update", CliJson.getString(teamMessage, "sessionUpdate"));
        ObjectNode teamRawOutput = CliJson.getObject(teamMessage, "rawOutput");
        Assert.assertEquals("task.assigned", CliJson.getString(teamRawOutput, "messageType"));
        Assert.assertEquals("system", CliJson.getString(teamRawOutput, "fromMemberId"));
        Assert.assertEquals("reviewer", CliJson.getString(teamRawOutput, "toMemberId"));
        Assert.assertEquals("review", CliJson.getString(teamRawOutput, "taskId"));
        Assert.assertEquals("Review this patch", CliJson.getString(teamRawOutput, "text"));
        ArrayNode teamMessageContent = CliJson.getArray(teamMessage, "content");
        Assert.assertNotNull(teamMessageContent);
        Assert.assertFalse(teamMessageContent.isEmpty());
        Assert.assertTrue(CliJson.getString(CliJson.getObject(CliJson.getObject(teamMessageContent, 0), "content"), "text")
                .contains("[task.assigned] system -> reviewer"));
        Assert.assertEquals(0, countSessionUpdates(messages, "team_message"));
    }

    @Test
    public void test_permission_request_round_trip() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-permission");
        CodeCommandOptions options = parseOptions(workspace, "--model", "fake-model", "--approval", "manual");

        PipedOutputStream clientToServer = new PipedOutputStream();
        PipedInputStream serverInput = new PipedInputStream(clientToServer);
        PipedOutputStream serverToClient = new PipedOutputStream();
        PipedInputStream clientInput = new PipedInputStream(serverToClient);
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        final AcpJsonRpcServer server = new AcpJsonRpcServer(
                serverInput,
                serverToClient,
                err,
                options,
                provider()
        );

        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                server.run();
            }
        });
        serverThread.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput, StandardCharsets.UTF_8));
        clientToServer.write(line(request(1, "initialize", params("protocolVersion", 1))).getBytes(StandardCharsets.UTF_8));
        clientToServer.write(line(request(2, "session/new", params(
                "sessionId", "permission-session",
                "cwd", workspace.toString()
        ))).getBytes(StandardCharsets.UTF_8));
        clientToServer.write(line(request(3, "session/prompt", params(
                "sessionId", "permission-session",
                "prompt", textPrompt("run bash now")
        ))).getBytes(StandardCharsets.UTF_8));
        clientToServer.flush();

        boolean sawPermissionRequest = false;
        boolean sawToolCallUpdate = false;
        boolean sawPromptResponse = false;
        List<ObjectNode> seenMessages = new ArrayList<ObjectNode>();
        for (int i = 0; i < 20; i++) {
            String line = reader.readLine();
            Assert.assertNotNull(line);
            ObjectNode message = CliJson.parseObject(line);
            seenMessages.add(message);
            if ("session/request_permission".equals(CliJson.getString(message, "method"))) {
                sawPermissionRequest = true;
                Object requestId = CliJson.toPlainValue(message.get("id"));
                clientToServer.write(line(response(requestId, params(
                        "outcome", params(
                                "outcome", "selected",
                                "optionId", "allow_once"
                        )
                ))).getBytes(StandardCharsets.UTF_8));
                clientToServer.flush();
                continue;
            }
            if ("session/update".equals(CliJson.getString(message, "method"))) {
                ObjectNode update = CliJson.getObject(CliJson.getObject(message, "params"), "update");
                if ("tool_call_update".equals(CliJson.getString(update, "sessionUpdate"))) {
                    sawToolCallUpdate = true;
                }
                continue;
            }
            if (hasIntId(message, 3)) {
                sawPromptResponse = true;
                Assert.assertEquals("end_turn", CliJson.getString(CliJson.getObject(message, "result"), "stopReason"));
                break;
            }
        }

        clientToServer.close();
        serverThread.join(5000L);

        Assert.assertTrue(sawPermissionRequest);
        Assert.assertTrue(sawToolCallUpdate);
        Assert.assertTrue(sawPromptResponse);
        Assert.assertEquals(1, countSessionUpdates(seenMessages, "tool_call"));

        ObjectNode toolCall = findSessionUpdate(seenMessages, "tool_call");
        Assert.assertNotNull(toolCall);
        Assert.assertEquals("other", CliJson.getString(toolCall, "kind"));
        Assert.assertEquals("pending", CliJson.getString(toolCall, "status"));

        ObjectNode toolCallUpdate = findSessionUpdate(seenMessages, "tool_call_update");
        Assert.assertNotNull(toolCallUpdate);
        Assert.assertEquals("completed", CliJson.getString(toolCallUpdate, "status"));
        ArrayNode content = CliJson.getArray(toolCallUpdate, "content");
        Assert.assertNotNull(content);
        Assert.assertFalse(content.isEmpty());
        Assert.assertEquals("content", CliJson.getString(CliJson.getObject(content, 0), "type"));
        Assert.assertNotNull(CliJson.getObject(CliJson.getObject(content, 0), "content"));
        Assert.assertTrue(CliJson.getString(CliJson.getObject(CliJson.getObject(content, 0), "content"), "text").contains("cmd /c echo hi"));
    }

    @Test
    public void test_permission_request_uses_generated_session_id_when_session_new_omits_session_id() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-permission-generated-session");
        CodeCommandOptions options = parseOptions(workspace, "--model", "fake-model", "--approval", "manual");

        PipedOutputStream clientToServer = new PipedOutputStream();
        PipedInputStream serverInput = new PipedInputStream(clientToServer);
        PipedOutputStream serverToClient = new PipedOutputStream();
        PipedInputStream clientInput = new PipedInputStream(serverToClient);
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        final AcpJsonRpcServer server = new AcpJsonRpcServer(
                serverInput,
                serverToClient,
                err,
                options,
                provider()
        );

        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                server.run();
            }
        });
        serverThread.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput, StandardCharsets.UTF_8));
        List<ObjectNode> messages = new ArrayList<ObjectNode>();

        clientToServer.write(line(request(1, "initialize", params("protocolVersion", 1))).getBytes(StandardCharsets.UTF_8));
        clientToServer.write(line(request(2, "session/new", params(
                "cwd", workspace.toString()
        ))).getBytes(StandardCharsets.UTF_8));
        clientToServer.flush();

        readUntilResponse(reader, messages, 1);
        readUntilResponse(reader, messages, 2);
        readUntilSessionUpdate(reader, messages, "available_commands_update");

        ObjectNode newSessionResult = responseResult(messages, 2);
        Assert.assertNotNull(newSessionResult);
        String generatedSessionId = CliJson.getString(newSessionResult, "sessionId");
        Assert.assertNotNull(generatedSessionId);
        Assert.assertFalse(generatedSessionId.trim().isEmpty());

        sendPrompt(clientToServer, 3, generatedSessionId, "run bash now");

        boolean sawPermissionRequest = false;
        boolean sawPromptResponse = false;
        for (int i = 0; i < 30; i++) {
            String messageLine = reader.readLine();
            Assert.assertNotNull(messageLine);
            ObjectNode message = CliJson.parseObject(messageLine);
            messages.add(message);
            if ("session/request_permission".equals(CliJson.getString(message, "method"))) {
                sawPermissionRequest = true;
                ObjectNode permissionParams = CliJson.getObject(message, "params");
                Assert.assertNotNull(permissionParams);
                Assert.assertEquals(generatedSessionId, CliJson.getString(permissionParams, "sessionId"));
                Object requestId = CliJson.toPlainValue(message.get("id"));
                clientToServer.write(line(response(requestId, params(
                        "outcome", params(
                                "outcome", "selected",
                                "optionId", "allow_once"
                        )
                ))).getBytes(StandardCharsets.UTF_8));
                clientToServer.flush();
                continue;
            }
            if (hasIntId(message, 3)) {
                sawPromptResponse = true;
                Assert.assertEquals("end_turn", CliJson.getString(CliJson.getObject(message, "result"), "stopReason"));
                break;
            }
        }

        clientToServer.close();
        serverThread.join(5000L);

        Assert.assertTrue(sawPermissionRequest);
        Assert.assertTrue(sawPromptResponse);
        Assert.assertEquals(1, countSessionUpdates(messages, "tool_call"));
    }

    @Test
    public void test_markdown_newlines_are_preserved_in_agent_chunks() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-markdown");
        CodeCommandOptions options = parseOptions(workspace, "--model", "fake-model");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String promptText = "markdown demo";
        String input = line(request(1, "initialize", params("protocolVersion", 1)))
                + line(request(2, "session/new", params(
                "sessionId", "markdown-session",
                "cwd", workspace.toString()
        )))
                + line(request(3, "session/prompt", params(
                "sessionId", "markdown-session",
                "prompt", textPrompt(promptText)
        )));

        int exitCode = new AcpJsonRpcServer(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                out,
                err,
                options,
                provider()
        ).run();

        Assert.assertEquals(0, exitCode);

        List<ObjectNode> messages = parseLines(out);
        ObjectNode update = findSessionUpdate(messages, "agent_message_chunk");
        Assert.assertNotNull(update);
        Assert.assertEquals("# Title\n\n- item", CliJson.getString(CliJson.getObject(update, "content"), "text"));
    }

    @Test
    public void test_markdown_char_stream_is_forwarded_without_coalescing() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-markdown-stream");
        CodeCommandOptions options = parseOptions(workspace, "--model", "fake-model");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String input = line(request(1, "initialize", params("protocolVersion", 1)))
                + line(request(2, "session/new", params(
                "sessionId", "markdown-stream-session",
                "cwd", workspace.toString()
        )))
                + line(request(3, "session/prompt", params(
                "sessionId", "markdown-stream-session",
                "prompt", textPrompt("markdown stream demo")
        )));

        int exitCode = new AcpJsonRpcServer(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                out,
                err,
                options,
                provider()
        ).run();

        Assert.assertEquals(0, exitCode);

        List<ObjectNode> messages = parseLines(out);
        List<String> chunks = sessionUpdateTexts(messages, "agent_message_chunk");
        Assert.assertFalse(chunks.isEmpty());
        Assert.assertEquals(Arrays.asList("##", " Heading\n\n", "1", ". item\n\n", "Done", "."), chunks);
        Assert.assertEquals("## Heading\n\n1. item\n\nDone.", join(chunks));
    }

    @Test
    public void test_whitespace_only_stream_chunks_are_forwarded() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-acp-whitespace-stream");
        CodeCommandOptions options = parseOptions(workspace, "--model", "fake-model");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String input = line(request(1, "initialize", params("protocolVersion", 1)))
                + line(request(2, "session/new", params(
                "sessionId", "whitespace-stream-session",
                "cwd", workspace.toString()
        )))
                + line(request(3, "session/prompt", params(
                "sessionId", "whitespace-stream-session",
                "prompt", textPrompt("whitespace stream demo")
        )));

        int exitCode = new AcpJsonRpcServer(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                out,
                err,
                options,
                provider()
        ).run();

        Assert.assertEquals(0, exitCode);

        List<ObjectNode> messages = parseLines(out);
        List<String> chunks = sessionUpdateTexts(messages, "agent_message_chunk");
        Assert.assertEquals(Arrays.asList("# Heading", "\n\n", "- item"), chunks);
        Assert.assertEquals("# Heading\n\n- item", join(chunks));
    }

    private AcpJsonRpcServer.AgentFactoryProvider provider() {
        return provider(new FakeAcpModelClient());
    }

    private AcpJsonRpcServer.AgentFactoryProvider provider(final AgentModelClient modelClient) {
        return new AcpJsonRpcServer.AgentFactoryProvider() {
            @Override
            public CodingCliAgentFactory create(final CodeCommandOptions options,
                                                final AcpToolApprovalDecorator.PermissionGateway permissionGateway,
                                                final io.github.lnyocly.ai4j.cli.mcp.CliResolvedMcpConfig resolvedMcpConfig) {
                return new CodingCliAgentFactory() {
                    @Override
                    public PreparedCodingAgent prepare(CodeCommandOptions runtimeOptions) {
                        return prepare(runtimeOptions, null, null, Collections.<String>emptySet());
                    }

                    @Override
                    public PreparedCodingAgent prepare(CodeCommandOptions runtimeOptions,
                                                       io.github.lnyocly.ai4j.tui.TerminalIO terminal,
                                                       io.github.lnyocly.ai4j.tui.TuiInteractionState interactionState,
                                                       java.util.Collection<String> pausedMcpServers) {
                        return new PreparedCodingAgent(
                                CodingAgents.builder()
                                        .modelClient(modelClient)
                                        .model(runtimeOptions.getModel())
                                        .workspaceContext(WorkspaceContext.builder().rootPath(runtimeOptions.getWorkspace()).build())
                                        .agentOptions(AgentOptions.builder().stream(runtimeOptions.isStream()).build())
                                        .codingOptions(CodingAgentOptions.builder()
                                                .toolExecutorDecorator(new AcpToolApprovalDecorator(runtimeOptions.getApprovalMode(), permissionGateway))
                                                .build())
                                        .build(),
                                runtimeOptions.getProtocol() == null ? CliProtocol.CHAT : runtimeOptions.getProtocol(),
                                resolvedMcpConfig == null ? null : io.github.lnyocly.ai4j.cli.mcp.CliMcpRuntimeManager.initialize(resolvedMcpConfig)
                        );
                    }
                };
            }
        };
    }

    private AcpJsonRpcServer.AgentFactoryProvider blockingProvider(CountDownLatch promptStarted,
                                                                   CountDownLatch releasePrompt) {
        return provider(new BlockingAcpModelClient(promptStarted, releasePrompt));
    }

    private AcpJsonRpcServer.AgentFactoryProvider factoryBoundApprovalProvider() {
        return new AcpJsonRpcServer.AgentFactoryProvider() {
            @Override
            public CodingCliAgentFactory create(final CodeCommandOptions options,
                                                final AcpToolApprovalDecorator.PermissionGateway permissionGateway,
                                                final io.github.lnyocly.ai4j.cli.mcp.CliResolvedMcpConfig resolvedMcpConfig) {
                final ApprovalMode capturedApprovalMode = options == null ? ApprovalMode.AUTO : options.getApprovalMode();
                return new CodingCliAgentFactory() {
                    @Override
                    public PreparedCodingAgent prepare(CodeCommandOptions runtimeOptions) {
                        return prepare(runtimeOptions, null, null, Collections.<String>emptySet());
                    }

                    @Override
                    public PreparedCodingAgent prepare(CodeCommandOptions runtimeOptions,
                                                       io.github.lnyocly.ai4j.tui.TerminalIO terminal,
                                                       io.github.lnyocly.ai4j.tui.TuiInteractionState interactionState,
                                                       java.util.Collection<String> pausedMcpServers) {
                        return new PreparedCodingAgent(
                                CodingAgents.builder()
                                        .modelClient(new FakeAcpModelClient())
                                        .model(runtimeOptions.getModel())
                                        .workspaceContext(WorkspaceContext.builder().rootPath(runtimeOptions.getWorkspace()).build())
                                        .agentOptions(AgentOptions.builder().stream(runtimeOptions.isStream()).build())
                                        .codingOptions(CodingAgentOptions.builder()
                                                .toolExecutorDecorator(new AcpToolApprovalDecorator(capturedApprovalMode, permissionGateway))
                                                .build())
                                        .build(),
                                runtimeOptions.getProtocol() == null ? CliProtocol.CHAT : runtimeOptions.getProtocol(),
                                resolvedMcpConfig == null ? null : io.github.lnyocly.ai4j.cli.mcp.CliMcpRuntimeManager.initialize(resolvedMcpConfig)
                        );
                    }
                };
            }
        };
    }

    private AcpJsonRpcServer.AgentFactoryProvider subagentProvider() {
        return new AcpJsonRpcServer.AgentFactoryProvider() {
            @Override
            public CodingCliAgentFactory create(final CodeCommandOptions options,
                                                final AcpToolApprovalDecorator.PermissionGateway permissionGateway,
                                                final io.github.lnyocly.ai4j.cli.mcp.CliResolvedMcpConfig resolvedMcpConfig) {
                return new CodingCliAgentFactory() {
                    @Override
                    public PreparedCodingAgent prepare(CodeCommandOptions runtimeOptions) {
                        return prepare(runtimeOptions, null, null, Collections.<String>emptySet());
                    }

                    @Override
                    public PreparedCodingAgent prepare(CodeCommandOptions runtimeOptions,
                                                       io.github.lnyocly.ai4j.tui.TerminalIO terminal,
                                                       io.github.lnyocly.ai4j.tui.TuiInteractionState interactionState,
                                                       java.util.Collection<String> pausedMcpServers) {
                        return new PreparedCodingAgent(
                                CodingAgents.builder()
                                        .modelClient(new FakeSubagentRootModelClient())
                                        .model(runtimeOptions.getModel())
                                        .workspaceContext(WorkspaceContext.builder().rootPath(runtimeOptions.getWorkspace()).build())
                                        .agentOptions(AgentOptions.builder().stream(runtimeOptions.isStream()).build())
                                        .codingOptions(CodingAgentOptions.builder()
                                                .toolExecutorDecorator(new AcpToolApprovalDecorator(runtimeOptions.getApprovalMode(), permissionGateway))
                                                .build())
                                        .subAgent(SubAgentDefinition.builder()
                                                .name("reviewer")
                                                .toolName("subagent_review")
                                                .description("Review code changes")
                                                .agent(Agents.react()
                                                        .modelClient(new FakeSubagentWorkerModelClient())
                                                        .model(runtimeOptions.getModel())
                                                        .build())
                                                .build())
                                        .build(),
                                runtimeOptions.getProtocol() == null ? CliProtocol.CHAT : runtimeOptions.getProtocol(),
                                resolvedMcpConfig == null ? null : io.github.lnyocly.ai4j.cli.mcp.CliMcpRuntimeManager.initialize(resolvedMcpConfig)
                        );
                    }
                };
            }
        };
    }

    private AcpJsonRpcServer.AgentFactoryProvider teamSubagentProvider() {
        return new AcpJsonRpcServer.AgentFactoryProvider() {
            @Override
            public CodingCliAgentFactory create(final CodeCommandOptions options,
                                                final AcpToolApprovalDecorator.PermissionGateway permissionGateway,
                                                final io.github.lnyocly.ai4j.cli.mcp.CliResolvedMcpConfig resolvedMcpConfig) {
                return new CodingCliAgentFactory() {
                    @Override
                    public PreparedCodingAgent prepare(CodeCommandOptions runtimeOptions) {
                        return prepare(runtimeOptions, null, null, Collections.<String>emptySet());
                    }

                    @Override
                    public PreparedCodingAgent prepare(CodeCommandOptions runtimeOptions,
                                                       io.github.lnyocly.ai4j.tui.TerminalIO terminal,
                                                       io.github.lnyocly.ai4j.tui.TuiInteractionState interactionState,
                                                       java.util.Collection<String> pausedMcpServers) {
                        return new PreparedCodingAgent(
                                CodingAgents.builder()
                                        .modelClient(new FakeSubagentRootModelClient())
                                        .model(runtimeOptions.getModel())
                                        .workspaceContext(WorkspaceContext.builder().rootPath(runtimeOptions.getWorkspace()).build())
                                        .agentOptions(AgentOptions.builder().stream(runtimeOptions.isStream()).build())
                                        .codingOptions(CodingAgentOptions.builder()
                                                .toolExecutorDecorator(new AcpToolApprovalDecorator(runtimeOptions.getApprovalMode(), permissionGateway))
                                                .build())
                                        .subAgent(SubAgentDefinition.builder()
                                                .name("team-reviewer")
                                                .toolName("subagent_review")
                                                .description("Review code changes with a team")
                                                .agent(Agents.team()
                                                        .planner((objective, members, teamOptions) -> AgentTeamPlan.builder()
                                                                .tasks(Arrays.asList(
                                                                        AgentTeamTask.builder()
                                                                                .id("review")
                                                                                .memberId("reviewer")
                                                                                .task("Review this patch")
                                                                                .build()
                                                                ))
                                                                .build())
                                                        .synthesizerAgent(Agents.react()
                                                                .modelClient(new FakeSubagentWorkerModelClient("team-synth-complete"))
                                                                .model(runtimeOptions.getModel())
                                                                .build())
                                                        .member(AgentTeamMember.builder()
                                                                .id("reviewer")
                                                                .name("Reviewer")
                                                                .agent(Agents.react()
                                                                        .modelClient(new FakeSubagentWorkerModelClient("team-review-ready"))
                                                                        .model(runtimeOptions.getModel())
                                                                        .build())
                                                                .build())
                                                        .buildAgent())
                                                .build())
                                        .build(),
                                runtimeOptions.getProtocol() == null ? CliProtocol.CHAT : runtimeOptions.getProtocol(),
                                resolvedMcpConfig == null ? null : io.github.lnyocly.ai4j.cli.mcp.CliMcpRuntimeManager.initialize(resolvedMcpConfig)
                        );
                    }
                };
            }
        };
    }

    private AcpJsonRpcServer.AgentFactoryProvider runtimeEchoProvider() {
        return new AcpJsonRpcServer.AgentFactoryProvider() {
            @Override
            public CodingCliAgentFactory create(final CodeCommandOptions options,
                                                final AcpToolApprovalDecorator.PermissionGateway permissionGateway,
                                                final io.github.lnyocly.ai4j.cli.mcp.CliResolvedMcpConfig resolvedMcpConfig) {
                return new CodingCliAgentFactory() {
                    @Override
                    public PreparedCodingAgent prepare(CodeCommandOptions runtimeOptions) {
                        return prepare(runtimeOptions, null, null, Collections.<String>emptySet());
                    }

                    @Override
                    public PreparedCodingAgent prepare(CodeCommandOptions runtimeOptions,
                                                       io.github.lnyocly.ai4j.tui.TerminalIO terminal,
                                                       io.github.lnyocly.ai4j.tui.TuiInteractionState interactionState,
                                                       java.util.Collection<String> pausedMcpServers) {
                        return new PreparedCodingAgent(
                                CodingAgents.builder()
                                        .modelClient(new RuntimeEchoModelClient(
                                                runtimeOptions.getProvider() == null ? null : runtimeOptions.getProvider().getPlatform(),
                                                runtimeOptions.getModel()))
                                        .model(runtimeOptions.getModel())
                                        .workspaceContext(WorkspaceContext.builder().rootPath(runtimeOptions.getWorkspace()).build())
                                        .agentOptions(AgentOptions.builder().stream(runtimeOptions.isStream()).build())
                                        .codingOptions(CodingAgentOptions.builder()
                                                .toolExecutorDecorator(new AcpToolApprovalDecorator(runtimeOptions.getApprovalMode(), permissionGateway))
                                                .build())
                                        .build(),
                                runtimeOptions.getProtocol() == null ? CliProtocol.CHAT : runtimeOptions.getProtocol(),
                                resolvedMcpConfig == null ? null : io.github.lnyocly.ai4j.cli.mcp.CliMcpRuntimeManager.initialize(resolvedMcpConfig)
                        );
                    }
                };
            }
        };
    }

    private AcpJsonRpcServer.AgentFactoryProvider blockingRuntimeEchoProvider(final CountDownLatch promptStarted,
                                                                              final CountDownLatch releasePrompt) {
        return new AcpJsonRpcServer.AgentFactoryProvider() {
            @Override
            public CodingCliAgentFactory create(final CodeCommandOptions options,
                                                final AcpToolApprovalDecorator.PermissionGateway permissionGateway,
                                                final io.github.lnyocly.ai4j.cli.mcp.CliResolvedMcpConfig resolvedMcpConfig) {
                return new CodingCliAgentFactory() {
                    @Override
                    public PreparedCodingAgent prepare(CodeCommandOptions runtimeOptions) {
                        return prepare(runtimeOptions, null, null, Collections.<String>emptySet());
                    }

                    @Override
                    public PreparedCodingAgent prepare(CodeCommandOptions runtimeOptions,
                                                       io.github.lnyocly.ai4j.tui.TerminalIO terminal,
                                                       io.github.lnyocly.ai4j.tui.TuiInteractionState interactionState,
                                                       java.util.Collection<String> pausedMcpServers) {
                        return new PreparedCodingAgent(
                                CodingAgents.builder()
                                        .modelClient(new BlockingRuntimeEchoModelClient(
                                                runtimeOptions.getProvider() == null ? null : runtimeOptions.getProvider().getPlatform(),
                                                runtimeOptions.getModel(),
                                                promptStarted,
                                                releasePrompt))
                                        .model(runtimeOptions.getModel())
                                        .workspaceContext(WorkspaceContext.builder().rootPath(runtimeOptions.getWorkspace()).build())
                                        .agentOptions(AgentOptions.builder().stream(runtimeOptions.isStream()).build())
                                        .codingOptions(CodingAgentOptions.builder()
                                                .toolExecutorDecorator(new AcpToolApprovalDecorator(runtimeOptions.getApprovalMode(), permissionGateway))
                                                .build())
                                        .build(),
                                runtimeOptions.getProtocol() == null ? CliProtocol.CHAT : runtimeOptions.getProtocol(),
                                resolvedMcpConfig == null ? null : io.github.lnyocly.ai4j.cli.mcp.CliMcpRuntimeManager.initialize(resolvedMcpConfig)
                        );
                    }
                };
            }
        };
    }

    private CodeCommandOptions parseOptions(Path workspace, String... args) {
        CodeCommandOptionsParser parser = new CodeCommandOptionsParser();
        List<String> values = new ArrayList<String>(Arrays.asList(args));
        values.add("--workspace");
        values.add(workspace.toString());
        return parser.parse(values, Collections.<String, String>emptyMap(), new Properties(), workspace);
    }

    private ObjectNode request(Object id, String method, Object params) {
        ObjectNode object = CliJson.newObject();
        object.put("jsonrpc", "2.0");
        object.set("id", CliJson.valueToTree(id));
        object.put("method", method);
        object.set("params", CliJson.valueToTree(params));
        return object;
    }

    private ObjectNode response(Object id, Object result) {
        ObjectNode object = CliJson.newObject();
        object.put("jsonrpc", "2.0");
        object.set("id", CliJson.valueToTree(id));
        object.set("result", CliJson.valueToTree(result));
        return object;
    }

    private ArrayNode textPrompt(String text) {
        ArrayNode prompt = CliJson.newArray();
        ObjectNode block = CliJson.newObject();
        block.put("type", "text");
        block.put("text", text);
        prompt.add(block);
        return prompt;
    }

    private ObjectNode params(Object... values) {
        ObjectNode object = CliJson.newObject();
        for (int i = 0; i + 1 < values.length; i += 2) {
            object.set(String.valueOf(values[i]), CliJson.valueToTree(values[i + 1]));
        }
        return object;
    }

    private String line(ObjectNode object) {
        return CliJson.toJson(object) + "\n";
    }

    private void sendPrompt(PipedOutputStream clientToServer,
                            int id,
                            String sessionId,
                            String text) throws Exception {
        clientToServer.write(line(request(id, "session/prompt", params(
                "sessionId", sessionId,
                "prompt", textPrompt(text)
        ))).getBytes(StandardCharsets.UTF_8));
        clientToServer.flush();
    }

    private void readUntilResponse(BufferedReader reader,
                                   List<ObjectNode> messages,
                                   int responseId) throws Exception {
        for (int i = 0; i < 100; i++) {
            String messageLine = reader.readLine();
            Assert.assertNotNull(messageLine);
            if (messageLine.trim().isEmpty()) {
                continue;
            }
            ObjectNode message = CliJson.parseObject(messageLine);
            messages.add(message);
            if (hasIntId(message, responseId)) {
                return;
            }
        }
        Assert.fail("Timed out waiting for ACP response id=" + responseId);
    }

    private void readUntilSessionUpdate(BufferedReader reader,
                                        List<ObjectNode> messages,
                                        String sessionUpdateType) throws Exception {
        if (findSessionUpdate(messages, sessionUpdateType) != null) {
            return;
        }
        for (int i = 0; i < 100; i++) {
            String messageLine = reader.readLine();
            Assert.assertNotNull(messageLine);
            if (messageLine.trim().isEmpty()) {
                continue;
            }
            ObjectNode message = CliJson.parseObject(messageLine);
            messages.add(message);
            if ("session/update".equals(CliJson.getString(message, "method"))) {
                ObjectNode params = CliJson.getObject(message, "params");
                ObjectNode update = CliJson.getObject(params, "update");
                if (update != null && sessionUpdateType.equals(CliJson.getString(update, "sessionUpdate"))) {
                    return;
                }
            }
        }
        Assert.fail("Timed out waiting for ACP session update type=" + sessionUpdateType);
    }

    private List<ObjectNode> parseLines(ByteArrayOutputStream output) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(output.toByteArray()),
                StandardCharsets.UTF_8
        ));
        List<ObjectNode> messages = new ArrayList<ObjectNode>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                messages.add(CliJson.parseObject(line));
            }
        }
        return messages;
    }

    private boolean hasIntId(ObjectNode message, int id) {
        return message != null && Integer.valueOf(id).equals(CliJson.toPlainValue(message.get("id")));
    }

    private boolean hasResponse(List<ObjectNode> messages, int id) {
        for (ObjectNode message : messages) {
            if (hasIntId(message, id) && message.has("result")) {
                return true;
            }
        }
        return false;
    }

    private ObjectNode responseResult(List<ObjectNode> messages, int id) {
        for (ObjectNode message : messages) {
            if (hasIntId(message, id) && message.has("result")) {
                return CliJson.getObject(message, "result");
            }
        }
        return null;
    }

    private ObjectNode findError(List<ObjectNode> messages, int id) {
        for (ObjectNode message : messages) {
            if (hasIntId(message, id) && message.has("error")) {
                return message;
            }
        }
        return null;
    }

    private boolean hasSessionUpdate(List<ObjectNode> messages, String updateType, String expectedText) {
        for (ObjectNode message : messages) {
            if (!"session/update".equals(CliJson.getString(message, "method"))) {
                continue;
            }
            ObjectNode params = CliJson.getObject(message, "params");
            if (params == null) {
                continue;
            }
            ObjectNode update = CliJson.getObject(params, "update");
            if (update == null || !updateType.equals(CliJson.getString(update, "sessionUpdate"))) {
                continue;
            }
            ObjectNode content = CliJson.getObject(update, "content");
            if (content != null && expectedText.equals(CliJson.getString(content, "text"))) {
                return true;
            }
        }
        return false;
    }

    private int countSessionUpdates(List<ObjectNode> messages, String updateType) {
        int count = 0;
        for (ObjectNode message : messages) {
            if (!"session/update".equals(CliJson.getString(message, "method"))) {
                continue;
            }
            ObjectNode params = CliJson.getObject(message, "params");
            if (params == null) {
                continue;
            }
            ObjectNode update = CliJson.getObject(params, "update");
            if (update != null && updateType.equals(CliJson.getString(update, "sessionUpdate"))) {
                count++;
            }
        }
        return count;
    }

    private boolean containsAvailableCommand(ArrayNode commands, String name) {
        if (commands == null || name == null) {
            return false;
        }
        for (int i = 0; i < commands.size(); i++) {
            ObjectNode command = CliJson.getObject(commands, i);
            if (command != null && name.equals(CliJson.getString(command, "name"))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsConfigOption(ArrayNode configOptions, String id) {
        if (configOptions == null || id == null) {
            return false;
        }
        for (int i = 0; i < configOptions.size(); i++) {
            ObjectNode option = CliJson.getObject(configOptions, i);
            if (option != null && id.equals(CliJson.getString(option, "id"))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsConfigOptionValue(ArrayNode configOptions, String id, String expectedValue) {
        if (configOptions == null || id == null || expectedValue == null) {
            return false;
        }
        for (int i = 0; i < configOptions.size(); i++) {
            ObjectNode option = CliJson.getObject(configOptions, i);
            if (option != null
                    && id.equals(CliJson.getString(option, "id"))
                    && expectedValue.equals(CliJson.getString(option, "currentValue"))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSessionUpdatePrefix(List<ObjectNode> messages, String updateType, String prefix) {
        if (prefix == null) {
            return false;
        }
        List<String> texts = sessionUpdateTexts(messages, updateType);
        for (String text : texts) {
            if (text != null && text.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private ObjectNode findSessionUpdate(List<ObjectNode> messages, String updateType) {
        for (ObjectNode message : messages) {
            if (!"session/update".equals(CliJson.getString(message, "method"))) {
                continue;
            }
            ObjectNode params = CliJson.getObject(message, "params");
            if (params == null) {
                continue;
            }
            ObjectNode update = CliJson.getObject(params, "update");
            if (update != null && updateType.equals(CliJson.getString(update, "sessionUpdate"))) {
                return update;
            }
        }
        return null;
    }

    private ObjectNode findSessionUpdateByToolCallId(List<ObjectNode> messages, String updateType, String toolCallId) {
        ObjectNode matched = null;
        for (ObjectNode message : messages) {
            if (!"session/update".equals(CliJson.getString(message, "method"))) {
                continue;
            }
            ObjectNode params = CliJson.getObject(message, "params");
            if (params == null) {
                continue;
            }
            ObjectNode update = CliJson.getObject(params, "update");
            if (update == null || !updateType.equals(CliJson.getString(update, "sessionUpdate"))) {
                continue;
            }
            if (toolCallId.equals(CliJson.getString(update, "toolCallId"))) {
                matched = update;
            }
        }
        return matched;
    }

    private ObjectNode findSessionUpdateByToolCallIdAndStatus(List<ObjectNode> messages,
                                                              String updateType,
                                                              String toolCallId,
                                                              String status) {
        for (ObjectNode message : messages) {
            if (!"session/update".equals(CliJson.getString(message, "method"))) {
                continue;
            }
            ObjectNode params = CliJson.getObject(message, "params");
            if (params == null) {
                continue;
            }
            ObjectNode update = CliJson.getObject(params, "update");
            if (update == null || !updateType.equals(CliJson.getString(update, "sessionUpdate"))) {
                continue;
            }
            if (toolCallId.equals(CliJson.getString(update, "toolCallId")) && status.equals(CliJson.getString(update, "status"))) {
                return update;
            }
        }
        return null;
    }

    private ObjectNode findSessionUpdateByToolCallIdAndRawOutputType(List<ObjectNode> messages,
                                                                     String toolCallId,
                                                                     String rawOutputType) {
        for (ObjectNode message : messages) {
            if (!"session/update".equals(CliJson.getString(message, "method"))) {
                continue;
            }
            ObjectNode params = CliJson.getObject(message, "params");
            if (params == null) {
                continue;
            }
            ObjectNode update = CliJson.getObject(params, "update");
            if (update == null || !"tool_call_update".equals(CliJson.getString(update, "sessionUpdate"))) {
                continue;
            }
            ObjectNode rawOutput = CliJson.getObject(update, "rawOutput");
            if (toolCallId.equals(CliJson.getString(update, "toolCallId"))
                    && rawOutput != null
                    && rawOutputType.equals(CliJson.getString(rawOutput, "type"))) {
                return update;
            }
        }
        return null;
    }

    private List<String> sessionUpdateTexts(List<ObjectNode> messages, String updateType) {
        List<String> texts = new ArrayList<String>();
        for (ObjectNode message : messages) {
            if (!"session/update".equals(CliJson.getString(message, "method"))) {
                continue;
            }
            ObjectNode params = CliJson.getObject(message, "params");
            if (params == null) {
                continue;
            }
            ObjectNode update = CliJson.getObject(params, "update");
            if (update == null || !updateType.equals(CliJson.getString(update, "sessionUpdate"))) {
                continue;
            }
            ObjectNode content = CliJson.getObject(update, "content");
            if (content != null) {
                texts.add(CliJson.getString(content, "text"));
            }
        }
        return texts;
    }

    private String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null) {
                builder.append(value);
            }
        }
        return builder.toString();
    }

    private void seedDelegateTaskHistory(Path workspace, String sessionId) throws Exception {
        Path sessionDirectory = workspace.resolve(".ai4j").resolve("sessions");
        long now = System.currentTimeMillis();
        new FileCodingSessionStore(sessionDirectory).save(StoredCodingSession.builder()
                .sessionId(sessionId)
                .rootSessionId(sessionId)
                .provider("openai")
                .protocol("responses")
                .model("fake-model")
                .workspace(workspace.toString())
                .summary("delegate history")
                .createdAtEpochMs(now)
                .updatedAtEpochMs(now)
                .state(CodingSessionState.builder()
                        .sessionId(sessionId)
                        .workspaceRoot(workspace.toString())
                        .build())
                .build());

        DefaultCodingSessionManager sessionManager = new DefaultCodingSessionManager(
                new FileCodingSessionStore(sessionDirectory),
                new FileSessionEventStore(sessionDirectory.resolve("events"))
        );
        CodingTaskSessionEventBridge bridge = new CodingTaskSessionEventBridge(sessionManager);

        CodingTask task = CodingTask.builder()
                .taskId("task-1")
                .definitionName("plan")
                .parentSessionId(sessionId)
                .childSessionId("delegate-session-1")
                .background(true)
                .status(CodingTaskStatus.QUEUED)
                .progress(CodingTaskProgress.builder()
                        .phase("queued")
                        .message("Task queued for execution.")
                        .percent(0)
                        .updatedAtEpochMs(now)
                        .build())
                .createdAtEpochMs(now)
                .build();
        CodingSessionLink link = CodingSessionLink.builder()
                .linkId("link-1")
                .taskId("task-1")
                .definitionName("plan")
                .parentSessionId(sessionId)
                .childSessionId("delegate-session-1")
                .sessionMode(CodingSessionMode.FORK)
                .background(true)
                .createdAtEpochMs(now)
                .build();

        bridge.onTaskCreated(task, link);
        bridge.onTaskUpdated(task.toBuilder()
                .status(CodingTaskStatus.COMPLETED)
                .outputText("delegate plan ready")
                .progress(task.getProgress().toBuilder()
                        .phase("completed")
                        .message("Delegated session completed.")
                        .percent(100)
                        .updatedAtEpochMs(now + 1)
                        .build())
                .build());
    }

    private static final class FakeAcpModelClient implements AgentModelClient {

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            String toolOutput = findLastToolOutput(prompt);
            if (toolOutput != null) {
                return AgentModelResult.builder().outputText("Tool done: " + toolOutput).build();
            }
            String userText = findLastUserText(prompt);
            if (userText != null && userText.toLowerCase().contains("markdown demo")) {
                return AgentModelResult.builder().outputText("# Title\n\n- item").build();
            }
            if (userText != null && userText.toLowerCase().contains("markdown stream demo")) {
                return AgentModelResult.builder().outputText("## Heading\n\n1. item\n\nDone.").build();
            }
            if (userText != null && userText.toLowerCase().contains("whitespace stream demo")) {
                return AgentModelResult.builder().outputText("# Heading\n\n- item").build();
            }
            if (userText != null && userText.toLowerCase().contains("run bash")) {
                return AgentModelResult.builder()
                        .toolCalls(Collections.singletonList(AgentToolCall.builder()
                                .callId("bash-call")
                                .name("bash")
                                .arguments("{\"action\":\"exec\",\"command\":\"cmd /c echo hi\"}")
                                .build()))
                        .build();
            }
            return AgentModelResult.builder().outputText("Echo: " + userText).build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            AgentModelResult result = create(prompt);
            if (listener != null) {
                if (result.getOutputText() != null && !result.getOutputText().isEmpty()) {
                    String userText = findLastUserText(prompt);
                    if (userText != null && userText.toLowerCase().contains("markdown stream demo")) {
                        List<String> tinyChunks = Arrays.asList("##", " Heading\n\n", "1", ". item\n\n", "Done", ".");
                        for (String chunk : tinyChunks) {
                            listener.onDeltaText(chunk);
                        }
                    } else if (userText != null && userText.toLowerCase().contains("whitespace stream demo")) {
                        for (String chunk : Arrays.asList("# Heading", "\n\n", "- item")) {
                            listener.onDeltaText(chunk);
                        }
                    } else {
                        listener.onDeltaText(result.getOutputText());
                    }
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
                if (!"function_call_output".equals(map.get("type"))) {
                    continue;
                }
                Object output = map.get("output");
                if (output != null) {
                    return String.valueOf(output);
                }
            }
            return null;
        }
    }

    private static final class RuntimeEchoModelClient implements AgentModelClient {

        private final String provider;
        private final String model;

        private RuntimeEchoModelClient(String provider, String model) {
            this.provider = provider;
            this.model = model;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return AgentModelResult.builder()
                    .outputText("Echo[" + provider + "/" + model + "]: " + findLastUserText(prompt))
                    .build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            AgentModelResult result = create(prompt);
            if (listener != null) {
                listener.onDeltaText(result.getOutputText());
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
    }

    private static final class BlockingRuntimeEchoModelClient implements AgentModelClient {

        private final String provider;
        private final String model;
        private final CountDownLatch promptStarted;
        private final CountDownLatch releasePrompt;

        private BlockingRuntimeEchoModelClient(String provider,
                                               String model,
                                               CountDownLatch promptStarted,
                                               CountDownLatch releasePrompt) {
            this.provider = provider;
            this.model = model;
            this.promptStarted = promptStarted;
            this.releasePrompt = releasePrompt;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return AgentModelResult.builder()
                    .outputText("Echo[" + provider + "/" + model + "]: " + findLastUserText(prompt))
                    .build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            String userText = findLastUserText(prompt);
            if (userText != null && userText.toLowerCase().contains("hold config switch")) {
                promptStarted.countDown();
                awaitRelease(releasePrompt);
            }
            AgentModelResult result = create(prompt);
            if (listener != null) {
                listener.onDeltaText(result.getOutputText());
                listener.onComplete(result);
            }
            return result;
        }
    }

    private static final class BlockingAcpModelClient implements AgentModelClient {

        private final FakeAcpModelClient delegate = new FakeAcpModelClient();
        private final CountDownLatch promptStarted;
        private final CountDownLatch releasePrompt;

        private BlockingAcpModelClient(CountDownLatch promptStarted, CountDownLatch releasePrompt) {
            this.promptStarted = promptStarted;
            this.releasePrompt = releasePrompt;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return delegate.create(prompt);
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            String userText = findLastUserText(prompt);
            if (userText != null && userText.toLowerCase().contains("hold mode switch")) {
                promptStarted.countDown();
                awaitRelease(releasePrompt);
            }
            return delegate.createStream(prompt, listener);
        }
    }

    private static void awaitRelease(CountDownLatch releasePrompt) {
        try {
            releasePrompt.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to release prompt", ex);
        }
    }

    private static String findLastUserText(AgentPrompt prompt) {
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

    private static final class FakeSubagentRootModelClient implements AgentModelClient {

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            String toolOutput = findLastToolOutput(prompt);
            if (toolOutput != null) {
                return AgentModelResult.builder().outputText("Root completed after " + toolOutput).build();
            }
            return AgentModelResult.builder()
                    .toolCalls(Collections.singletonList(AgentToolCall.builder()
                            .callId("review-call")
                            .name("subagent_review")
                            .arguments("{\"task\":\"Review this patch\"}")
                            .build()))
                    .build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            AgentModelResult result = create(prompt);
            if (listener != null) {
                if (result.getToolCalls() != null) {
                    for (AgentToolCall call : result.getToolCalls()) {
                        listener.onToolCall(call);
                    }
                }
                if (result.getOutputText() != null) {
                    listener.onDeltaText(result.getOutputText());
                }
                listener.onComplete(result);
            }
            return result;
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
                if (!"function_call_output".equals(map.get("type"))) {
                    continue;
                }
                Object output = map.get("output");
                if (output != null) {
                    return String.valueOf(output);
                }
            }
            return null;
        }
    }

    private static final class FakeSubagentWorkerModelClient implements AgentModelClient {

        private final String output;

        private FakeSubagentWorkerModelClient() {
            this("review-ready");
        }

        private FakeSubagentWorkerModelClient(String output) {
            this.output = output;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return AgentModelResult.builder().outputText(output).build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            AgentModelResult result = create(prompt);
            if (listener != null) {
                listener.onDeltaText(result.getOutputText());
                listener.onComplete(result);
            }
            return result;
        }
    }
}
