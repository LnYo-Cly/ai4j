package io.github.lnyocly.ai4j.middleware;

import io.github.lnyocly.ai4j.client.ModelCall;
import io.github.lnyocly.ai4j.client.ModelCallException;
import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.model.EventDeltaKind;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelCallExceptionMarker;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.ModelUsage;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ToolDeclaration;
import io.github.lnyocly.ai4j.protocol.Capabilities;
import io.github.lnyocly.ai4j.protocol.Capability;
import io.github.lnyocly.ai4j.protocol.CapabilityMatrix;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/**
 * Deterministic unit tests (zero {@code Thread.sleep}) for all {@link ModelMiddleware}
 * implementations and {@link MiddlewareModelClient}.
 */
public class MiddlewareSuiteTest {

    private static final Executor DIRECT_EXECUTOR = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    @Test
    public void retryMiddlewareHonors429RetryAfterAnd5xxBackoffOnBlockingAndStreaming() {
        final List<Long> recordedSleeps = new ArrayList<Long>();
        final AtomicInteger attempt = new AtomicInteger(0);
        ModelClient flaky = new StubClient(Protocol.GEMINI) {
            @Override
            public ModelResult create(ModelRequest request) throws ModelCallException {
                int n = attempt.getAndIncrement();
                if (n == 0) {
                    throw new ModelCallException(Protocol.GEMINI, 429, "RESOURCE_EXHAUSTED", "r1",
                            true, "rate limited", null, ModelCallStatus.FAILED, 1200L);
                }
                if (n == 1) {
                    throw new ModelCallException(Protocol.GEMINI, 503, "UNAVAILABLE", "r2",
                            true, "busy", null, ModelCallStatus.FAILED, null);
                }
                return textResult("recovered", 10, 5);
            }

            @Override
            public ModelCall createStream(ModelRequest request, ModelEventListener listener) {
                int n = attempt.getAndIncrement();
                if (n == 0) {
                    ModelCallException err = new ModelCallException(Protocol.GEMINI, 429, "RESOURCE_EXHAUSTED",
                            "r3", true, "slow", null, ModelCallStatus.FAILED, 1500L);
                    if (listener != null) {
                        listener.onEvent(ModelEvent.builder(Protocol.GEMINI, ModelEventType.ERROR)
                                .failure(ModelCallExceptionMarker.builder(Protocol.GEMINI)
                                        .httpStatus(429)
                                        .retryable(true)
                                        .message("slow")
                                        .retryAfterMillis(1500L)
                                        .build())
                                .build());
                    }
                    return failedCall(err);
                }
                if (listener != null) {
                    listener.onEvent(ModelEvent.builder(Protocol.GEMINI, ModelEventType.ITEM_DELTA)
                            .delta(EventDeltaKind.TEXT, "ok-stream")
                            .build());
                    listener.onEvent(ModelEvent.builder(Protocol.GEMINI, ModelEventType.DONE).build());
                }
                return completedCall(textResult("ok-stream", 5, 2));
            }
        };

        RetryMiddleware retry = RetryMiddleware.builder()
                .maxRetries(3)
                .initialBackoffMs(100L)
                .maxBackoffMs(5000L)
                .multiplier(2.0d)
                .sleeper(new RetryBackoffSleeper() {
                    @Override
                    public void sleep(long millis) {
                        recordedSleeps.add(Long.valueOf(millis));
                    }
                })
                .streamRetryExecutor(DIRECT_EXECUTOR)
                .build();

        ModelClient wrapped = MiddlewareModelClient.wrap(flaky, retry);
        ModelResult blocking = wrapped.create(simpleRequest("hello"));
        Assert.assertEquals("recovered", ((Message) blocking.getItems().get(0)).textContent());
        Assert.assertEquals(2, recordedSleeps.size());
        Assert.assertEquals(Long.valueOf(1200L), recordedSleeps.get(0)); // 429 Retry-After
        Assert.assertEquals(Long.valueOf(200L), recordedSleeps.get(1));  // 100 * 2^1

        recordedSleeps.clear();
        attempt.set(0);
        final List<ModelEventType> streamEvents = new ArrayList<ModelEventType>();
        ModelCall streamCall = wrapped.createStream(simpleRequest("hello"), new ModelEventListener() {
            @Override
            public void onEvent(ModelEvent event) {
                streamEvents.add(event.getType());
            }
        });
        ModelResult streamResult = streamCall.await();
        Assert.assertEquals("ok-stream", ((Message) streamResult.getItems().get(0)).textContent());
        Assert.assertEquals(Collections.singletonList(Long.valueOf(1500L)), recordedSleeps);
        Assert.assertTrue(streamEvents.contains(ModelEventType.RETRYING));
        Assert.assertFalse(streamEvents.contains(ModelEventType.ERROR));
    }

