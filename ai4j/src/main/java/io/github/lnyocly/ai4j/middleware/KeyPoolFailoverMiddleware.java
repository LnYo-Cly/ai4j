package io.github.lnyocly.ai4j.middleware;

import io.github.lnyocly.ai4j.client.ModelCall;
import io.github.lnyocly.ai4j.client.ModelCallException;
import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.model.ModelCallExceptionMarker;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.stream.DispatcherProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Multi-key pool rotation, circuit-breaker cooldown, and cross-protocol/model
 * failover middleware.
 */
public final class KeyPoolFailoverMiddleware implements ModelMiddleware {

    private final List<TargetSlot> primarySlots;
    private final List<TargetSlot> fallbackSlots;
    private final int failureThreshold;
    private final long errorCooldownMs;
    private final long rateLimitCooldownMs;
    private final long authCooldownMs;
    private final LongSupplier clockMillis;
    private final AtomicInteger roundRobin = new AtomicInteger(0);

    private KeyPoolFailoverMiddleware(Builder builder) {
        this.primarySlots = Collections.unmodifiableList(new ArrayList<TargetSlot>(builder.primarySlots));
        this.fallbackSlots = Collections.unmodifiableList(new ArrayList<TargetSlot>(builder.fallbackSlots));
        this.failureThreshold = Math.max(1, builder.failureThreshold);
        this.errorCooldownMs = Math.max(0L, builder.errorCooldownMs);
        this.rateLimitCooldownMs = Math.max(0L, builder.rateLimitCooldownMs);
        this.authCooldownMs = Math.max(0L, builder.authCooldownMs);
        this.clockMillis = builder.clockMillis == null ? new LongSupplier() {
            @Override
            public long getAsLong() {
                return System.currentTimeMillis();
            }
        } : builder.clockMillis;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ModelResult intercept(ModelCallContext context, Next next) throws ModelCallException {
        List<TargetSlot> plan = buildAttemptOrder();
        if (plan.isEmpty()) {
            return next.proceed(context);
        }
        ModelCallException lastFailure = null;
        for (TargetSlot slot : plan) {
            ModelCallContext targetContext = applySlot(context, slot);
            targetContext.attribute("failover.slot", slot.id);
            try {
                ModelResult result = next.proceed(targetContext);
                slot.recordSuccess();
                return result;
            } catch (ModelCallException failure) {
                lastFailure = failure;
                recordSlotFailure(slot, failure);
                if (!shouldFailover(context.request(), failure)) {
                    throw failure;
                }
            }
        }
        throw lastFailure;
    }

    @Override
    public ModelCall interceptStream(ModelCallContext context, ModelEventListener listener, StreamNext next) {
        List<TargetSlot> plan = buildAttemptOrder();
        if (plan.isEmpty()) {
            return next.proceed(context, listener);
        }
        return new FailoverStreamCall(context, listener, next, plan);
    }

    public boolean isSlotOpen(String slotId) {
        long now = clockMillis.getAsLong();
        for (TargetSlot slot : primarySlots) {
            if (slot.id.equals(slotId)) {
                return !slot.isHealthy(now);
            }
        }
        for (TargetSlot slot : fallbackSlots) {
            if (slot.id.equals(slotId)) {
                return !slot.isHealthy(now);
            }
        }
        return false;
    }

    private List<TargetSlot> buildAttemptOrder() {
        long now = clockMillis.getAsLong();
        List<TargetSlot> order = new ArrayList<TargetSlot>(primarySlots.size() + fallbackSlots.size());
        if (!primarySlots.isEmpty()) {
            int start = Math.floorMod(roundRobin.getAndIncrement(), primarySlots.size());
            for (int offset = 0; offset < primarySlots.size(); offset++) {
                TargetSlot candidate = primarySlots.get((start + offset) % primarySlots.size());
                if (candidate.isHealthy(now)) {
                    order.add(candidate);
                }
            }
        }
        for (TargetSlot fallback : fallbackSlots) {
            if (fallback.isHealthy(now)) {
                order.add(fallback);
            }
        }
        if (order.isEmpty()) {
            TargetSlot earliest = findEarliestRecoverySlot();
            if (earliest != null) {
                order.add(earliest);
            }
        }
        return order;
    }

    private TargetSlot findEarliestRecoverySlot() {
        TargetSlot best = null;
        for (TargetSlot slot : primarySlots) {
            if (best == null || slot.openUntilMillis.get() < best.openUntilMillis.get()) {
                best = slot;
            }
        }
        for (TargetSlot slot : fallbackSlots) {
            if (best == null || slot.openUntilMillis.get() < best.openUntilMillis.get()) {
                best = slot;
            }
        }
        return best;
    }

    private static ModelCallContext applySlot(ModelCallContext context, TargetSlot slot) {
        ModelCallContext updated = context;
        if (slot.client != null) {
            updated = updated.withClient(slot.client);
        }
        ModelRequest req = updated.request();
        boolean rewriteModel = slot.overrideModel != null && !slot.overrideModel.isEmpty();
        Protocol targetProto = slot.client != null ? slot.client.endpoint().getProtocol() : req.getProtocol();
        boolean rewriteProtocol = targetProto != null && req.getProtocol() != targetProto;
        if (rewriteModel || rewriteProtocol) {
            ModelRequest.Builder rb = req.toBuilder();
            if (rewriteModel) {
                rb.model(slot.overrideModel);
            }
            if (rewriteProtocol) {
                rb.protocol(targetProto);
            }
            updated = updated.withRequest(rb.build());
        }
        return updated;
    }

    private void recordSlotFailure(TargetSlot slot, ModelCallException failure) {
        long now = clockMillis.getAsLong();
        int status = failure.getHttpStatus();
        if (status == 401 || status == 403) {
            slot.trip(now + authCooldownMs);
            return;
        }
        if (status == 429) {
            long wait = failure.getRetryAfterMillis() != null
                    ? failure.getRetryAfterMillis()
                    : rateLimitCooldownMs;
            slot.trip(now + wait);
            return;
        }
        int failures = slot.consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            slot.trip(now + errorCooldownMs);
        }
    }

