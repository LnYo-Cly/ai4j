package io.github.lnyocly.ai4j.middleware;

import io.github.lnyocly.ai4j.client.ModelCall;
import io.github.lnyocly.ai4j.client.ModelCallException;
import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.model.Block;
import io.github.lnyocly.ai4j.model.EventDeltaKind;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.ModelUsage;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ToolCallBlock;
import io.github.lnyocly.ai4j.model.ToolCallItem;
import io.github.lnyocly.ai4j.stream.DispatcherProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.UnaryOperator;

/**
 * Asynchronous shadow-evaluation middleware mirroring a configurable fraction of
 * redacted production requests to a candidate {@link ModelClient} and producing
 * comparison reports without affecting the primary response.
 */
public final class ShadowEvalMiddleware implements ModelMiddleware {

    public static final class ShadowEvalReport {

        private final String primaryModel;
        private final String candidateModel;
        private final Protocol primaryProtocol;
        private final Protocol candidateProtocol;
        private final long primaryLatencyMs;
        private final long candidateLatencyMs;
        private final String primaryText;
        private final String candidateText;
        private final boolean textMatch;
        private final boolean toolNamesMatch;
        private final ModelUsage primaryUsage;
        private final ModelUsage candidateUsage;
        private final boolean candidateSuccess;
        private final Throwable candidateError;

        private ShadowEvalReport(String primaryModel, String candidateModel,
                                 Protocol primaryProtocol, Protocol candidateProtocol,
                                 long primaryLatencyMs, long candidateLatencyMs,
                                 String primaryText, String candidateText,
                                 boolean textMatch, boolean toolNamesMatch,
                                 ModelUsage primaryUsage, ModelUsage candidateUsage,
                                 boolean candidateSuccess, Throwable candidateError) {
            this.primaryModel = primaryModel;
            this.candidateModel = candidateModel;
            this.primaryProtocol = primaryProtocol;
            this.candidateProtocol = candidateProtocol;
            this.primaryLatencyMs = primaryLatencyMs;
            this.candidateLatencyMs = candidateLatencyMs;
            this.primaryText = primaryText;
            this.candidateText = candidateText;
            this.textMatch = textMatch;
            this.toolNamesMatch = toolNamesMatch;
            this.primaryUsage = primaryUsage;
            this.candidateUsage = candidateUsage;
            this.candidateSuccess = candidateSuccess;
            this.candidateError = candidateError;
        }

        public String getPrimaryModel() {
            return primaryModel;
        }

        public String getCandidateModel() {
            return candidateModel;
        }

        public Protocol getPrimaryProtocol() {
            return primaryProtocol;
        }

        public Protocol getCandidateProtocol() {
            return candidateProtocol;
        }

        public long getPrimaryLatencyMs() {
            return primaryLatencyMs;
        }

        public long getCandidateLatencyMs() {
            return candidateLatencyMs;
        }

        public String getPrimaryText() {
            return primaryText;
        }

        public String getCandidateText() {
            return candidateText;
        }

        public boolean isTextMatch() {
            return textMatch;
        }

        public boolean isToolNamesMatch() {
            return toolNamesMatch;
        }

        public ModelUsage getPrimaryUsage() {
            return primaryUsage;
        }

        public ModelUsage getCandidateUsage() {
            return candidateUsage;
        }

        public boolean isCandidateSuccess() {
            return candidateSuccess;
        }

        public Throwable getCandidateError() {
            return candidateError;
        }
    }

    private final ModelClient candidateClient;
    private final String candidateModel;
    private final double sampleRate;
    private final DoubleSupplier sampler;
    private final Executor executor;
    private final UnaryOperator<ModelRequest> redactor;
    private final Consumer<ShadowEvalReport> reportConsumer;
    private final List<ShadowEvalReport> recordedReports = new CopyOnWriteArrayList<ShadowEvalReport>();

    private ShadowEvalMiddleware(Builder builder) {
        this.candidateClient = Objects.requireNonNull(builder.candidateClient, "candidateClient is required");
        this.candidateModel = builder.candidateModel;
        this.sampleRate = Math.max(0.0d, Math.min(1.0d, builder.sampleRate));
        this.sampler = builder.sampler == null ? new DoubleSupplier() {
            @Override
            public double getAsDouble() {
                return ThreadLocalRandom.current().nextDouble();
            }
        } : builder.sampler;
        this.executor = builder.executor == null ? DispatcherProvider.executor() : builder.executor;
        this.redactor = builder.redactor == null ? defaultRedactor() : builder.redactor;
        this.reportConsumer = builder.reportConsumer;
    }

    public static Builder builder(ModelClient candidateClient) {
        return new Builder(candidateClient);
    }

