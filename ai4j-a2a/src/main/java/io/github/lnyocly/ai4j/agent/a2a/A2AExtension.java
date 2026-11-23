package io.github.lnyocly.ai4j.agent.a2a;

import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.Disposable;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ServiceKey;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cordis extension registering A2A protocol services and an {@code a2a_send_task} tool.
 */
public final class A2AExtension implements Ai4jExtension {

    public static final String EXTENSION_ID = "a2a";
    public static final String TOOL_NAME = "a2a_send_task";
    public static final String NAMESPACED_TOOL_NAME = "plugin__" + EXTENSION_ID + "__" + TOOL_NAME;
    public static final ServiceKey<A2AClient> CLIENT_KEY =
            ServiceKey.of("ai4j.a2a.client", A2AClient.class);

    private static final String VERSION = "2.8.2";

    private final AtomicBoolean active = new AtomicBoolean(false);
    private volatile A2AClient activeClient;

    @Override
    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id(EXTENSION_ID)
                .name("AI4J A2A Protocol")
                .version(VERSION)
                .vendor("ai4j")
                .capability(ExtensionCapability.TOOL)
                .permission("network.a2a")
                .configPrefix("ai4j.extensions.a2a")
                .build();
    }

    @Override
    public void apply(ExtensionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("extension context must not be null");
        }
        final A2AClient client = new A2AClient();
        this.activeClient = client;
        this.active.set(true);

        context.provide(CLIENT_KEY, client);

        context.tools().register(toolSpec(), new ExtensionToolExecutor() {
            @Override
            public String execute(ExtensionToolCall call) throws Exception {
                if (!active.get()) {
                    throw new IllegalStateException("A2A extension is not active");
                }
                String args = call == null ? null : call.getArguments();
                com.fasterxml.jackson.databind.node.ObjectNode obj = A2AJson.parseObjectOrNull(args);
                String agentUrl = A2AJson.text(obj, "agentUrl");
                String message = A2AJson.text(obj, "message");
                if (agentUrl == null || agentUrl.trim().isEmpty()) {
                    throw new IllegalArgumentException("agentUrl is required");
                }
                return client.sendTask(agentUrl.trim(), message == null ? "" : message);
            }
        });

        context.effect(new Disposable() {
            @Override
            public void dispose() {
                active.set(false);
                if (activeClient == client) {
                    activeClient = null;
                }
            }
        });
    }

    @Override
    public void onStop() {
        this.active.set(false);
        this.activeClient = null;
    }

    public boolean isActive() {
        return active.get();
    }

    public static ExtensionToolSpec toolSpec() {
        return ExtensionToolSpec.builder()
                .name(TOOL_NAME)
                .description("Send a task message to a remote Agent-to-Agent (A2A) endpoint and return the text reply.")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"agentUrl\":{\"type\":\"string\",\"description\":\"Base URL of the remote A2A agent\"},\"message\":{\"type\":\"string\",\"description\":\"Task prompt or message to send\"}},\"required\":[\"agentUrl\",\"message\"]}")
                .build();
    }
}