    @Test
    public void keyPoolFailoverRotatesTripsCircuitAndRecoversCrossProtocol() {
        final AtomicLong now = new AtomicLong(1_000L);
        final AtomicInteger primary1Calls = new AtomicInteger(0);
        final AtomicInteger fallbackCalls = new AtomicInteger(0);

        ModelClient primary1 = new StubClient(Protocol.MESSAGES) {
            @Override
            public ModelResult create(ModelRequest request) throws ModelCallException {
                if (primary1Calls.incrementAndGet() == 1) {
                    throw new ModelCallException(Protocol.MESSAGES, 401, "auth_error", null,
                            false, "bad key", null, ModelCallStatus.FAILED);
                }
                return textResult("from-primary-1", 4, 2);
            }
        };
        ModelClient fallbackGemini = new StubClient(Protocol.GEMINI) {
            @Override
            public ModelResult create(ModelRequest request) throws ModelCallException {
                fallbackCalls.incrementAndGet();
                Assert.assertEquals(Protocol.GEMINI, request.getProtocol());
                Assert.assertEquals("gemini-2.5-flash", request.getModel());
                return textResult("from-gemini-fallback", 6, 3);
            }
        };

        KeyPoolFailoverMiddleware failover = KeyPoolFailoverMiddleware.builder()
                .primary("anth-k1", primary1)
                .fallback("gem-fb", fallbackGemini, "gemini-2.5-flash")
                .authCooldownMs(10_000L)
                .clockMillis(new LongSupplier() {
                    @Override
                    public long getAsLong() {
                        return now.get();
                    }
                })
                .build();

        ModelClient wrapped = MiddlewareModelClient.wrap(primary1, failover);
        ModelResult first = wrapped.create(ModelRequest.builder()
                .protocol(Protocol.MESSAGES)
                .model("claude-sonnet-4-5")
                .message(Message.user().text("hi").build())
                .build());
        Assert.assertEquals("from-gemini-fallback", ((Message) first.getItems().get(0)).textContent());
        Assert.assertTrue(failover.isSlotOpen("anth-k1"));
        Assert.assertEquals(1, fallbackCalls.get());

        // While anth-k1 is open, call goes directly to fallback without touching primary1
        wrapped.create(simpleRequest("hi"));
        Assert.assertEquals(1, primary1Calls.get());
        Assert.assertEquals(2, fallbackCalls.get());

        // Advance clock past authCooldownMs -> primary1 recovers
        now.addAndGet(15_000L);
        Assert.assertFalse(failover.isSlotOpen("anth-k1"));
        ModelResult recovered = wrapped.create(simpleRequest("hi"));
        Assert.assertEquals("from-primary-1", ((Message) recovered.getItems().get(0)).textContent());
    }

    @Test
    public void budgetGuardEnforcesLimitsAcrossBlockingAndStreaming() {
        ModelClient stub = new StubClient(Protocol.CHAT_COMPLETIONS) {
            @Override
            public ModelResult create(ModelRequest request) {
                return textResult("ok", 60, 40); // total 100
            }

            @Override
            public ModelCall createStream(ModelRequest request, ModelEventListener listener) {
                ModelResult r = textResult("ok", 60, 40);
                if (listener != null) {
                    listener.onEvent(ModelEvent.builder(Protocol.CHAT_COMPLETIONS, ModelEventType.DONE)
                            .usage(r.getUsage())
                            .build());
                }
                return completedCall(r);
            }
        };
        BudgetGuardMiddleware guard = BudgetGuardMiddleware.builder()
                .maxTotalTokens(150L)
                .build();
        ModelClient wrapped = MiddlewareModelClient.wrap(stub, guard);

        ModelRequest req = ModelRequest.builder()
                .model("deepseek-chat")
                .message(Message.user().text("hi").build())
                .applicationMetadata(Collections.<String, Object>singletonMap("sessionId", "s-1"))
                .build();
        wrapped.create(req); // 100 tokens
        wrapped.createStream(req, null).await(); // +100 = 200 tokens
        Assert.assertEquals(200L, guard.snapshot("s-1").getTotalTokens());

        try {
            wrapped.create(req);
            Assert.fail("Expected BudgetExceededException");
        } catch (BudgetGuardMiddleware.BudgetExceededException expected) {
            Assert.assertEquals("s-1", expected.getScopeKey());
        }
    }

    @Test
    public void cognitiveTieringRoutesSystem1AndSystem2() {
        final List<String> seenModels = new ArrayList<String>();
        ModelClient routerTarget = new StubClient(Protocol.CHAT_COMPLETIONS) {
            @Override
            public ModelResult create(ModelRequest request) {
                seenModels.add(request.getModel());
                return textResult("ok", 1, 1);
            }
        };
        CognitiveTieringMiddleware tiering = CognitiveTieringMiddleware.builder()
                .system1Model("fast-mini")
                .system2Model("flagship-pro")
                .build();
        ModelClient wrapped = MiddlewareModelClient.wrap(routerTarget, tiering);

        wrapped.create(simpleRequest("Summarize: hello world"));
        wrapped.create(ModelRequest.builder()
                .model("default")
                .message(Message.user().text("Please analyze the root cause and refactor architecture").build())
                .tool(ToolDeclaration.function("t1").parameters(Collections.<String, Object>emptyMap()).build())
                .build());

        Assert.assertEquals("fast-mini", seenModels.get(0));
        Assert.assertEquals("flagship-pro", seenModels.get(1));
    }

