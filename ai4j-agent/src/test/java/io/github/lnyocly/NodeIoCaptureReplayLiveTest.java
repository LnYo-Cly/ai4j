package io.github.lnyocly;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.model.MessagesModelClient;
import io.github.lnyocly.ai4j.agent.replay.InMemoryIoCaptureSink;
import io.github.lnyocly.ai4j.agent.replay.IoCaptureAgentListener;
import io.github.lnyocly.ai4j.agent.replay.NodeIoRecord;
import io.github.lnyocly.ai4j.agent.replay.NodeReplayer;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.config.AnthropicConfig;
import io.github.lnyocly.ai4j.platform.anthropic.chat.AnthropicMessagesService;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IMessagesService;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import okhttp3.OkHttpClient;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Live verification of node I/O capture + replay against a real LLM (GLM via the Anthropic
 * Messages endpoint). Runs a real agent turn with a tool, captures every MODEL/TOOL node's I/O,
 * then <b>live-replays the MODEL node by re-invoking the real LLM</b> with the captured prompt,
 * and re-invokes the captured tool. Skipped unless {@code ANTHROPIC_API_KEY} is set.
 */
@Category(LiveProviderTest.class)
public class NodeIoCaptureReplayLiveTest {

    private static String env(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.trim().isEmpty()) ? def : v;
    }

    @Test
    public void liveCaptureAndReplayModelAndToolNodes() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assume.assumeTrue("skip: ANTHROPIC_API_KEY not set", key != null && !key.trim().isEmpty());
        String baseUrl = env("ANTHROPIC_BASE_URL", "https://open.bigmodel.cn/api/anthropic/");
        String model = env("ANTHROPIC_MODEL", "glm-5.1");

        // echo_text tool
        Tool.Function fn = new Tool.Function();
        fn.setName("echo_text");
        fn.setDescription("Echo back the provided text exactly.");
        Tool.Function.Parameter param = new Tool.Function.Parameter();
        Map<String, Tool.Function.Property> props = new HashMap<String, Tool.Function.Property>();
        Tool.Function.Property textProp = new Tool.Function.Property();
        textProp.setType("string");
        textProp.setDescription("The text to echo back");
        props.put("text", textProp);
        param.setProperties(props);
        param.setRequired(Collections.singletonList("text"));
        fn.setParameters(param);
        Tool tool = new Tool("function", fn);
        AgentToolRegistry registry = new StaticToolRegistry(Collections.<Object>singletonList(tool));
        final ToolExecutor executor = new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) {
                return "echo result";
            }
        };

        Agent agent = Agents.react()
                .anthropicMessages(key, baseUrl)
                .model(model)
                .maxOutputTokens(512)
                .toolRegistry(registry)
                .toolExecutor(executor)
                .build();

        InMemoryIoCaptureSink sink = new InMemoryIoCaptureSink();
        IoCaptureAgentListener capture = new IoCaptureAgentListener(sink);

        AgentResult result = agent.newSession().runStreamResult(
                AgentRequest.builder().input("Use the echo_text tool to echo 'hello', then tell me the result.").build(),
                capture);

        System.out.println("=== capture/replay live test ===");
        System.out.println("run outputText=" + result.getOutputText());
        System.out.println("run toolResults=" + result.getToolResults());

        List<NodeIoRecord> models = sink.records(NodeIoRecord.NodeType.MODEL);
        List<NodeIoRecord> tools = sink.records(NodeIoRecord.NodeType.TOOL);
        assertTrue("must capture >=1 MODEL node", models.size() >= 1);
        assertTrue("MODEL node input must be the AgentPrompt", models.get(0).getInputs() instanceof AgentPrompt);
        assertTrue("must capture >=1 TOOL node (echo_text)", tools.size() >= 1);
        assertTrue("TOOL node input must be the AgentToolCall", tools.get(0).getInputs() instanceof AgentToolCall);

        // --- LIVE replay of the first MODEL node: re-invoke the REAL LLM with the captured prompt ---
        AgentModelClient replayClient = buildReplayClient(key, baseUrl);
        AgentModelResult liveReplay = new NodeReplayer().replayModelLive(models.get(0), replayClient);
        assertNotNull("live replay must return a result", liveReplay);
        boolean hasOutput = (liveReplay.getOutputText() != null && !liveReplay.getOutputText().isEmpty())
                || (liveReplay.getToolCalls() != null && !liveReplay.getToolCalls().isEmpty())
                || liveReplay.getRawResponse() != null;
        System.out.println("live-replay outputText=" + liveReplay.getOutputText()
                + " toolCalls=" + (liveReplay.getToolCalls() == null ? 0 : liveReplay.getToolCalls().size()));
        assertTrue("live replay must produce real model output (a fresh REAL LLM call)", hasOutput);

        // --- replay the TOOL node: re-invoke echo with the captured call ---
        NodeIoRecord toolRecord = tools.get(0);
        AgentToolResult replayedTool = new NodeReplayer().replayToolLive(toolRecord,
                new java.util.function.Function<AgentToolCall, AgentToolResult>() {
                    @Override
                    public AgentToolResult apply(AgentToolCall call) {
                        try {
                            return AgentToolResult.builder()
                                    .name(call.getName())
                                    .callId(call.getCallId())
                                    .output(executor.execute(call))
                                    .build();
                        } catch (Exception e) {
                            return AgentToolResult.builder()
                                    .name(call.getName())
                                    .callId(call.getCallId())
                                    .output("replay error: " + e.getMessage())
                                    .build();
                        }
                    }
                });
        assertNotNull(replayedTool);
        assertTrue("replayed tool must carry the echo output",
                replayedTool.getOutput() != null && replayedTool.getOutput().contains("echo"));
    }

    /** Builds an AgentModelClient identical to AgentBuilder.anthropicMessages(...) for live replay. */
    private static AgentModelClient buildReplayClient(String key, String baseUrl) {
        AnthropicConfig config = new AnthropicConfig();
        config.setApiKey(key);
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            config.setApiHost(baseUrl);
        }
        Configuration configuration = new Configuration();
        configuration.setAnthropicConfig(config);
        configuration.setOkHttpClient(new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build());
        IMessagesService service = new AnthropicMessagesService(configuration);
        return new MessagesModelClient(service);
    }
}
