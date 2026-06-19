package io.github.lnyocly.ai4j.extension;

import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEvent;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleEventType;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleHook;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AgentLifecycleExtensionRegistryTest {

    @Test
    public void shouldKeepExistingExtensionsCompatibleWithoutLifecycleHooks() {
        ExtensionRegistry registry = ExtensionRegistry.of(new ToolOnlyExtension())
                .enable("tool-only")
                .exposeTool("tool.echo");

        ExtensionRuntimeSnapshot snapshot = registry.snapshot();

        Assert.assertTrue(snapshot.getLifecycleHooks().isEmpty());
        Assert.assertEquals(1, snapshot.getTools().size());
    }

    @Test
    public void shouldRegisterLifecycleHooksForEnabledExtension() throws Exception {
        RecordingHook first = new RecordingHook("hook.first");
        RecordingHook second = new RecordingHook("hook.second");
        ExtensionRegistry registry = ExtensionRegistry.of(new LifecycleExtension(first, second))
                .enable("lifecycle-pack");

        ExtensionRuntimeSnapshot snapshot = registry.snapshot();

        Assert.assertEquals(2, snapshot.getLifecycleHooks().size());
        Assert.assertSame(first, snapshot.getLifecycleHooks().get(0));
        Assert.assertSame(second, snapshot.getLifecycleHooks().get(1));

        AgentLifecycleEvent event = AgentLifecycleEvent.builder(AgentLifecycleEventType.BEFORE_TURN)
                .runtime("react")
                .sessionId("s-1")
                .step(0)
                .message("turn")
                .payload("payload")
                .attribute("k", "v")
                .build();
        snapshot.getLifecycleHooks().get(0).onEvent(event);

        Assert.assertEquals(1, first.events.size());
        Assert.assertEquals(AgentLifecycleEventType.BEFORE_TURN, first.events.get(0).getType());
        Assert.assertEquals("react", first.events.get(0).getRuntime());
        Assert.assertEquals("s-1", first.events.get(0).getSessionId());
        Assert.assertEquals(Integer.valueOf(0), first.events.get(0).getStep());
        Assert.assertEquals("v", first.events.get(0).getAttributes().get("k"));
    }

    @Test
    public void shouldNotExposeLifecycleHooksForDisabledExtension() {
        ExtensionRegistry registry = ExtensionRegistry.of(new LifecycleExtension(new RecordingHook("hook.first")));

        Assert.assertTrue(registry.snapshot().getLifecycleHooks().isEmpty());
        Assert.assertEquals(1, registry.inspectRuntime("lifecycle-pack").getLifecycleHooks().size());
    }

    @Test
    public void shouldRejectLifecycleRegistrationWithoutDeclaredCapability() {
        ExtensionRegistry registry = ExtensionRegistry.of(new BadLifecycleCapabilityExtension())
                .enable("bad-lifecycle");

        try {
            registry.snapshot();
            Assert.fail("expected ExtensionException");
        } catch (ExtensionException ex) {
            Assert.assertTrue(ex.getMessage().contains("did not declare capability"));
            Assert.assertTrue(ex.getMessage().contains("lifecycle"));
        }
    }

    @Test
    public void shouldRejectDuplicateLifecycleHookNames() {
        ExtensionRegistry registry = ExtensionRegistry.of(new LifecycleExtension(
                new RecordingHook("hook.same"),
                new RecordingHook("hook.same")
        )).enable("lifecycle-pack");

        try {
            registry.snapshot();
            Assert.fail("expected ExtensionException");
        } catch (ExtensionException ex) {
            Assert.assertTrue(ex.getMessage().contains("duplicate lifecycle hook id"));
        }
    }

    @Test
    public void shouldParseLifecycleCapabilityId() {
        Assert.assertEquals(ExtensionCapability.LIFECYCLE, ExtensionCapability.fromId(" lifecycle "));
    }

    private static class LifecycleExtension implements Ai4jExtension {
        private final AgentLifecycleHook[] hooks;

        private LifecycleExtension(AgentLifecycleHook... hooks) {
            this.hooks = hooks;
        }

        public ExtensionManifest manifest() {
            return ExtensionManifest.builder()
                    .id("lifecycle-pack")
                    .name("Lifecycle Pack")
                    .version("1.0.0")
                    .vendor("tests")
                    .capability(ExtensionCapability.LIFECYCLE)
                    .build();
        }

        public void apply(ExtensionContext context) {
            if (hooks != null) {
                for (AgentLifecycleHook hook : hooks) {
                    context.lifecycle().register(hook);
                }
            }
        }
    }

    private static class BadLifecycleCapabilityExtension implements Ai4jExtension {
        public ExtensionManifest manifest() {
            return ExtensionManifest.builder()
                    .id("bad-lifecycle")
                    .capability(ExtensionCapability.TOOL)
                    .build();
        }

        public void apply(ExtensionContext context) {
            context.lifecycle().register(new RecordingHook("bad.lifecycle"));
        }
    }

    private static class ToolOnlyExtension implements Ai4jExtension {
        public ExtensionManifest manifest() {
            return ExtensionManifest.builder()
                    .id("tool-only")
                    .capability(ExtensionCapability.TOOL)
                    .build();
        }

        public void apply(ExtensionContext context) {
            context.tools().register(io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec.builder()
                            .name("tool.echo")
                            .inputSchema("{\"type\":\"object\"}")
                            .build(),
                    new io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor() {
                        public String execute(io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall call) {
                            return call == null ? "" : call.getArguments();
                        }
                    });
        }
    }

    private static class RecordingHook implements AgentLifecycleHook {
        private final String name;
        private final List<AgentLifecycleEvent> events = new ArrayList<AgentLifecycleEvent>();

        private RecordingHook(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public void onEvent(AgentLifecycleEvent event) {
            events.add(event);
        }
    }
}
