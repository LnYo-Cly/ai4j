package io.github.lnyocly.ai4j.middleware;

import io.github.lnyocly.ai4j.client.ModelCall;
import io.github.lnyocly.ai4j.client.ModelCallException;
import io.github.lnyocly.ai4j.exception.Ai4jException;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.ModelUsage;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Scope/session token and cost circuit-breaker middleware.
 */
public final class BudgetGuardMiddleware implements ModelMiddleware {

    public interface CostCalculator {
        double calculateCost(String model, ModelUsage usage);
    }

    public static final class BudgetExceededException extends Ai4jException {

        private final String scopeKey;
        private final BudgetSnapshot snapshot;

        public BudgetExceededException(String scopeKey, BudgetSnapshot snapshot, String message) {
            super(message);
            this.scopeKey = scopeKey;
            this.snapshot = snapshot;
        }

        public String getScopeKey() {
            return scopeKey;
        }

        public BudgetSnapshot getSnapshot() {
            return snapshot;
        }
    }

    public static final class BudgetPolicy {

        private final long maxInputTokens;
        private final long maxOutputTokens;
        private final long maxTotalTokens;
        private final double maxCost;
        private final long maxRequests;

        private BudgetPolicy(long maxInputTokens, long maxOutputTokens, long maxTotalTokens,
                             double maxCost, long maxRequests) {
            this.maxInputTokens = maxInputTokens;
            this.maxOutputTokens = maxOutputTokens;
            this.maxTotalTokens = maxTotalTokens;
            this.maxCost = maxCost;
            this.maxRequests = maxRequests;
        }

        public static PolicyBuilder builder() {
            return new PolicyBuilder();
        }

        public long getMaxInputTokens() {
            return maxInputTokens;
        }

        public long getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public long getMaxTotalTokens() {
            return maxTotalTokens;
        }

        public double getMaxCost() {
            return maxCost;
        }

        public long getMaxRequests() {
            return maxRequests;
        }

        public static final class PolicyBuilder {

            private long maxInputTokens = -1L;
            private long maxOutputTokens = -1L;
            private long maxTotalTokens = -1L;
            private double maxCost = -1.0d;
            private long maxRequests = -1L;

            public PolicyBuilder maxInputTokens(long maxInputTokens) {
                this.maxInputTokens = maxInputTokens;
                return this;
            }

            public PolicyBuilder maxOutputTokens(long maxOutputTokens) {
                this.maxOutputTokens = maxOutputTokens;
                return this;
            }

            public PolicyBuilder maxTotalTokens(long maxTotalTokens) {
                this.maxTotalTokens = maxTotalTokens;
                return this;
            }

            public PolicyBuilder maxCost(double maxCost) {
                this.maxCost = maxCost;
                return this;
            }

            public PolicyBuilder maxRequests(long maxRequests) {
                this.maxRequests = maxRequests;
                return this;
            }

            public BudgetPolicy build() {
                return new BudgetPolicy(maxInputTokens, maxOutputTokens, maxTotalTokens, maxCost, maxRequests);
            }
        }
    }

    public static final class BudgetSnapshot {

        private final long inputTokens;
        private final long outputTokens;
        private final long totalTokens;
        private final double totalCost;
        private final long requestCount;

        private BudgetSnapshot(long inputTokens, long outputTokens, long totalTokens,
                               double totalCost, long requestCount) {
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.totalTokens = totalTokens;
            this.totalCost = totalCost;
            this.requestCount = requestCount;
        }

        public long getInputTokens() {
            return inputTokens;
        }

        public long getOutputTokens() {
            return outputTokens;
        }

        public long getTotalTokens() {
            return totalTokens;
        }

        public double getTotalCost() {
            return totalCost;
        }

        public long getRequestCount() {
            return requestCount;
        }
    }

    private static final class ScopeLedger {

        private final AtomicLong inputTokens = new AtomicLong(0L);
        private final AtomicLong outputTokens = new AtomicLong(0L);
        private final AtomicLong totalTokens = new AtomicLong(0L);
        private final AtomicLong requestCount = new AtomicLong(0L);
        private double totalCost;

