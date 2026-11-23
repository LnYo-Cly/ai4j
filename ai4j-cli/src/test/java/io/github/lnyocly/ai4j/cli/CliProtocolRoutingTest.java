package io.github.lnyocly.ai4j.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.model.MessagesModelClient;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptionsParser;
import io.github.lnyocly.ai4j.cli.factory.DefaultCodingCliAgentFactory;
import io.github.lnyocly.ai4j.cli.factory.GeminiCliModelClient;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.service.PlatformType;
import org.jline.reader.Candidate;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

public class CliProtocolRoutingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void parsesAllFourProtocolsAndAnthropicAlias() {
        Assert.assertEquals(CliProtocol.CHAT, CliProtocol.parse("chat"));
        Assert.assertEquals(CliProtocol.RESPONSES, CliProtocol.parse("RESPONSES"));
        Assert.assertEquals(CliProtocol.MESSAGES, CliProtocol.parse(" messages "));
        Assert.assertEquals(CliProtocol.MESSAGES, CliProtocol.parse("anthropic"));
        Assert.assertEquals(CliProtocol.GEMINI, CliProtocol.parse("GEMINI"));

        try {
            CliProtocol.parse("unknown");
            Assert.fail("Expected unsupported protocol failure");
        } catch (IllegalArgumentException ex) {
            Assert.assertEquals("Unsupported protocol: unknown. Expected: chat, responses, messages, gemini", ex.getMessage());
        }
    }

    @Test
    public void resolvesDefaultProtocolsAndSupportMatrix() {
        Assert.assertEquals(CliProtocol.MESSAGES, CliProtocol.defaultProtocol(PlatformType.ANTHROPIC, null));
        Assert.assertEquals(CliProtocol.GEMINI, CliProtocol.defaultProtocol(PlatformType.GEMINI, null));
        Assert.assertEquals(CliProtocol.RESPONSES, CliProtocol.defaultProtocol(PlatformType.OPENAI, null));
        Assert.assertEquals(CliProtocol.CHAT, CliProtocol.defaultProtocol(PlatformType.OPENAI, "https://api.deepseek.com"));
        Assert.assertEquals(CliProtocol.RESPONSES, CliProtocol.defaultProtocol(PlatformType.DASHSCOPE, null));
        Assert.assertEquals(CliProtocol.CHAT, CliProtocol.defaultProtocol(PlatformType.ZHIPU, null));

        Assert.assertTrue(CliProtocol.MESSAGES.isSupportedBy(PlatformType.ANTHROPIC));
        Assert.assertTrue(CliProtocol.MESSAGES.isSupportedBy(PlatformType.ZHIPU));
        Assert.assertTrue(CliProtocol.GEMINI.isSupportedBy(PlatformType.GEMINI));
        Assert.assertFalse(CliProtocol.RESPONSES.isSupportedBy(PlatformType.ANTHROPIC));
        Assert.assertFalse(CliProtocol.RESPONSES.isSupportedBy(PlatformType.GEMINI));
        Assert.assertFalse(CliProtocol.GEMINI.isSupportedBy(PlatformType.ANTHROPIC));
    }

    @Test
    public void parsesCliFlagsAndSuggestsAllProtocolCandidates() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-cli-protocol-test");
        CodeCommandOptionsParser parser = new CodeCommandOptionsParser();

        CodeCommandOptions anthropicOptions = parser.parse(
                Arrays.asList("--provider", "anthropic", "--protocol", "anthropic", "--model", "claude-sonnet-4-5", "--workspace", workspace.toString()),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );
        Assert.assertEquals(CliProtocol.MESSAGES, anthropicOptions.getProtocol());

        CodeCommandOptions geminiOptions = parser.parse(
                Arrays.asList("--provider", "gemini", "--protocol", "gemini", "--model", "gemini-2.5-pro", "--workspace", workspace.toString()),
                Collections.<String, String>emptyMap(),
                new Properties(),
                workspace
        );
        Assert.assertEquals(CliProtocol.GEMINI, geminiOptions.getProtocol());

        SlashCommandController controller = new SlashCommandController(null, null);
        List<Candidate> candidates = controller.suggest("/provider add demo --protocol ", "/provider add demo --protocol ".length());
        List<String> values = new ArrayList<String>();
        for (Candidate candidate : candidates) {
            values.add(candidate.value());
        }
        Assert.assertTrue(values.contains("chat"));
        Assert.assertTrue(values.contains("responses"));
        Assert.assertTrue(values.contains("messages"));
        Assert.assertTrue(values.contains("gemini"));
    }

    @Test
    public void factoryRoutesMessagesAndGeminiProtocolsCleanly() throws Exception {
        TestableFactory factory = new TestableFactory();
        Path workspace = Files.createTempDirectory("ai4j-cli-routing-messages");
        final AtomicReference<String> requestPath = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                requestPath.set(exchange.getRequestURI().getPath());
                byte[] response = ("{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-sonnet-4-5\","
                        + "\"content\":[{\"type\":\"text\",\"text\":\"Hello from Claude\"}],"
                        + "\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":10,\"output_tokens\":4}}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(response);
                }
            }
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            CodeCommandOptions zhipuMessagesOptions = parseOptions(
                    workspace, "zhipu", "messages", "claude-sonnet-4-5", baseUrl, "test-anthropic-key"
            );

            AgentModelClient messagesClient = factory.createClient(zhipuMessagesOptions, CliProtocol.MESSAGES);
            Assert.assertTrue(messagesClient instanceof MessagesModelClient);

            AgentModelResult result = messagesClient.create(AgentPrompt.builder()
                    .model("claude-sonnet-4-5")
                    .items(Collections.<Object>singletonList(AgentInputItem.userMessage("hi")))
                    .build());
            Assert.assertEquals("Hello from Claude", result.getOutputText());
            Assert.assertEquals("/v1/messages", requestPath.get());
        } finally {
            server.stop(0);
        }

        try {
            CodeCommandOptions unsupported = parseOptions(
                    workspace, "anthropic", "responses", "claude-sonnet-4-5", null, "test-key"
            );
            factory.createClient(unsupported, CliProtocol.RESPONSES);
            Assert.fail("Expected unsupported protocol error");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage().contains("does not support responses protocol"));
        }
    }

    @Test
    public void geminiClientExecutesSyncMultiTurnAndSseStreams() throws Exception {
        TestableFactory factory = new TestableFactory();
        Path workspace = Files.createTempDirectory("ai4j-cli-routing-gemini");
        final List<String> paths = new ArrayList<String>();
        final List<String> bodies = new ArrayList<String>();
        final AtomicReference<String> apiKeyHeader = new AtomicReference<String>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String path = exchange.getRequestURI().getRawPath();
                String query = exchange.getRequestURI().getRawQuery();
                paths.add(query == null ? path : path + "?" + query);
                apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
                bodies.add(readUtf8(exchange.getRequestBody()));
                boolean stream = path.contains(":streamGenerateContent");
                byte[] response = stream
                        ? ("data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Final \"},{\"text\":\"answer\"}]}}],\"usageMetadata\":{\"promptTokenCount\":25,\"candidatesTokenCount\":6,\"totalTokenCount\":31}}\n\n")
                        .getBytes(StandardCharsets.UTF_8)
                        : ("{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":["
                        + "{\"text\":\"thinking step\",\"thought\":true},"
                        + "{\"functionCall\":{\"name\":\"bash\",\"args\":{\"cmd\":\"pwd\"}},\"thoughtSignature\":\"sig-123\"}"
                        + "]}}],\"usageMetadata\":{\"promptTokenCount\":20,\"candidatesTokenCount\":8,\"thoughtsTokenCount\":5,\"totalTokenCount\":33}}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", stream ? "text/event-stream" : "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(response);
                }
            }
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            CodeCommandOptions options = parseOptions(
                    workspace, "gemini", "gemini", "gemini-2.5-pro", baseUrl, "gemini-secret"
            );

            AgentModelClient client = factory.createClient(options, CliProtocol.GEMINI);
            Assert.assertTrue(client instanceof GeminiCliModelClient);

            Tool tool = new Tool();
            tool.setType("function");
            Tool.Function fn = new Tool.Function();
            fn.setName("bash");
            fn.setDescription("Run shell");
            Tool.Function.Parameter param = new Tool.Function.Parameter();
            Map<String, Tool.Function.Property> props = new LinkedHashMap<String, Tool.Function.Property>();
            props.put("cmd", new Tool.Function.Property("string", "Command", null, null, false));
            param.setProperties(props);
            param.setRequired(Collections.singletonList("cmd"));
            fn.setParameters(param);
            tool.setFunction(fn);

            AgentModelResult first = client.create(AgentPrompt.builder()
                    .model("gemini-2.5-pro")
                    .systemPrompt("Be concise")
                    .items(Collections.<Object>singletonList(AgentInputItem.userMessage("check pwd")))
                    .tools(Collections.<Object>singletonList(tool))
                    .toolChoice("auto")
                    .build());

            Assert.assertEquals("thinking step", first.getReasoningText());
            Assert.assertEquals(1, first.getToolCalls().size());
            AgentToolCall call = first.getToolCalls().get(0);
            Assert.assertEquals("bash", call.getName());
            Assert.assertEquals("sig-123", call.getMetadata().get(GeminiCliModelClient.METADATA_THOUGHT_SIGNATURE));
            Assert.assertEquals(Long.valueOf(33L), first.getTotalTokens());

            Assert.assertEquals("/v1beta/models/gemini-2.5-pro:generateContent", paths.get(0));
            Assert.assertEquals("gemini-secret", apiKeyHeader.get());
            JsonNode firstBody = MAPPER.readTree(bodies.get(0));
            Assert.assertEquals("Be concise", firstBody.path("systemInstruction").path("parts").get(0).path("text").asText());
            Assert.assertEquals("bash", firstBody.path("tools").get(0).path("functionDeclarations").get(0).path("name").asText());

            List<Object> followUpItems = new ArrayList<Object>();
            followUpItems.add(AgentInputItem.userMessage("check pwd"));
            followUpItems.addAll(first.getMemoryItems());
            followUpItems.add(AgentInputItem.functionCallOutput(call.getCallId(), "/workspace"));

            final StringBuilder streamed = new StringBuilder();
            AgentModelResult second = client.createStream(AgentPrompt.builder()
                    .model("gemini-2.5-pro")
                    .items(followUpItems)
                    .stream(true)
                    .build(), new AgentModelStreamListener() {
                @Override
                public void onDeltaText(String delta) {
                    streamed.append(delta);
                }
            });

            Assert.assertEquals("Final answer", streamed.toString());
            Assert.assertEquals("Final answer", second.getOutputText());

            Assert.assertEquals("/v1beta/models/gemini-2.5-pro:streamGenerateContent?alt=sse", paths.get(1));
            JsonNode secondBody = MAPPER.readTree(bodies.get(1));
            Assert.assertEquals(3, secondBody.path("contents").size());
            Assert.assertEquals("sig-123", secondBody.path("contents").get(1).path("parts").get(0).path("thoughtSignature").asText());
            Assert.assertEquals("bash", secondBody.path("contents").get(2).path("parts").get(0).path("functionResponse").path("name").asText());
        } finally {
            server.stop(0);
        }
    }

    private static CodeCommandOptions parseOptions(Path workspace,
                                                   String provider,
                                                   String protocol,
                                                   String model,
                                                   String baseUrl,
                                                   String apiKey) {
        List<String> args = new ArrayList<String>();
        args.add("--provider");
        args.add(provider);
        args.add("--protocol");
        args.add(protocol);
        args.add("--model");
        args.add(model);
        if (baseUrl != null) {
            args.add("--base-url");
            args.add(baseUrl);
        }
        if (apiKey != null) {
            args.add("--api-key");
            args.add(apiKey);
        }
        return new CodeCommandOptionsParser().parse(args, Collections.<String, String>emptyMap(), new Properties(), workspace);
    }

    private static String readUtf8(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static final class TestableFactory extends DefaultCodingCliAgentFactory {
        AgentModelClient createClient(CodeCommandOptions options, CliProtocol protocol) {
            return createModelClient(options, protocol);
        }
    }
}
