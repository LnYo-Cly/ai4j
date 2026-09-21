package io.github.lnyocly.ai4j.extension.fixture;

import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;

/**
 * Probe extension used by {@code ExtensionIsolationTest}: it is copied into a
 * per-extension directory and loaded through {@code IsolatedExtensionLoader}, so
 * every loaded copy gets its own static state — the observable signal of
 * classloader isolation. Keep it free of inner classes so a single .class file
 * fully describes it.
 */
public class IsolatedProbeExtension implements Ai4jExtension {

    public static int applyCount;
    public static int stopCount;

    @Override
    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id("isolated-probe")
                .name("Isolated Probe")
                .capability(ExtensionCapability.TOOL)
                .build();
    }

    @Override
    public void apply(ExtensionContext context) {
        applyCount++;
        context.tools().register(
                ExtensionToolSpec.builder()
                        .name("probe.echo")
                        .description("echoes arguments")
                        .inputSchema("{\"type\":\"object\"}")
                        .build(),
                new ExtensionToolExecutor() {
                    @Override
                    public String execute(ExtensionToolCall call) {
                        return "probe:" + call.getArguments();
                    }
                });
    }

    @Override
    public void onStop() {
        stopCount++;
    }
}