        synchronized void record(ModelUsage usage, double cost) {
            requestCount.incrementAndGet();
            if (usage != null) {
                long in = Math.max(0L, usage.getInputTokens() == ModelUsage.UNKNOWN ? 0L : usage.getInputTokens());
                long out = Math.max(0L, usage.getOutputTokens() == ModelUsage.UNKNOWN ? 0L : usage.getOutputTokens());
                long total = usage.getTotalTokens() == ModelUsage.UNKNOWN
                        ? (in + out)
                        : Math.max(0L, usage.getTotalTokens());
                inputTokens.addAndGet(in);
                outputTokens.addAndGet(out);
                totalTokens.addAndGet(total);
            }
            totalCost += Math.max(0.0d, cost);
        }

        synchronized BudgetSnapshot snapshot() {
            return new BudgetSnapshot(inputTokens.get(), outputTokens.get(), totalTokens.get(),
                    totalCost, requestCount.get());
        }
    }

    private final BudgetPolicy defaultPolicy;
    private final Map<String, BudgetPolicy> scopePolicies = new ConcurrentHashMap<String, BudgetPolicy>();
    private final Map<String, ScopeLedger> ledgers = new ConcurrentHashMap<String, ScopeLedger>();
    private final Function<ModelCallContext, String> scopeKeyResolver;
    private final CostCalculator costCalculator;

