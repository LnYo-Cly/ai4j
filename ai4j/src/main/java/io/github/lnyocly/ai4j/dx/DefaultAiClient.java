package io.github.lnyocly.ai4j.dx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import io.github.lnyocly.ai4j.client.ModelCall;
import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.client.ModelClients;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.codec.JsonCodecException;
import io.github.lnyocly.ai4j.exception.Ai4jException;
import io.github.lnyocly.ai4j.middleware.MiddlewareModelClient;
import io.github.lnyocly.ai4j.middleware.ModelMiddleware;
import io.github.lnyocly.ai4j.model.Block;
import io.github.lnyocly.ai4j.model.CallExecutionOptions;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.ModelUsage;
import io.github.lnyocly.ai4j.model.ReasoningItem;
import io.github.lnyocly.ai4j.model.TextBlock;
import io.github.lnyocly.ai4j.model.ToolCallItem;
import io.github.lnyocly.ai4j.model.ToolDeclaration;
import io.github.lnyocly.ai4j.model.ToolResultItem;
import io.github.lnyocly.ai4j.model.ToolResultValue;
import io.github.lnyocly.ai4j.preset.ProviderPreset;
import io.github.lnyocly.ai4j.preset.ProviderPresets;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.Platform;
import io.github.lnyocly.ai4j.stream.AiReply;
import io.github.lnyocly.ai4j.stream.AiStream;
import io.github.lnyocly.ai4j.stream.AiStreamEvent;
import io.github.lnyocly.ai4j.stream.AiStreamEventType;
import io.github.lnyocly.ai4j.stream.DefaultAiStream;
import io.github.lnyocly.ai4j.stream.DispatcherProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Default {@link AiClient} implementation routing {@code "provider:model"} specs
 * through {@link ProviderPresets}, {@link ModelClient}, {@link ModelMiddleware},
 * {@link InstanceToolRegistry}, and {@link DefaultAiStream}.
 */
public final class DefaultAiClient implements AiClient {

    private final Map<String, ModelClient> clientsByProvider;
    private final ModelClient defaultClient;
    private final boolean hasExplicitDefaultClient;
    private final String defaultModel;
    private final Configuration configuration;
    private final List<ModelMiddleware> middlewares;
    private final AiMemoryStore memoryStore;
    private final List<Object> defaultToolBeans;
    private final int defaultMaxToolRounds;
    private final int defaultMaxSelfHealRetries;
    private final Executor streamExecutor;

    private DefaultAiClient(Builder builder) {
        this.configuration = builder.configuration == null ? new Configuration() : builder.configuration;
        this.middlewares = builder.middlewares.isEmpty()
                ? Collections.<ModelMiddleware>emptyList()
                : Collections.unmodifiableList(new ArrayList<ModelMiddleware>(builder.middlewares));
        this.clientsByProvider = new ConcurrentHashMap<String, ModelClient>();
        for (Map.Entry<String, ModelClient> entry : builder.clientsByProvider.entrySet()) {
            this.clientsByProvider.put(entry.getKey(), wrapWithMiddlewares(entry.getValue()));
        }
        this.hasExplicitDefaultClient = builder.defaultClient != null;
        this.defaultClient = builder.defaultClient != null
                ? wrapWithMiddlewares(builder.defaultClient)
                : firstRegisteredClient();
        this.defaultModel = builder.defaultModel;
        this.memoryStore = builder.memoryStore == null ? new InMemoryAiMemoryStore() : builder.memoryStore;
        this.defaultToolBeans = builder.defaultToolBeans.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<Object>(builder.defaultToolBeans));
        this.defaultMaxToolRounds = Math.max(1, builder.defaultMaxToolRounds);
        this.defaultMaxSelfHealRetries = Math.max(0, builder.defaultMaxSelfHealRetries);
        this.streamExecutor = builder.streamExecutor == null
                ? DispatcherProvider.executor() : builder.streamExecutor;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Discover configured provider API keys from environment variables via
     * {@link ProviderPresets}.
     */
    public static DefaultAiClient fromEnv() {
        return fromEnv(new Configuration());
    }