    @Override
    public ModelResult intercept(final ModelCallContext context, Next next) throws ModelCallException {
        long startNs = System.nanoTime();
        final ModelResult primaryResult = next.proceed(context);
        final long primaryLatencyMs = Math.max(0L, (System.nanoTime() - startNs) / 1_000_000L);

        if (shouldSample()) {
            final ModelRequest shadowRequest = prepareShadowRequest(context.request());
            final String primaryModelName = context.request().getModel();
            final Protocol primaryProto = context.endpoint().getProtocol();
            final String primaryText = extractText(primaryResult);
            final List<String> primaryTools = extractToolNames(primaryResult);
            final ModelUsage primaryUsage = primaryResult.getUsage();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    runShadow(shadowRequest, primaryModelName, primaryProto,
                            primaryText, primaryTools, primaryUsage, primaryLatencyMs);
                }
            });
        }
        return primaryResult;
    }

    @Override
    public ModelCall interceptStream(final ModelCallContext context,
                                     final ModelEventListener listener,
                                     StreamNext next) {
        if (!shouldSample()) {
            return next.proceed(context, listener);
        }
        final long startNs = System.nanoTime();
        final ModelRequest shadowRequest = prepareShadowRequest(context.request());
        final String primaryModelName = context.request().getModel();
        final Protocol primaryProto = context.endpoint().getProtocol();
        final StringBuilder textBuf = new StringBuilder();
        final StringBuilder fallbackItemTextBuf = new StringBuilder();
        final List<String> toolNames = Collections.synchronizedList(new ArrayList<String>());
        final AtomicReference<ModelUsage> usageRef = new AtomicReference<ModelUsage>();
        final AtomicBoolean dispatched = new AtomicBoolean(false);

        return next.proceed(context, new ModelEventListener() {
            @Override
            public void onEvent(ModelEvent event) {
                if (event != null) {
                    collectStreamObservation(event, textBuf, fallbackItemTextBuf, toolNames, usageRef);
                    if (event.getType() == ModelEventType.DONE && dispatched.compareAndSet(false, true)) {
                        final long primaryLatencyMs = Math.max(0L, (System.nanoTime() - startNs) / 1_000_000L);
                        final String primaryText = textBuf.length() > 0
                                ? textBuf.toString() : fallbackItemTextBuf.toString();
                        final List<String> primaryTools = new ArrayList<String>(toolNames);
                        final ModelUsage primaryUsage = usageRef.get();
                        executor.execute(new Runnable() {
                            @Override
                            public void run() {
                                runShadow(shadowRequest, primaryModelName, primaryProto,
                                        primaryText, primaryTools, primaryUsage, primaryLatencyMs);
                            }
                        });
                    }
                }
                if (listener != null) {
                    listener.onEvent(event);
                }
            }
        });
    }

    private static void collectStreamObservation(ModelEvent event,
                                                 StringBuilder textBuf,
                                                 StringBuilder fallbackItemTextBuf,
                                                 List<String> toolNames,
                                                 AtomicReference<ModelUsage> usageRef) {
        if (event.getUsage() != null) {
            usageRef.set(event.getUsage());
        }
        if (event.getType() == ModelEventType.ITEM_START && event.getToolName() != null
                && !event.getToolName().isEmpty()) {
            if (!toolNames.contains(event.getToolName())) {
                toolNames.add(event.getToolName());
            }
        } else if (event.getType() == ModelEventType.ITEM_DELTA) {
            if (event.getDeltaKind() == EventDeltaKind.TEXT && event.getDeltaText() != null) {
                textBuf.append(event.getDeltaText());
            } else if (event.getDeltaKind() == EventDeltaKind.TOOL_ARGS && event.getToolName() != null
                    && !event.getToolName().isEmpty() && !toolNames.contains(event.getToolName())) {
                toolNames.add(event.getToolName());
            }
        } else if (event.getType() == ModelEventType.ITEM_DONE) {
            if (!event.getItems().isEmpty()) {
                for (ModelItem each : event.getItems()) {
                    collectItemObservation(each, fallbackItemTextBuf, toolNames);
                }
            } else if (event.getItem() != null) {
                collectItemObservation(event.getItem(), fallbackItemTextBuf, toolNames);
            }
        }
    }

    private static void collectItemObservation(ModelItem item,
                                               StringBuilder fallbackItemTextBuf,
                                               List<String> toolNames) {
        if (item instanceof ToolCallItem) {
            String name = ((ToolCallItem) item).getName();
            if (name != null && !name.isEmpty() && !toolNames.contains(name)) {
                toolNames.add(name);
            }
        } else if (item instanceof Message) {
            Message msg = (Message) item;
            String text = msg.textContent();
            if (text != null) {
                fallbackItemTextBuf.append(text);
            }
            for (Block block : msg.getBlocks()) {
                if (block instanceof ToolCallBlock) {
                    String name = ((ToolCallBlock) block).getName();
                    if (name != null && !name.isEmpty() && !toolNames.contains(name)) {
                        toolNames.add(name);
                    }
                }
            }
        }
    }

    public List<ShadowEvalReport> getRecordedReports() {
        return Collections.unmodifiableList(recordedReports);
    }

    private boolean shouldSample() {
        if (sampleRate <= 0.0d) {
            return false;
        }
        if (sampleRate >= 1.0d) {
            return true;
        }
        return sampler.getAsDouble() < sampleRate;
    }

    private ModelRequest prepareShadowRequest(ModelRequest primaryRequest) {
        ModelRequest redacted = redactor.apply(primaryRequest);
        ModelRequest.Builder rb = redacted.toBuilder().stream(Boolean.FALSE);
        if (candidateModel != null && !candidateModel.isEmpty()) {
            rb.model(candidateModel);
        }
        Protocol candidateProto = candidateClient.endpoint().getProtocol();
        if (redacted.getProtocol() != null && candidateProto != null) {
            rb.protocol(candidateProto);
        }
        return rb.build();
    }

    private void runShadow(ModelRequest shadowRequest, String primaryModelName, Protocol primaryProto,
                           String primaryText, List<String> primaryTools,
                           ModelUsage primaryUsage, long primaryLatencyMs) {
        long shadowStartNs = System.nanoTime();
        try {
            ModelResult candidateResult = candidateClient.create(shadowRequest);
            long shadowLatencyMs = Math.max(0L, (System.nanoTime() - shadowStartNs) / 1_000_000L);
            String candidateText = extractText(candidateResult);
            List<String> candidateTools = extractToolNames(candidateResult);
            ShadowEvalReport report = new ShadowEvalReport(
                    primaryModelName,
                    shadowRequest.getModel(),
                    primaryProto,
                    candidateClient.endpoint().getProtocol(),
                    primaryLatencyMs,
                    shadowLatencyMs,
                    primaryText,
                    candidateText,
                    primaryText.equals(candidateText),
                    primaryTools.equals(candidateTools),
                    primaryUsage,
                    candidateResult.getUsage(),
                    true,
                    null);
            publish(report);
        } catch (Throwable failure) {
            long shadowLatencyMs = Math.max(0L, (System.nanoTime() - shadowStartNs) / 1_000_000L);
            ShadowEvalReport report = new ShadowEvalReport(
                    primaryModelName,
                    shadowRequest.getModel(),
                    primaryProto,
                    candidateClient.endpoint().getProtocol(),
                    primaryLatencyMs,
                    shadowLatencyMs,
                    primaryText,
                    "",
                    false,
                    false,
                    primaryUsage,
                    null,
                    false,
                    failure);
            publish(report);
        }
    }

    private void publish(ShadowEvalReport report) {
        recordedReports.add(report);
        if (reportConsumer != null) {
            try {
                reportConsumer.accept(report);
            } catch (Exception ignored) {
                // Report sink failure must never disturb shadow worker.
            }
        }
    }

    private static UnaryOperator<ModelRequest> defaultRedactor() {
        return new UnaryOperator<ModelRequest>() {
            @Override
            public ModelRequest apply(ModelRequest request) {
                return request.toBuilder()
                        .applicationMetadata(Collections.<String, Object>emptyMap())
                        .build();
            }
        };
    }

    private static String extractText(ModelResult result) {
        if (result == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ModelItem item : result.getItems()) {
            if (item instanceof Message) {
                String t = ((Message) item).textContent();
                if (t != null) {
                    sb.append(t);
                }
            }
        }
        return sb.toString();
    }

    private static List<String> extractToolNames(ModelResult result) {
        if (result == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<String>();
        for (ModelItem item : result.getItems()) {
            if (item instanceof ToolCallItem) {
                names.add(((ToolCallItem) item).getName());
            }
        }
        return names;
    }

    /** Fluent builder. */
    public static final class Builder {

        private final ModelClient candidateClient;
        private String candidateModel;
        private double sampleRate = 0.01d;
        private DoubleSupplier sampler;
        private Executor executor;
        private UnaryOperator<ModelRequest> redactor;
        private Consumer<ShadowEvalReport> reportConsumer;

        private Builder(ModelClient candidateClient) {
            this.candidateClient = Objects.requireNonNull(candidateClient, "candidateClient is required");
        }

        public Builder candidateModel(String candidateModel) {
            this.candidateModel = candidateModel;
            return this;
        }

        public Builder sampleRate(double sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        public Builder sampler(DoubleSupplier sampler) {
            this.sampler = sampler;
            return this;
        }

        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        public Builder redactor(UnaryOperator<ModelRequest> redactor) {
            this.redactor = redactor;
            return this;
        }

        public Builder reportConsumer(Consumer<ShadowEvalReport> reportConsumer) {
            this.reportConsumer = reportConsumer;
            return this;
        }

        public ShadowEvalMiddleware build() {
            return new ShadowEvalMiddleware(this);
        }
    }
}
