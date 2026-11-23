package io.github.lnyocly.ai4j.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.Disposable;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ServiceKey;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import io.github.lnyocly.ai4j.mcp.gateway.McpGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Cordis extension entry point for {@code ai4j-mcp}.
 *
 * <p>Provides a {@link McpGateway} via {@link #GATEWAY_SERVICE_KEY}, registers the
 * {@code mcp_call} extension tool, and binds a {@link Disposable} effect so disabling or
 * closing the {@link io.github.lnyocly.ai4j.extension.ExtensionRegistry} shuts down the
 * managed gateway cleanly.</p>
 */
public final class McpExtension implements Ai4jExtension {

    private static final Logger log = LoggerFactory.getLogger(McpExtension.class);

    public static final String EXTENSION_ID = "mcp";
    public static final String CALL_TOOL_NAME = "mcp_call";
    public static final String NAMESPACED_CALL_TOOL_NAME = "plugin__" + EXTENSION_ID + "__" + CALL_TOOL_NAME;
    public static final ServiceKey<McpGateway> GATEWAY_SERVICE_KEY =
            ServiceKey.of("ai4j.mcp.gateway", McpGateway.class);

    private static final String VERSION = "2.8.2";
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private final Supplier<McpGateway> gatewayFactory;

    public McpExtension() {
        this(new Supplier<McpGateway>() {
            @Override
            public McpGateway get() {
                return new McpGateway();
            }
        });
    }

    public McpExtension(final McpGateway gateway) {
        if (gateway == null) {
            throw new IllegalArgumentException("gateway must not be null");
        }
        this.gatewayFactory = new Supplier<McpGateway>() {
            @Override
            public McpGateway get() {
                return gateway;
            }
        };
    }

    public McpExtension(Supplier<McpGateway> gatewayFactory) {
        if (gatewayFactory == null) {
            throw new IllegalArgumentException("gatewayFactory must not be null");
        }
        this.gatewayFactory = gatewayFactory;
    }

    @Override
    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id(EXTENSION_ID)
                .name("AI4J MCP")
                .version(VERSION)
                .vendor("ai4j")
                .capability(ExtensionCapability.TOOL)
                .permission("mcp.invoke")
                .configPrefix("ai4j.extensions.mcp")
                .build();
    }

    @Override
    public void apply(ExtensionContext context) {
        final McpGateway gateway = gatewayFactory.get();
        if (gateway == null) {
            throw new IllegalStateException("gatewayFactory returned null");
        }
        final AtomicBoolean disposed = new AtomicBoolean(false);

        context.effect(new Disposable() {
            @Override
            public void dispose() {
                if (disposed.compareAndSet(false, true)) {
                    shutdownQuietly(gateway);
                }
            }
        });

        context.provide(GATEWAY_SERVICE_KEY, gateway);

        context.tools().register(callToolSpec(), new ExtensionToolExecutor() {
            @Override
            public String execute(ExtensionToolCall call) {
                Map<String, Object> payload = parseCallPayload(call);
                Object rawToolName = payload.get("toolName");
                String toolName = rawToolName == null ? null : String.valueOf(rawToolName).trim();
                if (toolName == null || toolName.isEmpty()) {
                    throw new IllegalArgumentException("toolName is required");
                }
                Object arguments = payload.get("arguments");
                if (gateway.isInitialized()) {
                    return gateway.callTool(toolName, arguments == null ? Collections.emptyMap() : arguments).join();
                }
                DefaultMcpToolBridge bridge = new DefaultMcpToolBridge();
                if (bridge.hasLocalMcpTool(toolName)) {
                    String argsJson = arguments == null ? "{}" : JsonCodec.toJson(arguments);
                    return bridge.invokeLocalMcpTool(toolName, argsJson);
                }
                throw new IllegalStateException("MCP gateway is not initialized and no local MCP tool matches: " + toolName);
            }
        });
    }

    public static ExtensionToolSpec callToolSpec() {
        return ExtensionToolSpec.builder()
                .name(CALL_TOOL_NAME)
                .description("Invoke an MCP tool exposed by the active MCP gateway or local @McpService registry.")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"toolName\":{\"type\":\"string\",\"description\":\"Name of the target MCP tool\"},\"arguments\":{\"type\":\"object\",\"description\":\"Arguments passed to the MCP tool\"}},\"required\":[\"toolName\"]}")
                .build();
    }

    private static Map<String, Object> parseCallPayload(ExtensionToolCall call) {
        String raw = call == null ? null : call.getArguments();
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> parsed = JsonCodec.fromJsonLenient(raw, MAP_TYPE);
        return parsed == null ? Collections.<String, Object>emptyMap() : parsed;
    }

    private static void shutdownQuietly(McpGateway gateway) {
        try {
            gateway.shutdown().get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Interrupted while shutting down MCP gateway", e);
        } catch (Exception e) {
            log.warn("Failed to shut down MCP gateway cleanly", e);
        }
    }
}
