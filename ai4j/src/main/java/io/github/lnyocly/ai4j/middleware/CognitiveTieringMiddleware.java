package io.github.lnyocly.ai4j.middleware;

import io.github.lnyocly.ai4j.client.ModelCall;
import io.github.lnyocly.ai4j.client.ModelCallException;
import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.model.Block;
import io.github.lnyocly.ai4j.model.JsonValues;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.MessageRole;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ToolResultBlock;
import io.github.lnyocly.ai4j.model.ToolResultItem;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * System 1 / System 2 cognitive tiering router middleware.
 *
 * <p>Routes low-complexity extraction/lookup calls to a fast, low-cost System 1
 * target while escalating complex planning, multi-tool orchestration, or
 * tool-error recovery steps to a flagship System 2 model/client.</p>
 */
public final class CognitiveTieringMiddleware implements ModelMiddleware {

    public enum Tier {
        SYSTEM_1,
        SYSTEM_2
    }

    public interface TierClassifier {
        Tier classify(ModelCallContext context);
    }

    public static final class TierTarget {

        private final ModelClient client;
        private final String model;
        private final Map<String, Object> providerOptions;

        private TierTarget(ModelClient client, String model, Map<String, Object> providerOptions) {
            this.client = client;
            this.model = model;
            this.providerOptions = JsonValues.copyObject(providerOptions);
        }

        public static TierTarget of(String model) {
            return new TierTarget(null, model, null);
        }

        public static TierTarget of(ModelClient client, String model) {
            return new TierTarget(client, model, null);
        }

        public static TierTarget of(ModelClient client, String model, Map<String, Object> providerOptions) {
            return new TierTarget(client, model, providerOptions);
        }

        public ModelClient getClient() {
            return client;
        }

        public String getModel() {
            return model;
        }

        public Map<String, Object> getProviderOptions() {
            return providerOptions;
        }
    }

    public static final class DefaultTierClassifier implements TierClassifier {

        private static final Set<String> DEFAULT_COMPLEX_KEYWORDS = new HashSet<String>(Arrays.asList(
                "architecture", "refactor", "debug", "root cause", "analyze", "design", "plan",
                "step by step", "prove", "optimize", "security audit",
                "架构", "重构", "排查", "根因", "分析", "设计", "规划", "证明", "审计"));

        private final int toolThreshold;
        private final int turnThreshold;
        private final int textLengthThreshold;

        public DefaultTierClassifier() {
            this(3, 6, 1500);
        }

        public DefaultTierClassifier(int toolThreshold, int turnThreshold, int textLengthThreshold) {
            this.toolThreshold = Math.max(1, toolThreshold);
            this.turnThreshold = Math.max(1, turnThreshold);
            this.textLengthThreshold = Math.max(100, textLengthThreshold);
        }

        @Override
        public Tier classify(ModelCallContext context) {
            ModelRequest request = context.request();
            Object explicit = context.getAttribute("cognitiveTier");
            if (explicit == null) {
                explicit = request.getApplicationMetadata().get("cognitiveTier");
            }
            if (explicit == null) {
                explicit = request.getApplicationMetadata().get("tier");
            }
            if (explicit != null) {
                String raw = String.valueOf(explicit).trim().toUpperCase(Locale.ROOT);
                if ("SYSTEM_1".equals(raw) || "S1".equals(raw)) {
                    return Tier.SYSTEM_1;
                }
                if ("SYSTEM_2".equals(raw) || "S2".equals(raw)) {
                    return Tier.SYSTEM_2;
                }
            }

            if (request.getTools().size() >= toolThreshold) {
                return Tier.SYSTEM_2;
            }

            int nonSystemItems = 0;
            int totalChars = 0;
            for (ModelItem item : request.getItems()) {
                if (item instanceof ToolResultItem) {
                    if (((ToolResultItem) item).isError()) {
                        return Tier.SYSTEM_2;
                    }
                    nonSystemItems++;
                } else if (item instanceof Message) {
                    Message msg = (Message) item;
                    if (msg.getRole() != MessageRole.SYSTEM) {
                        nonSystemItems++;
                    }
                    for (Block block : msg.getBlocks()) {
                        if (block instanceof ToolResultBlock && ((ToolResultBlock) block).getValue().isError()) {
                            return Tier.SYSTEM_2;
                        }
                    }
                    String text = msg.textContent();
                    if (text != null) {
                        totalChars += text.length();
                        if (msg.getRole() == MessageRole.USER && containsComplexKeyword(text)) {
                            return Tier.SYSTEM_2;
                        }
                    }
                } else {
                    nonSystemItems++;
                }
            }
            if (nonSystemItems >= turnThreshold || totalChars >= textLengthThreshold) {
                return Tier.SYSTEM_2;
            }
            return Tier.SYSTEM_1;
        }