    private BudgetGuardMiddleware(Builder builder) {
        this.defaultPolicy = Objects.requireNonNull(builder.defaultPolicy, "defaultPolicy is required");
        this.scopePolicies.putAll(builder.scopePolicies);
        this.scopeKeyResolver = builder.scopeKeyResolver == null
                ? defaultScopeResolver()
                : builder.scopeKeyResolver;
        this.costCalculator = builder.costCalculator == null
                ? new CostCalculator() {
                    @Override
                    public double calculateCost(String model, ModelUsage usage) {
                        return 0.0d;
                    }
                }
                : builder.costCalculator;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ModelResult intercept(ModelCallContext context, Next next) throws ModelCallException {
        String scopeKey = resolveScopeKey(context);
        enforceBudget(scopeKey);
        ModelResult result = next.proceed(context);
        recordUsage(scopeKey, context.request().getModel(), result.getUsage());
        return result;
    }

    @Override
    public ModelCall interceptStream(final ModelCallContext context,
                                     final ModelEventListener listener,
                                     StreamNext next) {
        final String scopeKey = resolveScopeKey(context);
        enforceBudget(scopeKey);
        final AtomicReference<ModelUsage> latestUsage = new AtomicReference<ModelUsage>();
        final AtomicBoolean recorded = new AtomicBoolean(false);
        return next.proceed(context, new ModelEventListener() {
            @Override
            public void onEvent(ModelEvent event) {
                if (event != null) {
                    if (event.getUsage() != null) {
                        latestUsage.set(event.getUsage());
                    }
                    if (event.getType() == ModelEventType.DONE && recorded.compareAndSet(false, true)) {
                        recordUsage(scopeKey, context.request().getModel(), latestUsage.get());
                    }
                }
                if (listener != null) {
                    listener.onEvent(event);
                }
            }
        });
    }

    public BudgetSnapshot snapshot(String scopeKey) {
        ScopeLedger ledger = ledgers.get(normalizeScope(scopeKey));
        return ledger == null ? new BudgetSnapshot(0L, 0L, 0L, 0.0d, 0L) : ledger.snapshot();
    }

    public void reset(String scopeKey) {
        ledgers.remove(normalizeScope(scopeKey));
    }

    private void enforceBudget(String scopeKey) {
        BudgetPolicy policy = scopePolicies.containsKey(scopeKey) ? scopePolicies.get(scopeKey) : defaultPolicy;
        BudgetSnapshot snap = snapshot(scopeKey);
        if (policy.getMaxRequests() >= 0L && snap.getRequestCount() >= policy.getMaxRequests()) {
            throw new BudgetExceededException(scopeKey, snap,
                    "Budget request limit exceeded for scope '" + scopeKey + "': "
                            + snap.getRequestCount() + " >= " + policy.getMaxRequests());
        }
        if (policy.getMaxInputTokens() >= 0L && snap.getInputTokens() >= policy.getMaxInputTokens()) {
            throw new BudgetExceededException(scopeKey, snap,
                    "Budget input token limit exceeded for scope '" + scopeKey + "': "
                            + snap.getInputTokens() + " >= " + policy.getMaxInputTokens());
        }
        if (policy.getMaxOutputTokens() >= 0L && snap.getOutputTokens() >= policy.getMaxOutputTokens()) {
            throw new BudgetExceededException(scopeKey, snap,
                    "Budget output token limit exceeded for scope '" + scopeKey + "': "
                            + snap.getOutputTokens() + " >= " + policy.getMaxOutputTokens());
        }
        if (policy.getMaxTotalTokens() >= 0L && snap.getTotalTokens() >= policy.getMaxTotalTokens()) {
            throw new BudgetExceededException(scopeKey, snap,
                    "Budget total token limit exceeded for scope '" + scopeKey + "': "
                            + snap.getTotalTokens() + " >= " + policy.getMaxTotalTokens());
        }
        if (policy.getMaxCost() >= 0.0d && snap.getTotalCost() >= policy.getMaxCost()) {
            throw new BudgetExceededException(scopeKey, snap,
                    "Budget cost limit exceeded for scope '" + scopeKey + "': "
                            + snap.getTotalCost() + " >= " + policy.getMaxCost());
        }
    }

    private void recordUsage(String scopeKey, String model, ModelUsage usage) {
        ScopeLedger ledger = ledgers.get(scopeKey);
        if (ledger == null) {
            ScopeLedger created = new ScopeLedger();
            ScopeLedger existing = ledgers.putIfAbsent(scopeKey, created);
            ledger = existing == null ? created : existing;
        }
        double cost = usage == null ? 0.0d : costCalculator.calculateCost(model, usage);
        ledger.record(usage, cost);
    }

    private String resolveScopeKey(ModelCallContext context) {
        return normalizeScope(scopeKeyResolver.apply(context));
    }

    private static String normalizeScope(String raw) {
        return raw == null || raw.trim().isEmpty() ? "default" : raw.trim();
    }

    private static Function<ModelCallContext, String> defaultScopeResolver() {
        return new Function<ModelCallContext, String>() {
            @Override
            public String apply(ModelCallContext context) {
                Object attr = context.getAttribute("scopeKey");
                if (attr != null) {
                    return String.valueOf(attr);
                }
                ModelRequest req = context.request();
                Map<String, Object> meta = req.getApplicationMetadata();
                if (meta.containsKey("scopeKey")) {
                    return String.valueOf(meta.get("scopeKey"));
                }
                if (meta.containsKey("sessionId")) {
                    return String.valueOf(meta.get("sessionId"));
                }
                return "default";
            }
        };
    }

    /** Fluent builder. */
    public static final class Builder {

        private BudgetPolicy defaultPolicy = BudgetPolicy.builder().build();
        private final Map<String, BudgetPolicy> scopePolicies = new ConcurrentHashMap<String, BudgetPolicy>();
        private Function<ModelCallContext, String> scopeKeyResolver;
        private CostCalculator costCalculator;

        private Builder() {
        }

        public Builder defaultPolicy(BudgetPolicy defaultPolicy) {
            this.defaultPolicy = defaultPolicy;
            return this;
        }

        public Builder maxTotalTokens(long maxTotalTokens) {
            this.defaultPolicy = BudgetPolicy.builder()
                    .maxInputTokens(defaultPolicy.getMaxInputTokens())
                    .maxOutputTokens(defaultPolicy.getMaxOutputTokens())
                    .maxTotalTokens(maxTotalTokens)
                    .maxCost(defaultPolicy.getMaxCost())
                    .maxRequests(defaultPolicy.getMaxRequests())
                    .build();
            return this;
        }

        public Builder maxCost(double maxCost) {
            this.defaultPolicy = BudgetPolicy.builder()
                    .maxInputTokens(defaultPolicy.getMaxInputTokens())
                    .maxOutputTokens(defaultPolicy.getMaxOutputTokens())
                    .maxTotalTokens(defaultPolicy.getMaxTotalTokens())
                    .maxCost(maxCost)
                    .maxRequests(defaultPolicy.getMaxRequests())
                    .build();
            return this;
        }

        public Builder scopePolicy(String scopeKey, BudgetPolicy policy) {
            if (scopeKey != null && policy != null) {
                this.scopePolicies.put(scopeKey.trim(), policy);
            }
            return this;
        }

        public Builder scopeKeyResolver(Function<ModelCallContext, String> scopeKeyResolver) {
            this.scopeKeyResolver = scopeKeyResolver;
            return this;
        }

        public Builder costCalculator(CostCalculator costCalculator) {
            this.costCalculator = costCalculator;
            return this;
        }

        public BudgetGuardMiddleware build() {
            return new BudgetGuardMiddleware(this);
        }
    }
}