    @Test
    public void shadowEvalRedactsMirrorsBothBlockingAndStreamingWithoutDisturbingPrimary() {
        final List<ModelRequest> shadowCaptured = new ArrayList<ModelRequest>();
        ModelClient primary = new StubClient(Protocol.CHAT_COMPLETIONS) {
            @Override
            public ModelResult create(ModelRequest request) {
                return textResult("same answer", 10, 5);
            }

            @Override
            public ModelCall createStream(ModelRequest request, ModelEventListener listener) {
                if (listener != null) {
                    listener.onEvent(ModelEvent.builder(Protocol.CHAT_COMPLETIONS, ModelEventType.ITEM_DELTA)
                            .delta(EventDeltaKind.TEXT, "same ")
                            .build());
                    listener.onEvent(ModelEvent.builder(Protocol.CHAT_COMPLETIONS, ModelEventType.ITEM_DELTA)
                            .delta(EventDeltaKind.TEXT, "answer")
                            .build());
                    listener.onEvent(ModelEvent.builder(Protocol.CHAT_COMPLETIONS, ModelEventType.DONE)
                            .usage(ModelUsage.builder().inputTokens(10).outputTokens(5).totalTokens(15).build())
                            .build());
                }
                return completedCall(textResult("same answer", 10, 5));
            }
        };
        ModelClient candidate = new StubClient(Protocol.GEMINI) {
            @Override
            public ModelResult create(ModelRequest request) throws ModelCallException {
                shadowCaptured.add(request);
                if (shadowCaptured.size() == 2) {
                    throw new ModelCallException(Protocol.GEMINI, 500, "err", null, true,
                            "candidate down", null, ModelCallStatus.FAILED);
                }
                return textResult("same answer", 8, 4);
            }
        };

        ShadowEvalMiddleware shadow = ShadowEvalMiddleware.builder(candidate)
                .candidateModel("gemini-2.5-flash")
                .sampleRate(0.5d)
                .sampler(new DoubleSupplier() {
                    @Override
                    public double getAsDouble() {
                        return 0.1d;
                    }
                })
                .executor(DIRECT_EXECUTOR)
                .build();

        ModelClient wrapped = MiddlewareModelClient.wrap(primary, shadow);
        ModelRequest req = ModelRequest.builder()
                .protocol(Protocol.CHAT_COMPLETIONS)
                .model("gpt-4o")
                .message(Message.user().text("hi").build())
                .applicationMetadata(Collections.<String, Object>singletonMap("secretToken", "s3cr3t"))
                .build();

        wrapped.create(req);
        wrapped.createStream(req, null).await();

        Assert.assertEquals(2, shadow.getRecordedReports().size());
        Assert.assertTrue(shadow.getRecordedReports().get(0).isTextMatch());
        Assert.assertTrue(shadow.getRecordedReports().get(0).isCandidateSuccess());
        // Redactor stripped applicationMetadata before sending to candidate
        Assert.assertTrue(shadowCaptured.get(0).getApplicationMetadata().isEmpty());
        Assert.assertEquals("gemini-2.5-flash", shadowCaptured.get(0).getModel());
        Assert.assertEquals(Protocol.GEMINI, shadowCaptured.get(0).getProtocol());

        // Candidate failure on stream shadow did not disrupt primary stream and recorded candidateError
        Assert.assertFalse(shadow.getRecordedReports().get(1).isCandidateSuccess());
        Assert.assertNotNull(shadow.getRecordedReports().get(1).getCandidateError());
    }

    private static ModelRequest simpleRequest(String text) {
        return ModelRequest.builder()
                .model("base-model")
                .message(Message.user().text(text).build())
                .build();
    }

    private static ModelResult textResult(String text, long in, long out) {
        return ModelResult.builder()
                .item(Message.assistant().text(text).build())
                .usage(ModelUsage.builder().inputTokens(in).outputTokens(out).totalTokens(in + out).build())
                .stopReason("stop")
                .build();
    }

    private static ModelCall completedCall(final ModelResult result) {
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

    private static ModelCall failedCall(final ModelCallException error) {
        return new ModelCall() {
            @Override
            public void cancel() {
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public ModelResult await() throws ModelCallException {
                throw error;
            }

            @Override
            public ModelCallStatus status() {
                return ModelCallStatus.FAILED;
            }
        };
    }

    private static class StubClient implements ModelClient {

        private final Endpoint endpoint;
        private final Capabilities capabilities;

        StubClient(Protocol protocol) {
            this.endpoint = Endpoint.builder(protocol).baseUrl("https://stub.local").build();
            this.capabilities = Capabilities.of(protocol, CapabilityMatrix.forProtocol(protocol));
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
        public ModelResult create(ModelRequest request) throws ModelCallException {
            return textResult("stub", 1, 1);
        }

        @Override
        public ModelCall createStream(ModelRequest request, ModelEventListener listener) {
            return completedCall(textResult("stub", 1, 1));
        }
    }
}