    /** Discover configured providers from environment variables over a shared {@link Configuration}. */
    public static DefaultAiClient fromEnv(Configuration configuration) {
        List<ProviderPreset> available = ProviderPresets.availableFromEnv();
        if (available.isEmpty()) {
            throw new Ai4jException("No provider API keys found in environment variables");
        }
        Builder builder = builder().configuration(configuration);
        for (ProviderPreset preset : available) {
            ModelClient client = ModelClients.of(preset.toEndpointFromEnv(), configuration);
            builder.client(preset.id(), client);
        }
        return builder.build();
    }

    @Override
    public AiPromptSpec prompt() {
        return new PromptSpec(defaultModel);
    }

    @Override
    public AiPromptSpec prompt(String modelSpec) {
        return new PromptSpec(modelSpec);
    }

    @Override
    public <T> T service(Class<T> serviceInterface) {
        return AiServiceProxyFactory.create(serviceInterface, this);
    }

    @Override
    public <T> T service(Class<T> serviceInterface, Object... toolBeans) {
        return AiServiceProxyFactory.create(serviceInterface, this, toolBeans);
    }

    private ModelClient wrapWithMiddlewares(ModelClient client) {
        if (client == null || middlewares.isEmpty()) {
            return client;
        }
        return MiddlewareModelClient.wrap(client, middlewares);
    }

    private ModelClient firstRegisteredClient() {
        if (clientsByProvider.isEmpty()) {
            return null;
        }
        return clientsByProvider.values().iterator().next();
    }

    private ResolvedTarget resolveTarget(String rawModelSpec) {
        String effectiveSpec = (rawModelSpec == null || rawModelSpec.trim().isEmpty())
                ? defaultModel : rawModelSpec;
        if (effectiveSpec == null || effectiveSpec.trim().isEmpty()) {
            throw new IllegalArgumentException("model is required");
        }
        ProviderPresets.ModelSpec spec = ProviderPresets.parseModelSpec(effectiveSpec);
        String providerId = spec.getProviderId();
        if (providerId != null) {
            ModelClient registered = clientsByProvider.get(providerId);
            if (registered != null) {
                return new ResolvedTarget(registered, spec.getModel());
            }
            boolean unnamedExplicitDefault = hasExplicitDefaultClient
                    && (defaultClient.endpoint() == null || defaultClient.endpoint().getName() == null);
            if (hasExplicitDefaultClient && defaultClient.endpoint() != null
                    && providerId.equalsIgnoreCase(defaultClient.endpoint().getName())) {
                return new ResolvedTarget(defaultClient, spec.getModel());
            }
            if (unnamedExplicitDefault && clientsByProvider.isEmpty()) {
                return new ResolvedTarget(defaultClient, spec.getModel());
            }
            if (spec.getPreset() != null && spec.getPreset().hasEnvApiKey()) {
                ModelClient created = wrapWithMiddlewares(
                        ModelClients.of(spec.getPreset().toEndpointFromEnv(), configuration));
                clientsByProvider.putIfAbsent(providerId, created);
                return new ResolvedTarget(clientsByProvider.get(providerId), spec.getModel());
            }
            if (unnamedExplicitDefault) {
                return new ResolvedTarget(defaultClient, spec.getModel());
            }
            throw new IllegalStateException("No ModelClient or environment API key configured for provider '"
                    + providerId + "'");
        }
        if (defaultClient != null) {
            return new ResolvedTarget(defaultClient, spec.getModel());
        }
        throw new IllegalStateException("No default ModelClient configured for model '" + spec.getModel() + "'");
    }

    static String interpolate(String template, Map<String, ?> variables) {
        if (template == null || variables == null || variables.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, ?> entry : variables.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String replacement = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            result = result.replace("{{" + entry.getKey() + "}}", replacement);
            result = result.replace("{" + entry.getKey() + "}", replacement);
        }
        return result;
    }

    private static final class ResolvedTarget {

        private final ModelClient client;
        private final String model;

        private ResolvedTarget(ModelClient client, String model) {
            this.client = client;
            this.model = model;
        }
    }

    private final class PromptSpec implements AiPromptSpec {