        private static boolean containsComplexKeyword(String text) {
            String lower = text.toLowerCase(Locale.ROOT);
            for (String kw : DEFAULT_COMPLEX_KEYWORDS) {
                if (lower.contains(kw)) {
                    return true;
                }
            }
            return false;
        }
    }

    private final TierTarget system1Target;
    private final TierTarget system2Target;
    private final TierClassifier classifier;

    private CognitiveTieringMiddleware(Builder builder) {
        this.system1Target = builder.system1Target;
        this.system2Target = builder.system2Target;
        this.classifier = builder.classifier == null ? new DefaultTierClassifier() : builder.classifier;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ModelResult intercept(ModelCallContext context, Next next) throws ModelCallException {
        return next.proceed(route(context));
    }

    @Override
    public ModelCall interceptStream(ModelCallContext context, ModelEventListener listener, StreamNext next) {
        return next.proceed(route(context), listener);
    }

    private ModelCallContext route(ModelCallContext context) {
        Tier tier = classifier.classify(context);
        context.attribute("cognitive.tier", tier);
        TierTarget target = tier == Tier.SYSTEM_2 ? system2Target : system1Target;
        if (target == null) {
            context.attribute("cognitive.model", context.request().getModel());
            return context;
        }
        ModelCallContext updated = context;
        if (target.getClient() != null) {
            updated = updated.withClient(target.getClient());
        }
        ModelRequest req = updated.request();
        boolean changeModel = target.getModel() != null && !target.getModel().isEmpty();
        boolean addOptions = !target.getProviderOptions().isEmpty();
        Protocol targetProto = target.getClient() != null
                ? target.getClient().endpoint().getProtocol()
                : req.getProtocol();
        boolean changeProto = targetProto != null && req.getProtocol() != targetProto;

        if (changeModel || addOptions || changeProto) {
            ModelRequest.Builder rb = req.toBuilder();
            if (changeModel) {
                rb.model(target.getModel());
            }
            if (changeProto) {
                rb.protocol(targetProto);
            }
            if (addOptions) {
                rb.providerOptions(target.getProviderOptions());
            }
            req = rb.build();
            updated = updated.withRequest(req);
        }
        updated.attribute("cognitive.model", req.getModel());
        return updated;
    }

    /** Fluent builder. */
    public static final class Builder {

        private TierTarget system1Target;
        private TierTarget system2Target;
        private TierClassifier classifier;

        private Builder() {
        }

        public Builder system1(TierTarget target) {
            this.system1Target = target;
            return this;
        }

        public Builder system1(ModelClient client, String model) {
            this.system1Target = TierTarget.of(client, model);
            return this;
        }

        public Builder system1Model(String model) {
            this.system1Target = TierTarget.of(model);
            return this;
        }

        public Builder system2(TierTarget target) {
            this.system2Target = target;
            return this;
        }

        public Builder system2(ModelClient client, String model) {
            this.system2Target = TierTarget.of(client, model);
            return this;
        }

        public Builder system2Model(String model) {
            this.system2Target = TierTarget.of(model);
            return this;
        }

        public Builder classifier(TierClassifier classifier) {
            this.classifier = Objects.requireNonNull(classifier, "classifier is required");
            return this;
        }

        public CognitiveTieringMiddleware build() {
            return new CognitiveTieringMiddleware(this);
        }
    }
}