    private static boolean shouldFailover(ModelRequest request, ModelCallException failure) {
        int status = failure.getHttpStatus();
        if (status == 401 || status == 403) {
            return true;
        }
        if (!request.isReplayable()) {
            return false;
        }
        return failure.isRetryable() || status == 429 || status == 408 || status == 504 || status >= 500;
    }

    private final class FailoverStreamCall implements ModelCall {

        private final ModelCallContext baseContext;
        private final ModelEventListener downstream;
        private final StreamNext next;
        private final List<TargetSlot> plan;
        private final Protocol fallbackProtocol;
        private final CountDownLatch settled = new CountDownLatch(1);
        private final AtomicReference<ModelCallStatus> status =
                new AtomicReference<ModelCallStatus>(ModelCallStatus.RUNNING);
        private final AtomicReference<ModelCall> activeCall = new AtomicReference<ModelCall>();
        private final AtomicReference<ModelResult> finalResult = new AtomicReference<ModelResult>();
        private final AtomicReference<ModelCallException> finalError = new AtomicReference<ModelCallException>();
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final Executor executor = DispatcherProvider.executor();

        private FailoverStreamCall(ModelCallContext baseContext, ModelEventListener downstream,
                                   StreamNext next, List<TargetSlot> plan) {
            this.baseContext = baseContext;
            this.downstream = downstream;
            this.next = next;
            this.plan = plan;
            this.fallbackProtocol = baseContext.request().getProtocol() != null
                    ? baseContext.request().getProtocol()
                    : baseContext.endpoint().getProtocol();
            trySlot(0);
        }

