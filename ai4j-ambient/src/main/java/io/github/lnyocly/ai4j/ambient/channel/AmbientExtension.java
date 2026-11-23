package io.github.lnyocly.ai4j.ambient.channel;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.ambient.heartbeat.QuietHoursPolicy;
import io.github.lnyocly.ai4j.ambient.heartbeat.SupervisoryAgenda;
import io.github.lnyocly.ai4j.ambient.memory.MemoryProvenanceGate;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ServiceKey;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandRequest;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandSpec;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import io.github.lnyocly.ai4j.harness.HarnessCommandGateway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cordis {@link Ai4jExtension} wiring the ambient {@link ChannelGateway},
 * {@link SupervisoryAgenda}, and {@link MemoryProvenanceGate} services into
 * {@link ExtensionContext} with reversible {@code Disposable} cleanup.
 */
public final class AmbientExtension implements Ai4jExtension {

    public static final String EXTENSION_ID = "ambient";
    public static final String TOOL_ESCALATE = "ambient_escalate";
    public static final String TOOL_CHANNEL_SEND = "ambient_channel_send";
    public static final String COMMAND_STATUS = "ambient-status";
    public static final String SKILL_NAME = "ambient-runtime";

    public static final ServiceKey<ChannelGateway> CHANNEL_GATEWAY_KEY =
            ServiceKey.of("ai4j.ambient.channel-gateway", ChannelGateway.class);
    public static final ServiceKey<SupervisoryAgenda> SUPERVISORY_AGENDA_KEY =
            ServiceKey.of("ai4j.ambient.supervisory-agenda", SupervisoryAgenda.class);
    public static final ServiceKey<MemoryProvenanceGate> PROVENANCE_GATE_KEY =
            ServiceKey.of("ai4j.ambient.memory-provenance-gate", MemoryProvenanceGate.class);
    public static final ServiceKey<HarnessCommandGateway> HARNESS_GATEWAY_KEY =
            ServiceKey.of("ai4j.ambient.harness-gateway", HarnessCommandGateway.class);

    private static final String VERSION = "2.8.2-SNAPSHOT";
    private final AtomicReference<ChannelGateway> activeGateway = new AtomicReference<ChannelGateway>();

