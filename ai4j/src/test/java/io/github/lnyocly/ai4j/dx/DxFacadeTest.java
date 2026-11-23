package io.github.lnyocly.ai4j.dx;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.lnyocly.ai4j.client.ModelCall;
import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.model.Block;
import io.github.lnyocly.ai4j.model.DocumentBlock;
import io.github.lnyocly.ai4j.model.EventDeltaKind;
import io.github.lnyocly.ai4j.model.ImageBlock;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.MessageRole;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.ModelUsage;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ToolCallItem;
import io.github.lnyocly.ai4j.model.ToolCallValue;
import io.github.lnyocly.ai4j.model.ToolResultItem;
import io.github.lnyocly.ai4j.protocol.Capabilities;
import io.github.lnyocly.ai4j.protocol.Capability;
import io.github.lnyocly.ai4j.protocol.CapabilityMatrix;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.stream.AiReply;
import io.github.lnyocly.ai4j.stream.AiStream;
import io.github.lnyocly.ai4j.stream.AiStreamEvent;
import io.github.lnyocly.ai4j.stream.AiStreamEventType;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Tests for {@link AiClient}, {@link DefaultAiClient}, {@link AiServiceProxyFactory},
 * {@link InstanceToolRegistry}, {@link ToolOut}, {@link Attachment}, and {@link DxJsonSchema}.
 */
public class DxFacadeTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    public static final class OrderDto {
        public String id;
        public int amount;

        public OrderDto() {
        }