        private String modelSpec;
        private String sessionId;
        private String systemPrompt;
        private String userPrompt;
        private final List<ModelItem> explicitItems = new ArrayList<ModelItem>();
        private final List<Attachment> attachments = new ArrayList<Attachment>();
        private final InstanceToolRegistry toolRegistry = new InstanceToolRegistry();
        private final List<ToolDeclaration> extraToolDeclarations = new ArrayList<ToolDeclaration>();
        private Double temperature;
        private Double topP;
        private Integer maxOutputTokens;
        private int maxToolRounds = defaultMaxToolRounds;
        private int maxSelfHealRetries = defaultMaxSelfHealRetries;
        private final Map<String, Object> providerOptions = new LinkedHashMap<String, Object>();
        private final Map<String, Object> applicationMetadata = new LinkedHashMap<String, Object>();
        private CallExecutionOptions execution;

        private PromptSpec(String modelSpec) {
            this.modelSpec = modelSpec;
            for (Object bean : defaultToolBeans) {
                this.toolRegistry.register(bean);
            }
        }

        @Override
        public AiPromptSpec model(String modelSpec) {
            this.modelSpec = modelSpec;
            return this;
        }

        @Override
        public AiPromptSpec sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        @Override
        public AiPromptSpec system(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        @Override
        public AiPromptSpec user(String userPrompt) {
            this.userPrompt = userPrompt;
            return this;
        }

        @Override
        public AiPromptSpec user(String userTemplate, Map<String, ?> variables) {
            this.userPrompt = interpolate(userTemplate, variables);
            return this;
        }

        @Override
        public AiPromptSpec message(Message message) {
            if (message != null) {
                this.explicitItems.add(message);
            }
            return this;
        }

        @Override
        public AiPromptSpec items(List<ModelItem> items) {
            if (items != null) {
                this.explicitItems.addAll(items);
            }
            return this;
        }

        @Override
        public AiPromptSpec attach(Attachment... attachments) {
            if (attachments != null) {
                for (Attachment attachment : attachments) {
                    if (attachment != null) {
                        this.attachments.add(attachment);
                    }
                }
            }
            return this;
        }

        @Override
        public AiPromptSpec attachments(List<Attachment> attachments) {
            if (attachments != null) {
                for (Attachment attachment : attachments) {
                    if (attachment != null) {
                        this.attachments.add(attachment);
                    }
                }
            }
            return this;
        }

        @Override
        public AiPromptSpec tools(Object... toolBeans) {
            this.toolRegistry.registerAll(toolBeans);
            return this;
        }

        @Override
        public AiPromptSpec toolRegistry(InstanceToolRegistry registry) {
            if (registry != null) {
                this.toolRegistry.registerAll(registry);
            }
            return this;
        }

        @Override
        public AiPromptSpec tool(ToolDeclaration declaration) {
            if (declaration != null) {
                this.extraToolDeclarations.add(declaration);
            }
            return this;
        }

        @Override
        public AiPromptSpec temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        @Override
        public AiPromptSpec topP(Double topP) {
            this.topP = topP;
            return this;
        }

        @Override
        public AiPromptSpec maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        @Override
        public AiPromptSpec maxToolRounds(int maxToolRounds) {
            this.maxToolRounds = Math.max(1, maxToolRounds);
            return this;
        }

        @Override
        public AiPromptSpec maxSelfHealRetries(int maxSelfHealRetries) {
            this.maxSelfHealRetries = Math.max(0, maxSelfHealRetries);
            return this;
        }

        @Override
        public AiPromptSpec providerOption(String key, Object value) {
            if (key != null) {
                this.providerOptions.put(key, value);
            }
            return this;
        }

        @Override
        public AiPromptSpec metadata(String key, Object value) {
            if (key != null) {
                this.applicationMetadata.put(key, value);
            }
            return this;
        }

        @Override
        public AiPromptSpec execution(CallExecutionOptions execution) {
            this.execution = execution;
            return this;
        }

        @Override
        public AiReply<String> call() {
            return executeBlocking(null);
        }

        @Override
        public <T> AiReply<T> call(Class<T> responseType) {
            Objects.requireNonNull(responseType, "responseType is required");
            return executeBlocking(JsonCodec.protocolMapper().constructType(responseType));
        }

        @Override
        public <T> AiReply<T> call(TypeReference<T> typeReference) {
            Objects.requireNonNull(typeReference, "typeReference is required");
            return executeBlocking(JsonCodec.protocolMapper().constructType(typeReference));
        }

        @Override
        public <T> AiReply<T> call(JavaType javaType) {
            Objects.requireNonNull(javaType, "javaType is required");
            return executeBlocking(javaType);
        }

        @Override
        public AiStream<String> stream() {
            return executeStreaming(null);
        }

        @Override
        public <T> AiStream<T> stream(Class<T> responseType) {
            Objects.requireNonNull(responseType, "responseType is required");
            return executeStreaming(JsonCodec.protocolMapper().constructType(responseType));
        }

        @Override
        public <T> AiStream<T> stream(TypeReference<T> typeReference) {
            Objects.requireNonNull(typeReference, "typeReference is required");
            return executeStreaming(JsonCodec.protocolMapper().constructType(typeReference));
        }

        @Override
        public <T> AiStream<T> stream(JavaType javaType) {
            Objects.requireNonNull(javaType, "javaType is required");
            return executeStreaming(javaType);
        }

        private List<ToolDeclaration> allTools() {
            List<ToolDeclaration> all = new ArrayList<ToolDeclaration>();
            all.addAll(toolRegistry.declarations());
            all.addAll(extraToolDeclarations);
            return all;
        }

        private List<ModelItem> buildConversationTurnItems(List<ModelItem> memoryHistory) {
            List<ModelItem> items = new ArrayList<ModelItem>();
            if (memoryHistory != null) {
                items.addAll(memoryHistory);
            }
            items.addAll(explicitItems);
            if (userPrompt != null || !attachments.isEmpty()) {
                Message.Builder userMsg = Message.user();
                if (userPrompt != null) {
                    userMsg.text(userPrompt);
                }
                for (Attachment attachment : attachments) {
                    userMsg.block(attachment.toBlock());
                }
                items.add(userMsg.build());
            }
            return items;
        }

        private ModelRequest buildRequest(String model, List<ModelItem> conversationItems,
                                          JavaType structuredType, boolean stream) {
            ModelRequest.Builder rb = ModelRequest.builder()
                    .model(model)
                    .stream(Boolean.valueOf(stream))
                    .temperature(temperature)
                    .topP(topP)
                    .maxOutputTokens(maxOutputTokens)
                    .providerOptions(providerOptions)
                    .execution(execution);

            Map<String, Object> meta = new LinkedHashMap<String, Object>(applicationMetadata);
            if (sessionId != null && !sessionId.trim().isEmpty()) {
                meta.put("sessionId", sessionId);
            }
            if (!meta.isEmpty()) {
                rb.applicationMetadata(meta);
            }

            String effectiveSystem = systemPrompt;
            if (structuredType != null && !isTextType(structuredType)) {
                String schemaJson = JsonCodec.toJson(DxJsonSchema.schemaFor(structuredType));
                String schemaInstruction = "Respond ONLY with valid JSON matching this schema:\n" + schemaJson;
                effectiveSystem = (effectiveSystem == null || effectiveSystem.trim().isEmpty())
                        ? schemaInstruction
                        : (effectiveSystem + "\n\n" + schemaInstruction);
            }
            if (effectiveSystem != null && !effectiveSystem.trim().isEmpty()) {
                rb.item(Message.system().text(effectiveSystem).build());
            }
            rb.items(conversationItems);
            rb.tools(allTools());
            return rb.build();
        }

        @SuppressWarnings("unchecked")
        private <T> AiReply<T> executeBlocking(JavaType targetType) {
            ResolvedTarget target = resolveTarget(modelSpec);
            List<ModelItem> memoryBefore = sessionId == null ? Collections.<ModelItem>emptyList()
                    : memoryStore.load(sessionId);
            int initialMemoryCount = memoryBefore.size();
            List<ModelItem> conversation = buildConversationTurnItems(memoryBefore);
            List<Object> collectedDetails = new ArrayList<Object>();
            UsageTotals usageTotals = new UsageTotals();

            ModelResult lastResult = null;
            for (int round = 0; round < maxToolRounds; round++) {
                ModelRequest req = buildRequest(target.model, conversation, targetType, false);
                lastResult = target.client.create(req);
                usageTotals.add(lastResult.getUsage());

                List<ToolCallItem> executableCalls = extractExecutableToolCalls(lastResult);
                conversation.addAll(lastResult.getItems());

                if (executableCalls.isEmpty() || round == maxToolRounds - 1) {
                    break;
                }
                for (ToolCallItem call : executableCalls) {
                    InstanceToolRegistry.ToolExecutionOutcome outcome =
                            toolRegistry.execute(call, ToolProgress.noop());
                    if (outcome.getDetails() != null) {
                        collectedDetails.add(outcome.getDetails());
                    }
                    conversation.add(toToolResultItem(call, outcome));
                }
            }

            T data;
            if (targetType == null || isTextType(targetType)) {
                data = (T) extractText(lastResult);
            } else {
                StructuredParseResult<T> parsed = parseWithSelfHeal(
                        target, conversation, lastResult, targetType, usageTotals, null);
                lastResult = parsed.result;
                data = parsed.value;
            }

            if (sessionId != null && !sessionId.trim().isEmpty() && conversation.size() > initialMemoryCount) {
                memoryStore.append(sessionId, conversation.subList(initialMemoryCount, conversation.size()));
            }
            return buildReply(lastResult, data, usageTotals.toUsage(), collectedDetails);
        }

        private <T> AiStream<T> executeStreaming(final JavaType targetType) {
            final ResolvedTarget target = resolveTarget(modelSpec);
            final DefaultAiStream<T> stream = DefaultAiStream.create();
            final DefaultAiStream.BlockTracker tracker = new DefaultAiStream.BlockTracker(stream);
            stream.emitStart();

            streamExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        runStreamingLoop(target, targetType, stream, tracker);
                    } catch (Throwable failure) {
                        if (!stream.isCancelled()) {
                            stream.fail(failure);
                        }
                    }
                }
            });
            return stream;
        }

        @SuppressWarnings("unchecked")
        private <T> void runStreamingLoop(ResolvedTarget target,
                                          JavaType targetType,
                                          final DefaultAiStream<T> stream,
                                          final DefaultAiStream.BlockTracker tracker) {
            List<ModelItem> memoryBefore = sessionId == null ? Collections.<ModelItem>emptyList()
                    : memoryStore.load(sessionId);
            int initialMemoryCount = memoryBefore.size();
            List<ModelItem> conversation = buildConversationTurnItems(memoryBefore);
            List<Object> collectedDetails = new ArrayList<Object>();
            UsageTotals usageTotals = new UsageTotals();
            ModelResult lastResult = null;

            for (int round = 0; round < maxToolRounds; round++) {
                if (stream.isCancelled()) {
                    return;
                }
                ModelRequest req = buildRequest(target.model, conversation, targetType, true);
                final ModelCall call = target.client.createStream(req, new ModelEventListener() {
                    @Override
                    public void onEvent(ModelEvent event) {
                        if (!stream.isCancelled() && event != null) {
                            tracker.accept(event);
                        }
                    }
                });
                stream.bindCancel(new Runnable() {
                    @Override
                    public void run() {
                        call.cancel();
                    }
                });
                if (stream.isCancelled()) {
                    call.cancel();
                    return;
                }

                lastResult = call.await();
                if (stream.isCancelled() || call.status() == ModelCallStatus.CANCELLED) {
                    return;
                }
                tracker.closeActiveBlock();
                usageTotals.add(lastResult.getUsage());

                List<ToolCallItem> executableCalls = extractExecutableToolCalls(lastResult);
                conversation.addAll(lastResult.getItems());

                if (executableCalls.isEmpty() || round == maxToolRounds - 1) {
                    break;
                }
                for (final ToolCallItem tc : executableCalls) {
                    if (stream.isCancelled()) {
                        return;
                    }
                    stream.emit(AiStreamEvent.builder(AiStreamEventType.TOOL_EXEC_START)
                            .callId(tc.getCallId())
                            .toolName(tc.getName())
                            .partial(tracker.snapshotPartial())
                            .build());
                    ToolProgress progress = new ToolProgress() {
                        @Override
                        public void emit(String message) {
                            emit(message, null);
                        }

                        @Override
                        public void emit(String message, Object details) {
                            if (!stream.isCancelled()) {
                                stream.emit(AiStreamEvent.builder(AiStreamEventType.TOOL_EXEC_PROGRESS)
                                        .callId(tc.getCallId())
                                        .toolName(tc.getName())
                                        .delta(message)
                                        .toolDetails(details)
                                        .partial(tracker.snapshotPartial())
                                        .build());
                            }
                        }
                    };
                    InstanceToolRegistry.ToolExecutionOutcome outcome = toolRegistry.execute(tc, progress);
                    if (outcome.getDetails() != null) {
                        collectedDetails.add(outcome.getDetails());
                    }
                    stream.emit(AiStreamEvent.builder(AiStreamEventType.TOOL_EXEC_END)
                            .callId(tc.getCallId())
                            .toolName(tc.getName())
                            .content(outcome.getContent())
                            .toolDetails(outcome.getDetails())
                            .toolError(outcome.isError())
                            .partial(tracker.snapshotPartial())
                            .build());
                    conversation.add(toToolResultItem(tc, outcome));
                }
            }

            T data;
            if (targetType == null || isTextType(targetType)) {
                data = (T) extractText(lastResult);
            } else {
                StructuredParseResult<T> parsed = parseWithSelfHeal(
                        target, conversation, lastResult, targetType, usageTotals, stream);
                if (stream.isCancelled()) {
                    return;
                }
                lastResult = parsed.result;
                data = parsed.value;
            }

            if (sessionId != null && !sessionId.trim().isEmpty() && conversation.size() > initialMemoryCount) {
                memoryStore.append(sessionId, conversation.subList(initialMemoryCount, conversation.size()));
            }
            stream.complete(buildReply(lastResult, data, usageTotals.toUsage(), collectedDetails));
        }

        private <T> StructuredParseResult<T> parseWithSelfHeal(ResolvedTarget target,
                                                               List<ModelItem> conversation,
                                                               ModelResult currentResult,
                                                               JavaType targetType,
                                                               UsageTotals usageTotals,
                                                               DefaultAiStream<?> stream) {
            ModelResult activeResult = currentResult;
            int attempt = 0;
            while (true) {
                if (stream != null && stream.isCancelled()) {
                    return new StructuredParseResult<T>(activeResult, null);
                }
                String text = extractText(activeResult);
                try {
                    String payload = JsonCodec.extractJsonPayload(text);
                    if (payload == null || payload.trim().isEmpty()) {
                        throw new JsonCodecException("Structured output did not contain a JSON payload");
                    }
                    T value = parseJsonLenient(payload, targetType);
                    if (value == null) {
                        throw new JsonCodecException("Structured output parsed to null");
                    }
                    return new StructuredParseResult<T>(activeResult, value);
                } catch (RuntimeException parseFailure) {
                    if (attempt >= maxSelfHealRetries) {
                        throw new Ai4jException("Failed to parse structured output into " + targetType
                                + ": " + parseFailure.getMessage(), parseFailure);
                    }
                    if (stream != null && stream.isCancelled()) {
                        return new StructuredParseResult<T>(activeResult, null);
                    }
                    attempt++;
                    conversation.add(Message.user()
                            .text("The previous response could not be parsed as valid JSON for the required schema ("
                                    + parseFailure.getMessage()
                                    + "). Reply ONLY with valid JSON matching the schema.")
                            .build());
                    ModelRequest repairReq = buildRequest(target.model, conversation, targetType, false);
                    activeResult = target.client.create(repairReq);
                    usageTotals.add(activeResult.getUsage());
                    conversation.addAll(activeResult.getItems());
                }
            }
        }

        private List<ToolCallItem> extractExecutableToolCalls(ModelResult result) {
            if (result == null || toolRegistry.isEmpty()) {
                return Collections.emptyList();
            }
            List<ToolCallItem> calls = new ArrayList<ToolCallItem>();
            for (ModelItem item : result.getItems()) {
                if (item instanceof ToolCallItem && toolRegistry.hasTool(((ToolCallItem) item).getName())) {
                    calls.add((ToolCallItem) item);
                } else if (item instanceof Message) {
                    for (Block block : ((Message) item).getBlocks()) {
                        if (block instanceof io.github.lnyocly.ai4j.model.ToolCallBlock) {
                            io.github.lnyocly.ai4j.model.ToolCallBlock tb =
                                    (io.github.lnyocly.ai4j.model.ToolCallBlock) block;
                            if (toolRegistry.hasTool(tb.getName())) {
                                calls.add(ToolCallItem.of(tb.getValue(), tb.getCallId(),
                                        tb.getApplicationMetadata(), tb.getProtocolData()));
                            }
                        }
                    }
                }
            }
            return calls;
        }

        private <T> T parseJsonLenient(String payload, JavaType targetType) {
            try {
                return JsonCodec.lenientMapper().readValue(payload, targetType);
            } catch (IOException ex) {
                throw new JsonCodecException("Failed to deserialize JSON into " + targetType + ": " + ex.getMessage(), ex);
            }
        }

        private ToolResultItem toToolResultItem(ToolCallItem call,
                                                 InstanceToolRegistry.ToolExecutionOutcome outcome) {
            Map<String, Object> protoData = Collections.<String, Object>singletonMap("name", call.getName());
            ToolResultValue value = ToolResultValue.of(
                    call.getCallId(),
                    Collections.<Block>singletonList(TextBlock.of(outcome.getContent())),
                    outcome.isError(),
                    null,
                    protoData);
            return ToolResultItem.of(value, null, null, protoData);
        }

        private boolean isTextType(JavaType type) {
            Class<?> raw = type.getRawClass();
            return raw == String.class || raw == Void.class || raw == Object.class;
        }

        private String extractText(ModelResult result) {
            if (result == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (ModelItem item : result.getItems()) {
                if (item instanceof Message) {
                    String text = ((Message) item).textContent();
                    if (text != null) {
                        sb.append(text);
                    }
                }
            }
            return sb.toString();
        }

        private <T> AiReply<T> buildReply(ModelResult lastResult, T data,
                                          ModelUsage usage, List<Object> details) {
            AiReply<T> base = AiReply.fromModelResult(lastResult, data);
            AiReply.Builder<T> builder = AiReply.<T>builder()
                    .data(data)
                    .text(base.getText())
                    .thinking(base.getThinking())
                    .message(base.getMessage())
                    .toolCalls(base.getToolCalls())
                    .items(base.getItems())
                    .modelResult(lastResult)
                    .usage(usage != null ? usage : base.getUsage())
                    .stopReason(base.getStopReason())
                    .responseId(base.getResponseId())
                    .toolDetails(details);
            return builder.build();
        }
    }

    private static final class StructuredParseResult<T> {

        private final ModelResult result;
        private final T value;

        private StructuredParseResult(ModelResult result, T value) {
            this.result = result;
            this.value = value;
        }
    }

    private static final class UsageTotals {

        private long inputTokens;
        private long outputTokens;
        private long totalTokens;
        private long cachedInputTokens;
        private long reasoningTokens;
        private boolean hasInput;
        private boolean hasOutput;
        private boolean hasTotal;
        private boolean hasCached;
        private boolean hasReasoning;

        private void add(ModelUsage usage) {
            if (usage == null) {
                return;
            }
            if (usage.getInputTokens() != ModelUsage.UNKNOWN) {
                inputTokens += usage.getInputTokens();
                hasInput = true;
            }
            if (usage.getOutputTokens() != ModelUsage.UNKNOWN) {
                outputTokens += usage.getOutputTokens();
                hasOutput = true;
            }
            if (usage.getTotalTokens() != ModelUsage.UNKNOWN) {
                totalTokens += usage.getTotalTokens();
                hasTotal = true;
            }
            if (usage.getCachedInputTokens() != ModelUsage.UNKNOWN) {
                cachedInputTokens += usage.getCachedInputTokens();
                hasCached = true;
            }
            if (usage.getReasoningTokens() != ModelUsage.UNKNOWN) {
                reasoningTokens += usage.getReasoningTokens();
                hasReasoning = true;
            }
        }

        private ModelUsage toUsage() {
            if (!hasInput && !hasOutput && !hasTotal && !hasCached && !hasReasoning) {
                return null;
            }
            ModelUsage.Builder builder = ModelUsage.builder();
            if (hasInput) {
                builder.inputTokens(inputTokens);
            }
            if (hasOutput) {
                builder.outputTokens(outputTokens);
            }
            if (hasTotal) {
                builder.totalTokens(totalTokens);
            }
            if (hasCached) {
                builder.cachedInputTokens(cachedInputTokens);
            }
            if (hasReasoning) {
                builder.reasoningTokens(reasoningTokens);
            }
            return builder.build();
        }
    }

    /** Fluent builder. */
    public static final class Builder {

        private final Map<String, ModelClient> clientsByProvider = new LinkedHashMap<String, ModelClient>();
        private ModelClient defaultClient;
        private String defaultModel;
        private Configuration configuration;
        private final List<ModelMiddleware> middlewares = new ArrayList<ModelMiddleware>();
        private AiMemoryStore memoryStore;
        private final List<Object> defaultToolBeans = new ArrayList<Object>();
        private int defaultMaxToolRounds = 8;
        private int defaultMaxSelfHealRetries = 2;
        private Executor streamExecutor;

        private Builder() {
        }

        public Builder configuration(Configuration configuration) {
            this.configuration = configuration;
            return this;
        }

        public Builder defaultClient(ModelClient client) {
            this.defaultClient = Objects.requireNonNull(client, "client is required");
            return this;
        }

        public Builder defaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
            return this;
        }

        public Builder client(String providerId, ModelClient client) {
            Objects.requireNonNull(providerId, "providerId is required");
            Objects.requireNonNull(client, "client is required");
            this.clientsByProvider.put(providerId.trim().toLowerCase(Locale.ROOT), client);
            return this;
        }

        public Builder client(Platform platform, ModelClient client) {
            Objects.requireNonNull(platform, "platform is required");
            return client(platform.id(), client);
        }

        public Builder preset(ProviderPreset preset, String apiKey) {
            return preset(preset, apiKey, null);
        }

        public Builder preset(ProviderPreset preset, String apiKey, String baseUrlOverride) {
            Objects.requireNonNull(preset, "preset is required");
            Configuration cfg = configuration == null ? new Configuration() : configuration;
            Endpoint endpoint = preset.toEndpoint(apiKey, baseUrlOverride);
            return client(preset.id(), ModelClients.of(endpoint, cfg));
        }

        public Builder middleware(ModelMiddleware middleware) {
            if (middleware != null) {
                this.middlewares.add(middleware);
            }
            return this;
        }

        public Builder middlewares(ModelMiddleware... middlewares) {
            if (middlewares != null) {
                this.middlewares.addAll(Arrays.asList(middlewares));
            }
            return this;
        }

        public Builder memoryStore(AiMemoryStore memoryStore) {
            this.memoryStore = memoryStore;
            return this;
        }

        public Builder tools(Object... toolBeans) {
            if (toolBeans != null) {
                for (Object bean : toolBeans) {
                    if (bean != null) {
                        this.defaultToolBeans.add(bean);
                    }
                }
            }
            return this;
        }

        public Builder defaultMaxToolRounds(int defaultMaxToolRounds) {
            this.defaultMaxToolRounds = defaultMaxToolRounds;
            return this;
        }

        public Builder defaultMaxSelfHealRetries(int defaultMaxSelfHealRetries) {
            this.defaultMaxSelfHealRetries = defaultMaxSelfHealRetries;
            return this;
        }

        public Builder streamExecutor(Executor streamExecutor) {
            this.streamExecutor = streamExecutor;
            return this;
        }

        public DefaultAiClient build() {
            return new DefaultAiClient(this);
        }
    }
}