    @Override
    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id(EXTENSION_ID)
                .name("AI4J Ambient Runtime")
                .version(VERSION)
                .vendor("ai4j")
                .capability(ExtensionCapability.TOOL)
                .capability(ExtensionCapability.COMMAND)
                .capability(ExtensionCapability.SKILL)
                .permission("ambient.channel")
                .permission("ambient.memory")
                .configPrefix("ai4j.extensions.ambient")
                .build();
    }

    @Override
    public void apply(ExtensionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("extension context must not be null");
        }
        Optional<HarnessCommandGateway> harnessOpt = context.findService(HARNESS_GATEWAY_KEY);
        HarnessCommandGateway harnessGateway = harnessOpt.isPresent() ? harnessOpt.get() : null;

        final SupervisoryAgenda agenda = new SupervisoryAgenda();
        final MemoryProvenanceGate provenanceGate = new MemoryProvenanceGate();
        final ChannelGateway gateway = new ChannelGateway(harnessGateway, agenda, QuietHoursPolicy.disabled());
        activeGateway.set(gateway);

        context.provide(SUPERVISORY_AGENDA_KEY, agenda);
        context.provide(PROVENANCE_GATE_KEY, provenanceGate);
        context.provide(CHANNEL_GATEWAY_KEY, gateway);
        context.effect(() -> {
            activeGateway.compareAndSet(gateway, null);
            gateway.dispose();
        });

        context.tools().register(channelSendToolSpec(), new ExtensionToolExecutor() {
            @Override
            public String execute(ExtensionToolCall call) {
                return handleSendTool(gateway, call);
            }
        });

        context.tools().register(escalateToolSpec(), new ExtensionToolExecutor() {
            @Override
            public String execute(ExtensionToolCall call) {
                return handleEscalateTool(gateway, call);
            }
        });

        context.commands().register(statusCommandSpec(), new io.github.lnyocly.ai4j.extension.command.ExtensionCommandHandler() {
            @Override
            public String handle(ExtensionCommandRequest request) {
                Map<String, Object> status = new LinkedHashMap<String, Object>();
                status.put("extensionId", EXTENSION_ID);
                status.put("disposed", Boolean.valueOf(gateway.isDisposed()));
                status.put("endpointCount", Integer.valueOf(gateway.getEndpoints().size()));
                status.put("awaitingRulingCount", Integer.valueOf(agenda.awaitingRuling(null).size()));
                status.put("pendingDigestCount", Integer.valueOf(agenda.pendingDigest(null).size()));
                return JsonCodec.toJson(status);
            }
        });

        context.skills().register(ExtensionSkillResource.builder()
                .name(SKILL_NAME)
                .description("Always-on personal ambient agent guidelines for provenance-gated memory, two-tier heartbeat, and structured channel escalations.")
                .resourcePath("skills/ambient/SKILL.md")
                .build());
    }

    @Override
    public void onStop() {
        ChannelGateway gateway = activeGateway.getAndSet(null);
        if (gateway != null) {
            gateway.dispose();
        }
    }

    private static String handleSendTool(ChannelGateway gateway, ExtensionToolCall call) {
        JsonNode args = parseArguments(call);
        String text = textValue(args, "text");
        if (text == null) {
            throw new IllegalArgumentException("ambient_channel_send requires 'text'");
        }
        String endpointId = textValue(args, "endpointId");
        String recipientId = textValue(args, "recipientId");
        String scopeKey = textValue(args, "scopeKey");
        ChannelGateway.OutboundDeliveryReceipt receipt = gateway.dispatchOutbound(
                OutboundChannelMessage.builder()
                        .targetEndpointId(endpointId)
                        .recipientId(recipientId)
                        .scopeKey(scopeKey)
                        .text(text)
                        .build()
        );
        return JsonCodec.toJson(receipt);
    }

    private static String handleEscalateTool(ChannelGateway gateway, ExtensionToolCall call) {
        JsonNode args = parseArguments(call);
        String question = textValue(args, "question");
        if (question == null) {
            throw new IllegalArgumentException("ambient_escalate requires 'question'");
        }
        String title = textValue(args, "title");
        String reason = textValue(args, "reason");
        String waitId = textValue(args, "waitId");
        String scopeKey = textValue(args, "scopeKey");
        List<EscalationOptionCard.EscalationOption> options = new ArrayList<EscalationOptionCard.EscalationOption>();
        JsonNode optionsNode = args.get("options");
        if (optionsNode != null && optionsNode.isArray()) {
            for (int i = 0; i < optionsNode.size(); i++) {
                JsonNode item = optionsNode.get(i);
                if (item != null && item.isTextual()) {
                    String optId = item.asText();
                    options.add(EscalationOptionCard.EscalationOption.builder()
                            .optionId(optId)
                            .label(optId)
                            .build());
                } else if (item != null && item.isObject()) {
                    String optId = textValue(item, "optionId");
                    String label = textValue(item, "label");
                    if (optId != null) {
                        options.add(EscalationOptionCard.EscalationOption.builder()
                                .optionId(optId)
                                .label(label != null ? label : optId)
                                .description(textValue(item, "description"))
                                .recommended(item.has("recommended") && item.get("recommended").asBoolean(false))
                                .build());
                    }
                }
            }
        }
        EscalationOptionCard card = gateway.publishEscalationCard(EscalationOptionCard.builder()
                .title(title != null ? title : question)
                .question(question)
                .reason(reason)
                .waitId(waitId)
                .scopeKey(scopeKey)
                .options(options)
                .build());
        return JsonCodec.toJson(Collections.singletonMap("cardId", card.getCardId()));
    }

    private static JsonNode parseArguments(ExtensionToolCall call) {
        String raw = call == null ? null : call.getArguments();
        if (raw == null || raw.trim().isEmpty()) {
            return JsonCodec.createObjectNode();
        }
        return JsonCodec.readTree(raw);
    }

    private static String textValue(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || child.isMissingNode()) {
            return null;
        }
        String text = child.asText();
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }

    public static ExtensionToolSpec channelSendToolSpec() {
        return ExtensionToolSpec.builder()
                .name(TOOL_CHANNEL_SEND)
                .description("Send an outbound message via the ambient ChannelGateway to a target endpoint or channel.")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"endpointId\":{\"type\":\"string\",\"description\":\"Target endpoint identifier\"},\"recipientId\":{\"type\":\"string\",\"description\":\"Recipient identifier\"},\"scopeKey\":{\"type\":\"string\",\"description\":\"Optional Harness scope\"},\"text\":{\"type\":\"string\",\"description\":\"Message content to deliver\"}},\"required\":[\"text\"]}")
                .build();
    }

    public static ExtensionToolSpec escalateToolSpec() {
        return ExtensionToolSpec.builder()
                .name(TOOL_ESCALATE)
                .description("Publish a structured escalation option card to owner endpoints through ChannelGateway.")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"},\"question\":{\"type\":\"string\"},\"reason\":{\"type\":\"string\"},\"waitId\":{\"type\":\"string\"},\"scopeKey\":{\"type\":\"string\"},\"options\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},\"required\":[\"question\"]}")
                .build();
    }

    public static ExtensionCommandSpec statusCommandSpec() {
        return ExtensionCommandSpec.builder()
                .name(COMMAND_STATUS)
                .description("Inspect ambient ChannelGateway endpoints and SupervisoryAgenda queue counts.")
                .usage("/ambient-status")
                .build();
    }
}