        private void trySlot(final int slotIndex) {
            if (cancelled.get()) {
                settleCancelled();
                return;
            }
            final TargetSlot slot = plan.get(slotIndex);
            final ModelCallContext targetContext = applySlot(baseContext, slot);
            targetContext.attribute("failover.slot", slot.id);
            final Protocol slotProtocol = targetContext.request().getProtocol() != null
                    ? targetContext.request().getProtocol()
                    : targetContext.endpoint().getProtocol();
            final AtomicBoolean deliveredContent = new AtomicBoolean(false);
            final AtomicBoolean forwardedError = new AtomicBoolean(false);
            final AtomicReference<ModelCallException> suppressedError = new AtomicReference<ModelCallException>();
            final ModelCall call;
            try {
                call = next.proceed(targetContext, new ModelEventListener() {
                    @Override
                    public void onEvent(ModelEvent event) {
                        if (cancelled.get() || event == null) {
                            return;
                        }
                        if (event.getType() == ModelEventType.ERROR) {
                            ModelCallExceptionMarker marker = event.getFailure();
                            ModelCallException mapped = marker == null
                                    ? new ModelCallException(slotProtocol, 0, "stream error", null,
                                            ModelCallStatus.FAILED)
                                    : ModelCallException.fromMarker(marker);
                            if (!deliveredContent.get() && slotIndex + 1 < plan.size()
                                    && shouldFailover(baseContext.request(), mapped)) {
                                suppressedError.compareAndSet(null, mapped);
                                return;
                            }
                            forwardedError.set(true);
                            if (downstream != null) {
                                downstream.onEvent(event);
                            }
                            return;
                        }
                        if (event.getType() != ModelEventType.DONE && event.getType() != ModelEventType.RETRYING) {
                            deliveredContent.set(true);
                        }
                        if (downstream != null) {
                            downstream.onEvent(event);
                        }
                    }
                });
            } catch (ModelCallException syncFailure) {
                handleSlotFailure(slot, slotIndex, slotProtocol, false, false, null, syncFailure);
                return;
            }
            activeCall.set(call);
            if (cancelled.get()) {
                call.cancel();
                settleCancelled();
                return;
            }
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        ModelResult result = call.await();
                        if (cancelled.get() || call.status() == ModelCallStatus.CANCELLED) {
                            settleCancelled();
                            return;
                        }
                        slot.recordSuccess();
                        finalResult.set(result);
                        if (status.compareAndSet(ModelCallStatus.RUNNING, ModelCallStatus.COMPLETED)) {
                            settled.countDown();
                        }
                    } catch (ModelCallException failure) {
                        handleSlotFailure(slot, slotIndex, slotProtocol,
                                deliveredContent.get(), forwardedError.get(), suppressedError.get(), failure);
                    }
                }
            });
        }

        private void handleSlotFailure(TargetSlot slot, int slotIndex, Protocol slotProtocol,
                                       boolean deliveredContent, boolean forwardedError,
                                       ModelCallException suppressed, ModelCallException failure) {
            if (cancelled.get()) {
                settleCancelled();
                return;
            }
            ModelCallException effective = suppressed != null ? suppressed : failure;
            if (effective == null) {
                effective = new ModelCallException(slotProtocol, 0, "stream error", null, ModelCallStatus.FAILED);
            } else if (effective.getRetryAfterMillis() == null
                    && failure != null && failure.getRetryAfterMillis() != null) {
                effective = failure;
            }
            recordSlotFailure(slot, effective);
            if (!deliveredContent && slotIndex + 1 < plan.size()
                    && shouldFailover(baseContext.request(), effective)) {
                trySlot(slotIndex + 1);
                return;
            }
            if (!forwardedError && downstream != null) {
                downstream.onEvent(ModelEvent.builder(slotProtocol, ModelEventType.ERROR)
                        .failure(ModelCallExceptionMarker.builder(slotProtocol)
                                .httpStatus(effective.getHttpStatus())
                                .providerErrorType(effective.getProviderErrorType())
                                .providerRequestId(effective.getProviderRequestId())
                                .retryable(effective.isRetryable())
                                .message(effective.getMessage())
                                .rawSnippet(effective.getRawSnippet())
                                .status(effective.getStatus())
                                .retryAfterMillis(effective.getRetryAfterMillis())
                                .build())
                        .build());
            }
            finalError.set(effective);
            if (status.compareAndSet(ModelCallStatus.RUNNING, ModelCallStatus.FAILED)) {
                settled.countDown();
            }
        }

        private void settleCancelled() {
            if (status.compareAndSet(ModelCallStatus.RUNNING, ModelCallStatus.CANCELLED)) {
                settled.countDown();
            }
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                ModelCall active = activeCall.get();
                if (active != null) {
                    active.cancel();
                }
                settleCancelled();
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public ModelResult await() throws ModelCallException {
            try {
                settled.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancel();
                throw new ModelCallException(fallbackProtocol, 0, "failover stream interrupted", null,
                        ModelCallStatus.CANCELLED);
            }
            ModelCallException error = finalError.get();
            if (error != null) {
                throw error;
            }
            return finalResult.get();
        }

        @Override
        public ModelCallStatus status() {
            return status.get();
        }
    }

    private static final class TargetSlot {

        private final String id;
        private final ModelClient client;
        private final String overrideModel;
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private final AtomicLong openUntilMillis = new AtomicLong(0L);

        private TargetSlot(String id, ModelClient client, String overrideModel) {
            this.id = Objects.requireNonNull(id, "slot id is required");
            this.client = client;
            this.overrideModel = overrideModel;
        }

        private boolean isHealthy(long nowMillis) {
            return nowMillis >= openUntilMillis.get();
        }

        private void recordSuccess() {
            consecutiveFailures.set(0);
            openUntilMillis.set(0L);
        }

        private void trip(long untilMillis) {
            openUntilMillis.set(untilMillis);
        }
    }

    /** Fluent builder. */
    public static final class Builder {

        private final List<TargetSlot> primarySlots = new ArrayList<TargetSlot>();
        private final List<TargetSlot> fallbackSlots = new ArrayList<TargetSlot>();
        private int failureThreshold = 1;
        private long errorCooldownMs = 15_000L;
        private long rateLimitCooldownMs = 30_000L;
        private long authCooldownMs = 300_000L;
        private LongSupplier clockMillis;

        private Builder() {
        }

        public Builder primary(String id, ModelClient client) {
            return primary(id, client, null);
        }

        public Builder primary(String id, ModelClient client, String overrideModel) {
            this.primarySlots.add(new TargetSlot(id, Objects.requireNonNull(client, "client is required"), overrideModel));
            return this;
        }

        public Builder fallback(String id, ModelClient client) {
            return fallback(id, client, null);
        }

        public Builder fallback(String id, ModelClient client, String overrideModel) {
            this.fallbackSlots.add(new TargetSlot(id, Objects.requireNonNull(client, "client is required"), overrideModel));
            return this;
        }

        public Builder failureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
            return this;
        }

        public Builder errorCooldownMs(long errorCooldownMs) {
            this.errorCooldownMs = errorCooldownMs;
            return this;
        }

        public Builder rateLimitCooldownMs(long rateLimitCooldownMs) {
            this.rateLimitCooldownMs = rateLimitCooldownMs;
            return this;
        }

        public Builder authCooldownMs(long authCooldownMs) {
            this.authCooldownMs = authCooldownMs;
            return this;
        }

        public Builder clockMillis(LongSupplier clockMillis) {
            this.clockMillis = clockMillis;
            return this;
        }

        public KeyPoolFailoverMiddleware build() {
            return new KeyPoolFailoverMiddleware(this);
        }
    }
}