        public OrderDto(String id, int amount) {
            this.id = id;
            this.amount = amount;
        }
    }

    public static final class AuditFinding {
        @JsonProperty("code")
        public String code;
        public boolean highRisk;
    }

    public interface OrderToolApi {
        @AiTool(name = "query_orders", description = "Query customer orders")
        ToolOut<List<OrderDto>> queryOrders(
                @AiToolParam(name = "userId", description = "Customer ID") String userId,
                ToolProgress progress);
    }

    public static class OrderToolBean implements OrderToolApi {
        private final String prefix;

        public OrderToolBean(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public ToolOut<List<OrderDto>> queryOrders(String userId, ToolProgress progress) {
            progress.emit("loading orders", Collections.singletonMap("userId", userId));
            List<OrderDto> details = Collections.singletonList(new OrderDto(prefix + "-" + userId, 420));
            return ToolOut.of("Found 1 order for " + userId, details);
        }
    }

    @AiService(model = "deepseek:deepseek-chat", memorySessionParam = "sessionId")
    public interface OrderAuditAssistant {
        @SystemMessage("Return objective findings")
        @UserMessage("Audit order {orderId}")
        AiReply<List<AuditFinding>> audit(
                @MemoryId String sessionId,
                @AiToolParam(name = "orderId") String orderId);

        @SystemMessage("Summarize concisely")
        String summarize(@UserMessage String prompt);

        AiStream<AuditFinding> streamFinding(@UserMessage String prompt);
    }

    @Test
    public void dualChannelToolOutKeepsDetailsOutOfModelContextAndEmitsProgress() {
        ScriptedClient client = new ScriptedClient(Protocol.CHAT_COMPLETIONS);
        client.enqueue(ModelResult.builder()
                .item(ToolCallItem.of(ToolCallValue.of("tc_1", "query_orders", "{\"userId\":\"u9\"}")))
                .usage(ModelUsage.builder().inputTokens(10).outputTokens(5).totalTokens(15).build())
                .build());
        client.enqueue(ModelResult.builder()
                .item(Message.assistant().text("Order summary complete.").build())
                .usage(ModelUsage.builder().inputTokens(20).outputTokens(6).totalTokens(26).build())
                .build());

        AiClient ai = AiClient.builder()
                .client("deepseek", client)
                .streamExecutor(new Executor() {
                    @Override
                    public void execute(Runnable command) {
                        command.run();
                    }
                })
                .build();

        final List<AiStreamEvent> events = Collections.synchronizedList(new ArrayList<AiStreamEvent>());
        AiReply<String> reply = ai.prompt("deepseek:deepseek-chat")
                .sessionId("sess-1")
                .user("Check {user}", Collections.singletonMap("user", "u9"))
                .tools(new OrderToolBean("ORD"))
                .stream()
                .onEvent(new Consumer<AiStreamEvent>() {
                    @Override
                    public void accept(AiStreamEvent event) {
                        events.add(event);
                    }
                })
                .await();

        Assert.assertEquals("Order summary complete.", reply.getData());
        Assert.assertEquals(1, reply.getToolDetails().size());
        @SuppressWarnings("unchecked")
        List<OrderDto> details = (List<OrderDto>) reply.getToolDetails().get(0);
        Assert.assertEquals("ORD-u9", details.get(0).id);
        Assert.assertEquals(41L, reply.getUsage().getTotalTokens());

        // Second request sent to model contains content ("Found 1 order for u9") and NEVER details ("ORD-u9")
        ModelRequest secondReq = client.requests.get(1);
        String wireContext = JsonCodec.toJson(secondReq.getItems());
        Assert.assertTrue(wireContext.contains("Found 1 order for u9"));
        Assert.assertFalse("ToolOut.details must never enter LLM context", wireContext.contains("ORD-u9"));

        // Progress and TOOL_EXEC_END carried details on stream
        boolean sawProgress = false;
        boolean sawToolEndDetails = false;
        for (AiStreamEvent e : events) {
            if (e.getType() == AiStreamEventType.TOOL_EXEC_PROGRESS && "loading orders".equals(e.getDelta())) {
                sawProgress = true;
            }
            if (e.getType() == AiStreamEventType.TOOL_EXEC_END && e.getToolDetails() != null) {
                sawToolEndDetails = true;
            }
        }
        Assert.assertTrue(sawProgress);
        Assert.assertTrue(sawToolEndDetails);
    }

    @Test
    public void structuredSelfHealAndDxJsonSchemaAndAiServiceProxyWork() throws Exception {
        ScriptedClient client = new ScriptedClient(Protocol.CHAT_COMPLETIONS);
        // 1) Invalid JSON triggers self-heal retry; 2) valid JSON list succeeds
        client.enqueue(ModelResult.builder()
                .item(Message.assistant().text("not valid json").build())
                .build());
        client.enqueue(ModelResult.builder()
                .item(Message.assistant().text("```json\n[{\"code\":\"R01\",\"highRisk\":true}]\n```").build())
                .build());
        // 3) summarize()
        client.enqueue(ModelResult.builder()
                .item(Message.assistant().text("short summary").build())
                .build());
        // 4) streamFinding()
        client.enqueue(ModelResult.builder()
                .item(Message.assistant().text("{\"code\":\"R02\",\"highRisk\":false}").build())
                .build());

        AiClient ai = AiClient.builder()
                .client("deepseek", client)
                .defaultMaxSelfHealRetries(2)
                .build();

        OrderAuditAssistant assistant = ai.service(OrderAuditAssistant.class);
        AiReply<List<AuditFinding>> findings = assistant.audit("order-sess-1", "O-77");
        Assert.assertEquals(1, findings.getData().size());
        Assert.assertEquals("R01", findings.getData().get(0).code);
        Assert.assertTrue(findings.getData().get(0).highRisk);

        // Verify DxJsonSchema generated array-of-AuditFinding schema (not {"type":"string"})
        String firstSys = ((Message) client.requests.get(0).getItems().get(0)).textContent();
        Assert.assertTrue(firstSys.contains("\"type\":\"array\""));
        Assert.assertTrue(firstSys.contains("\"highRisk\""));

        Assert.assertEquals("short summary", assistant.summarize("hello"));
        AiReply<AuditFinding> streamed = assistant.streamFinding("hello").await();
        Assert.assertEquals("R02", streamed.getData().code);

        // Verify DxJsonSchema directly with TypeReference and JavaType
        Map<String, Object> listSchema = DxJsonSchema.schemaFor(new TypeReference<List<AuditFinding>>() {});
        Assert.assertEquals("array", listSchema.get("type"));
        Map<String, Object> javaTypeSchema = DxJsonSchema.schemaFor(
                JsonCodec.protocolMapper().constructType(new TypeReference<List<AuditFinding>>() {}));
        Assert.assertEquals("array", javaTypeSchema.get("type"));

        // Verify Attachment variants including Path
        Path txtPath = tempFolder.newFile("note.png").toPath();
        Files.write(txtPath, new byte[]{1, 2, 3});
        Block imgBlock = Attachment.fromPath(txtPath).toBlock();
        Assert.assertTrue(imgBlock instanceof ImageBlock);
        Block docBlock = Attachment.fromInputStream(
                new ByteArrayInputStream("hi".getBytes(StandardCharsets.UTF_8)), "text/plain").toBlock();
        Assert.assertTrue(docBlock instanceof DocumentBlock);
    }

    @Test
    public void toolRegistrySupportsExternalRegistration() {
        InstanceToolRegistry reg = InstanceToolRegistry.of(new OrderToolBean("EXT"));
        ScriptedClient client = new ScriptedClient(Protocol.CHAT_COMPLETIONS);
        client.enqueue(ModelResult.builder()
                .item(ToolCallItem.of(ToolCallValue.of("c1", "query_orders", "{\"userId\":\"42\"}")))
                .build());
        client.enqueue(ModelResult.builder()
                .item(Message.assistant().text("done").build())
                .build());

        AiClient ai = AiClient.of(client);
        AiReply<String> reply = ai.prompt()
                .model("deepseek-chat")
                .toolRegistry(reg)
                .user("go")
                .call();
        Assert.assertEquals("done", reply.getText());
        Assert.assertEquals(1, reply.getToolDetails().size());
    }

    private static final class ScriptedClient implements ModelClient {

        private final Endpoint endpoint;
        private final Capabilities capabilities;
        private final Deque<ModelResult> responses = new ArrayDeque<ModelResult>();
        private final List<ModelRequest> requests = new ArrayList<ModelRequest>();

        private ScriptedClient(Protocol protocol) {
            this.endpoint = Endpoint.builder(protocol).baseUrl("https://scripted.local").build();
            this.capabilities = Capabilities.of(protocol, CapabilityMatrix.forProtocol(protocol));
        }

        private void enqueue(ModelResult result) {
            responses.addLast(result);
        }

        @Override
        public Endpoint endpoint() {
            return endpoint;
        }

        @Override
        public Capabilities capabilities() {
            return capabilities;
        }

        @Override
        public void require(Capability capability) {
            capabilities.require(capability);
        }

        @Override
        public ModelResult create(ModelRequest request) {
            requests.add(request);
            return responses.removeFirst();
        }

        @Override
        public ModelCall createStream(ModelRequest request, ModelEventListener listener) {
            requests.add(request);
            final ModelResult result = responses.removeFirst();
            if (listener != null) {
                for (ModelItem item : result.getItems()) {
                    if (item instanceof Message) {
                        String text = ((Message) item).textContent();
                        if (text != null && !text.isEmpty()) {
                            listener.onEvent(ModelEvent.builder(endpoint.getProtocol(), ModelEventType.ITEM_DELTA)
                                    .delta(EventDeltaKind.TEXT, text)
                                    .build());
                        }
                        listener.onEvent(ModelEvent.builder(endpoint.getProtocol(), ModelEventType.ITEM_DONE)
                                .item(item)
                                .build());
                    } else if (item instanceof ToolCallItem) {
                        listener.onEvent(ModelEvent.builder(endpoint.getProtocol(), ModelEventType.ITEM_DONE)
                                .item(item)
                                .build());
                    }
                }
                listener.onEvent(ModelEvent.builder(endpoint.getProtocol(), ModelEventType.DONE)
                        .usage(result.getUsage())
                        .build());
            }
            return new ModelCall() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public ModelResult await() {
                    return result;
                }

                @Override
                public ModelCallStatus status() {
                    return ModelCallStatus.COMPLETED;
                }
            };
        }
    }
}
