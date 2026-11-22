package io.github.lnyocly.ai4j.extension;

import io.github.lnyocly.ai4j.extension.command.CommandRegistry;
import io.github.lnyocly.ai4j.extension.guardrail.GuardrailRegistry;
import io.github.lnyocly.ai4j.extension.interceptor.InterceptorRegistry;
import io.github.lnyocly.ai4j.extension.lifecycle.LifecycleHookRegistry;
import io.github.lnyocly.ai4j.extension.prompt.PromptRegistry;
import io.github.lnyocly.ai4j.extension.skill.SkillRegistry;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import io.github.lnyocly.ai4j.extension.tool.ToolRegistry;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidationReport;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidator;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CordisMicrokernelTest {

    private static final ServiceKey<String> GREETING_KEY = ServiceKey.of("test.greeting", String.class);
    private static final ServiceKey<Integer> COUNTER_KEY = ServiceKey.of("test.counter", Integer.class);

    @Test
    public void compositeDisposableRunsLifoIdempotentlyAndPreservesSuppressedExceptions() {
        final List<String> order = new ArrayList<String>();
        Disposable.noop().dispose();
        Disposable.noop().close();

        Disposable composite = Disposable.composite(
                null,
                new Disposable() {
                    @Override
                    public void dispose() {
                        order.add("first");
                        throw new IllegalStateException("first-failure");
                    }
                },
                Disposable.noop(),
                new Disposable() {
                    @Override
                    public void dispose() {
                        order.add("second");
                    }
                },
                new Disposable() {
                    @Override
                    public void dispose() {
                        order.add("third");
                        throw new IllegalArgumentException("third-failure");
                    }
                }
        );

        try {
            composite.close();
            Assert.fail("expected ExtensionException");
        } catch (ExtensionException ex) {
            Assert.assertTrue(ex.getCause() instanceof IllegalArgumentException);
            Assert.assertEquals("third-failure", ex.getCause().getMessage());
            Assert.assertEquals(1, ex.getSuppressed().length);
            Assert.assertTrue(ex.getSuppressed()[0] instanceof IllegalStateException);
            Assert.assertEquals("first-failure", ex.getSuppressed()[0].getMessage());
        }

        Assert.assertEquals(Arrays.asList("third", "second", "first"), order);

        // Idempotent second call must not re-run or throw.
        composite.dispose();
        Assert.assertEquals(Arrays.asList("third", "second", "first"), order);

        // Collection overload also executes in reverse order once.
        final List<String> colOrder = new ArrayList<String>();
        Disposable fromCollection = Disposable.composite(Arrays.asList(
                new Disposable() {
                    @Override
                    public void dispose() {
                        colOrder.add("a");
                    }
                },
                new Disposable() {
                    @Override
                    public void dispose() {
                        colOrder.add("b");
                    }
                }
        ));
        fromCollection.dispose();
        fromCollection.dispose();
        Assert.assertEquals(Arrays.asList("b", "a"), colOrder);
    }

    @Test
    public void serviceKeyValidatesIdentityEqualityAndCasting() {
        ServiceKey<String> key1 = ServiceKey.of(" ai4j.service:greeting ", String.class);
        ServiceKey<String> key2 = ServiceKey.of("ai4j.service:greeting", String.class);
        ServiceKey<Integer> diffType = ServiceKey.of("ai4j.service:greeting", Integer.class);

        Assert.assertEquals("ai4j.service:greeting", key1.id());
        Assert.assertEquals(String.class, key1.type());
        Assert.assertEquals(key1, key2);
        Assert.assertEquals(key1.hashCode(), key2.hashCode());
        Assert.assertNotEquals(key1, diffType);
        Assert.assertTrue(key1.toString().contains("ai4j.service:greeting"));
        Assert.assertEquals("hello", key1.cast("hello"));
        Assert.assertNull(key1.cast(null));

        try {
            key1.cast(Integer.valueOf(42));
            Assert.fail("expected ClassCastException");
        } catch (ClassCastException expected) {
            Assert.assertTrue(expected.getMessage().contains("ai4j.service:greeting"));
        }

        try {
            ServiceKey.of("   ", String.class);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("service key id"));
        }

        try {
            ServiceKey.of("valid.id", null);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("service key type"));
        }
    }

    @Test
    public void legacyExtensionContextDefaultMethodsRemainCompatible() {
        ExtensionManifest manifest = ExtensionManifest.builder()
                .id("legacy-ctx")
                .capability(ExtensionCapability.TOOL)
                .build();
        ExtensionContext legacy = new ExtensionContext() {
            @Override
            public ExtensionManifest manifest() {
                return manifest;
            }

            @Override
            public ToolRegistry tools() {
                return null;
            }

            @Override
            public CommandRegistry commands() {
                return null;
            }

            @Override
            public SkillRegistry skills() {
                return null;
            }

            @Override
            public PromptRegistry prompts() {
                return null;
            }

            @Override
            public GuardrailRegistry guardrails() {
                return null;
            }

            @Override
            public LifecycleHookRegistry lifecycle() {
                return null;
            }

            @Override
            public InterceptorRegistry interceptors() {
                return null;
            }
        };

        Assert.assertFalse(legacy.findService(GREETING_KEY).isPresent());
        try {
            legacy.service(GREETING_KEY);
            Assert.fail("expected ExtensionException");
        } catch (ExtensionException expected) {
            Assert.assertTrue(expected.getMessage().contains("service not registered"));
        }
        try {
            legacy.effect(Disposable.noop());
            Assert.fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            Assert.assertTrue(expected.getMessage().contains("effect"));
        }
    }

    @Test
    public void effectsExecuteInLifoOrderOnDisableAndClose() {
        final List<String> events = new ArrayList<String>();
        final AtomicReference<ExtensionContext> capturedContext = new AtomicReference<ExtensionContext>();

        Ai4jExtension first = new SimpleExtension("ext-first", context -> {
            capturedContext.set(context);
            context.effect(() -> events.add("first-1"));
            Disposable early = context.effect(() -> events.add("first-early"));
            early.dispose();
            early.dispose(); // idempotent
            context.effect(() -> events.add("first-2"));
        });
        Ai4jExtension second = new SimpleExtension("ext-second", context -> {
            context.effect(() -> events.add("second-1"));
            context.effect(() -> events.add("second-2"));
        });

        ExtensionRegistry registry = ExtensionRegistry.of(first, second)
                .enable("ext-first")
                .enable("ext-second");
        registry.snapshot();

        Assert.assertEquals(Collections.singletonList("first-early"), events);

        registry.disable("ext-first");
        Assert.assertEquals(Arrays.asList("first-early", "first-2", "first-1"), events);

        // Stale ExtensionContext must reject mutations after disable.
        try {
            capturedContext.get().effect(() -> events.add("should-not-run"));
            Assert.fail("expected ExtensionException");
        } catch (ExtensionException expected) {
            Assert.assertTrue(expected.getMessage().contains("not active"));
        }

        registry.close();
        Assert.assertEquals(Arrays.asList("first-early", "first-2", "first-1", "second-2", "second-1"), events);
        // Idempotent close.
        registry.close();
        Assert.assertEquals(Arrays.asList("first-early", "first-2", "first-1", "second-2", "second-1"), events);
    }

    @Test
    public void earlyProvideToLateInjectBindsImmediatelyAndCleansUpLifo() {
        final List<String> events = new ArrayList<String>();

        Ai4jExtension provider = new SimpleExtension("provider", context -> {
            context.effect(() -> events.add("provider-effect"));
            context.provide(GREETING_KEY, "hello-world");
        });
        Ai4jExtension consumerA = new SimpleExtension("consumer-a", context -> {
            events.add("direct:" + context.service(GREETING_KEY));
            context.inject(GREETING_KEY, svc -> {
                events.add("bind-a:" + svc);
                return () -> {
                    events.add("unbind-a:stillPresent=" + context.findService(GREETING_KEY).isPresent());
                };
            });
        });
        Ai4jExtension consumerB = new SimpleExtension("consumer-b", context -> {
            context.inject(GREETING_KEY, svc -> {
                events.add("bind-b:" + svc);
                return () -> events.add("unbind-b:" + svc);
            });
            context.inject(GREETING_KEY, svc -> {
                events.add("consume-b:" + svc);
            });
        });

        ExtensionRegistry registry = ExtensionRegistry.of(provider, consumerA, consumerB)
                .enable("provider")
                .enable("consumer-a")
                .enable("consumer-b");
        registry.snapshot();

        Assert.assertEquals("hello-world", registry.service(GREETING_KEY));
        Assert.assertEquals(Arrays.asList(
                "direct:hello-world",
                "bind-a:hello-world",
                "bind-b:hello-world",
                "consume-b:hello-world"
        ), events);

        events.clear();
        // Disabling provider must dispose consumer bindings in LIFO order BEFORE removing the service,
        // and BEFORE earlier provider effects!
        registry.disable("provider");

        Assert.assertFalse(registry.findService(GREETING_KEY).isPresent());
        Assert.assertEquals(Arrays.asList(
                "unbind-b:hello-world",
                "unbind-a:stillPresent=true",
                "provider-effect"
        ), events);
    }

    @Test
    public void earlyInjectToLateProvideReactsAndRebindsWhenReplacementProviderEnables() {
        final List<String> events = new ArrayList<String>();
        final AtomicInteger consumerApplyCount = new AtomicInteger();

        Ai4jExtension consumer = new SimpleExtension("consumer", context -> {
            consumerApplyCount.incrementAndGet();
            context.inject(GREETING_KEY, svc -> {
                events.add("bind:" + svc);
                return () -> events.add("unbind:" + svc);
            });
        });
        Ai4jExtension provider1 = new SimpleExtension("provider-1", context ->
                context.provide(GREETING_KEY, "v1"));
        Ai4jExtension provider2 = new SimpleExtension("provider-2", context ->
                context.provide(GREETING_KEY, "v2"));

        ExtensionRegistry registry = ExtensionRegistry.of(consumer, provider1, provider2)
                .enable("consumer");
        registry.snapshot();
        Assert.assertTrue(events.isEmpty());
        Assert.assertFalse(registry.findService(GREETING_KEY).isPresent());

        // Late provider enables incrementally: consumer is NOT re-applied, its waiting inject fires.
        registry.enable("provider-1").snapshot();
        Assert.assertEquals(1, consumerApplyCount.get());
        Assert.assertEquals(Collections.singletonList("bind:v1"), events);

        // Provider-1 disables: binding disposes; service disappears.
        registry.disable("provider-1");
        Assert.assertEquals(Arrays.asList("bind:v1", "unbind:v1"), events);
        Assert.assertFalse(registry.findService(GREETING_KEY).isPresent());

        // Provider-2 enables later: waiting consumer automatically re-binds to v2!
        Assert.assertEquals("v2", registry.enable("provider-2").service(GREETING_KEY));
        Assert.assertEquals(1, consumerApplyCount.get());
        Assert.assertEquals(Arrays.asList("bind:v1", "unbind:v1", "bind:v2"), events);

        // Close cleans up remaining provider-2 binding and consumer cleanly.
        registry.close();
        Assert.assertEquals(Arrays.asList("bind:v1", "unbind:v1", "bind:v2", "unbind:v2"), events);
    }

    @Test
    public void duplicateServiceKeyIsRejectedAndManualProvideDisposalAllowsReplacement() {
        final List<String> events = new ArrayList<String>();

        Ai4jExtension p1 = new SimpleExtension("p1", context -> context.provide(COUNTER_KEY, Integer.valueOf(1)));
        Ai4jExtension p2 = new SimpleExtension("p2", context -> context.provide(COUNTER_KEY, Integer.valueOf(2)));

        ExtensionRegistry dupRegistry = ExtensionRegistry.of(p1, p2).enable("p1").enable("p2");
        try {
            dupRegistry.snapshot();
            Assert.fail("expected ExtensionException for duplicate service key");
        } catch (ExtensionException expected) {
            Assert.assertTrue(expected.getMessage().contains("duplicate service key"));
        }

        // Verify partial failed apply cleans up effects/services of failing extension while keeping p1 intact.
        Ai4jExtension failingAfterProvide = new SimpleExtension("failing-ext", context -> {
            context.effect(() -> events.add("rollback-effect"));
            context.provide(GREETING_KEY, "ephemeral");
            throw new ExtensionException("boom");
        });
        ExtensionRegistry rollbackRegistry = ExtensionRegistry.of(p1, failingAfterProvide)
                .enable("p1")
                .enable("failing-ext");
        try {
            rollbackRegistry.snapshot();
            Assert.fail("expected ExtensionException");
        } catch (ExtensionException expected) {
            Assert.assertTrue(expected.getMessage().contains("boom"));
        }
        Assert.assertEquals(Collections.singletonList("rollback-effect"), events);
        rollbackRegistry.disable("failing-ext");
        Assert.assertFalse(rollbackRegistry.findService(GREETING_KEY).isPresent());
        Assert.assertEquals(Integer.valueOf(1), rollbackRegistry.service(COUNTER_KEY));

        // Manual disposal of provide handle detaches bindings and allows re-providing.
        final AtomicReference<Disposable> provideHandle = new AtomicReference<Disposable>();
        Ai4jExtension dynamicProvider = new SimpleExtension("dynamic", context ->
                provideHandle.set(context.provide(GREETING_KEY, "first")));
        Ai4jExtension listener = new SimpleExtension("listener", context ->
                context.inject(GREETING_KEY, svc -> {
                    events.add("in:" + svc);
                    return () -> events.add("out:" + svc);
                }));
        events.clear();
        ExtensionRegistry manualRegistry = ExtensionRegistry.of(dynamicProvider, listener)
                .enable("dynamic")
                .enable("listener");
        manualRegistry.snapshot();
        Assert.assertEquals(Collections.singletonList("in:first"), events);

        provideHandle.get().dispose();
        provideHandle.get().dispose(); // idempotent
        Assert.assertEquals(Arrays.asList("in:first", "out:first"), events);
        Assert.assertFalse(manualRegistry.findService(GREETING_KEY).isPresent());

        // Null cleanup from ServiceInjector and manual inject disposal must both work safely.
        final AtomicReference<Disposable> injectHandle = new AtomicReference<Disposable>();
        Ai4jExtension nullCleanupListener = new SimpleExtension("null-cleanup", context -> {
            injectHandle.set(context.inject(GREETING_KEY, svc -> {
                events.add("null-binder:" + svc);
                return null;
            }));
        });
        Ai4jExtension secondProvider = new SimpleExtension("second-provider", context ->
                context.provide(GREETING_KEY, "second"));
        events.clear();
        ExtensionRegistry rebindRegistry = ExtensionRegistry.of(nullCleanupListener, secondProvider)
                .enable("null-cleanup")
                .enable("second-provider");
        rebindRegistry.snapshot();
        Assert.assertEquals(Collections.singletonList("null-binder:second"), events);
        injectHandle.get().dispose();
        injectHandle.get().dispose();
        rebindRegistry.disable("second-provider");
        Assert.assertFalse(rebindRegistry.findService(GREETING_KEY).isPresent());
    }

    @Test
    public void forkIsolatesChildMutationsAndCloseLeavesParentUntouched() {
        final List<String> events = new ArrayList<String>();
        final AtomicInteger parentStopCount = new AtomicInteger();
        final AtomicInteger childStopCount = new AtomicInteger();

        Ai4jExtension parentExt = new Ai4jExtension() {
            @Override
            public ExtensionManifest manifest() {
                return ExtensionManifest.builder()
                        .id("parent-ext")
                        .capability(ExtensionCapability.TOOL)
                        .build();
            }

            @Override
            public void apply(ExtensionContext context) {
                context.effect(() -> events.add("parent-effect-stop"));
                context.provide(GREETING_KEY, "from-parent");
                context.tools().register(
                        ExtensionToolSpec.builder().name("parent.tool").inputSchema("{\"type\":\"object\"}").build(),
                        call -> "parent");
            }

            @Override
            public void onStop() {
                parentStopCount.incrementAndGet();
            }
        };

        Ai4jExtension childExt = new Ai4jExtension() {
            @Override
            public ExtensionManifest manifest() {
                return ExtensionManifest.builder()
                        .id("child-ext")
                        .capability(ExtensionCapability.TOOL)
                        .build();
            }

            @Override
            public void apply(ExtensionContext context) {
                context.effect(() -> events.add("child-effect-stop"));
                context.provide(COUNTER_KEY, Integer.valueOf(99));
                context.inject(GREETING_KEY, svc -> {
                    events.add("child-bind:" + svc);
                    return () -> events.add("child-unbind:" + svc);
                });
                context.tools().register(
                        ExtensionToolSpec.builder().name("child.tool").inputSchema("{\"type\":\"object\"}").build(),
                        call -> "child");
            }

            @Override
            public void onStop() {
                childStopCount.incrementAndGet();
            }
        };

        ExtensionRegistry parent = ExtensionRegistry.of(parentExt, childExt)
                .enable("parent-ext")
                .exposeTool("parent.tool");

        ExtensionRegistry child = parent.fork()
                .enable("child-ext")
                .exposeTool("child.tool");

        // Parent services visible in child; child services/tools/enabledIds isolated from parent.
        Assert.assertEquals("from-parent", child.service(GREETING_KEY));
        Assert.assertEquals(Integer.valueOf(99), child.service(COUNTER_KEY));
        Assert.assertEquals(Optional.<Integer>empty(), parent.findService(COUNTER_KEY));
        Assert.assertEquals(Collections.singleton("parent-ext"), parent.getEnabledIds());
        Assert.assertEquals(Collections.singleton("child-ext"), child.getEnabledIds());
        Assert.assertEquals(1, parent.snapshot().getTools().size());
        Assert.assertEquals("plugin__parent-ext__parent.tool", parent.snapshot().getTools().get(0).getName());
        Assert.assertEquals(1, child.snapshot().getTools().size());
        Assert.assertEquals("plugin__child-ext__child.tool", child.snapshot().getTools().get(0).getName());
        Assert.assertEquals(Collections.singletonList("child-bind:from-parent"), events);

        // Child close disposes only child bindings/effects/services and leaves parent untouched.
        child.close();
        Assert.assertEquals(1, childStopCount.get());
        Assert.assertEquals(0, parentStopCount.get());
        Assert.assertEquals("from-parent", parent.service(GREETING_KEY));
        Assert.assertEquals(Arrays.asList("child-bind:from-parent", "child-unbind:from-parent", "child-effect-stop"), events);
        Assert.assertFalse(parent.findService(COUNTER_KEY).isPresent());
        Assert.assertEquals(1, parent.snapshot().getTools().size());

        // Parent close cleans up parent.
        parent.close();
        Assert.assertEquals(1, parentStopCount.get());
        Assert.assertEquals(Arrays.asList(
                "child-bind:from-parent",
                "child-unbind:from-parent",
                "child-effect-stop",
                "parent-effect-stop"
        ), events);
    }

    @Test
    public void forkSupportsChildShadowingAndLateParentProvision() {
        final List<String> events = new ArrayList<String>();

        Ai4jExtension lateParent = new SimpleExtension("late-parent", context ->
                context.provide(GREETING_KEY, "parent-late"));
        Ai4jExtension childConsumer = new SimpleExtension("child-consumer", context ->
                context.inject(GREETING_KEY, svc -> {
                    events.add("bind:" + svc);
                    return () -> events.add("unbind:" + svc);
                }));
        Ai4jExtension childShadow = new SimpleExtension("child-shadow", context ->
                context.provide(GREETING_KEY, "child-override"));

        ExtensionRegistry parent = ExtensionRegistry.of(lateParent, childConsumer, childShadow);
        ExtensionRegistry child = parent.fork().enable("child-consumer");
        child.snapshot();
        Assert.assertTrue(events.isEmpty());

        // Late parent provision triggers reactive binding in live child.
        parent.enable("late-parent").snapshot();
        Assert.assertEquals(Collections.singletonList("bind:parent-late"), events);

        // Child shadows parent key: old parent binding disposes, new child binding activates.
        child.enable("child-shadow").snapshot();
        Assert.assertEquals("child-override", child.service(GREETING_KEY));
        Assert.assertEquals("parent-late", parent.service(GREETING_KEY));
        Assert.assertEquals(Arrays.asList("bind:parent-late", "unbind:parent-late", "bind:child-override"), events);

        // Disabling child shadow restores parent service visibility and re-binds child consumer.
        child.disable("child-shadow");
        Assert.assertEquals("parent-late", child.service(GREETING_KEY));
        Assert.assertEquals(Arrays.asList(
                "bind:parent-late",
                "unbind:parent-late",
                "bind:child-override",
                "unbind:child-override",
                "bind:parent-late"
        ), events);

        // Closing child does not leak or stop parent.
        child.close();
        Assert.assertEquals("parent-late", parent.service(GREETING_KEY));
        Assert.assertEquals(Arrays.asList(
                "bind:parent-late",
                "unbind:parent-late",
                "bind:child-override",
                "unbind:child-override",
                "bind:parent-late",
                "unbind:parent-late"
        ), events);
        parent.close();
    }

    @Test
    public void inspectRuntimeAndValidatorCleanUpTemporaryEffects() {
        final AtomicInteger activeEffects = new AtomicInteger();

        Ai4jExtension ext = new Ai4jExtension() {
            @Override
            public ExtensionManifest manifest() {
                return ExtensionManifest.builder()
                        .id("clean-inspect")
                        .name("Clean Inspect")
                        .version("1.0.0")
                        .vendor("tests")
                        .capability(ExtensionCapability.TOOL)
                        .build();
            }

            @Override
            public void apply(ExtensionContext context) {
                activeEffects.incrementAndGet();
                context.effect(activeEffects::decrementAndGet);
                context.provide(GREETING_KEY, "temp");
                context.tools().register(
                        ExtensionToolSpec.builder()
                                .name("ping")
                                .description("Ping")
                                .inputSchema("{\"type\":\"object\"}")
                                .build(),
                        new ExtensionToolExecutor() {
                            @Override
                            public String execute(ExtensionToolCall call) {
                                return "pong";
                            }
                        });
            }
        };

        ExtensionRegistry registry = ExtensionRegistry.of(ext);
        ExtensionValidationReport report = ExtensionValidator.validate(registry, "clean-inspect");

        Assert.assertTrue(report.isValid());
        Assert.assertEquals(0, activeEffects.get());
        Assert.assertFalse(registry.findService(GREETING_KEY).isPresent());
    }

    private interface ContextConsumer {
        void accept(ExtensionContext context);
    }

    private static final class SimpleExtension implements Ai4jExtension {
        private final String id;
        private final ContextConsumer consumer;

        private SimpleExtension(String id, ContextConsumer consumer) {
            this.id = id;
            this.consumer = consumer;
        }

        @Override
        public ExtensionManifest manifest() {
            return ExtensionManifest.builder()
                    .id(id)
                    .name(id)
                    .capability(ExtensionCapability.TOOL)
                    .build();
        }

        @Override
        public void apply(ExtensionContext context) {
            consumer.accept(context);
        }
    }
}
