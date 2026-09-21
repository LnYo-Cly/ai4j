package io.github.lnyocly.ai4j.agent.extension;

import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.interceptor.ModelRequestHook;
import io.github.lnyocly.ai4j.agent.interceptor.PromptDecision;
import io.github.lnyocly.ai4j.agent.interceptor.PromptInterceptor;
import io.github.lnyocly.ai4j.agent.interceptor.ToolCallDecision;
import io.github.lnyocly.ai4j.agent.interceptor.ToolInterceptor;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionModelRequest;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionModelRequestDecision;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionModelRequestInterceptor;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionPromptDecision;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionPromptInterceptor;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionPromptRequest;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionToolCallDecision;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionToolCallInterceptor;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionToolCallRequest;

import java.util.List;

/**
 * Adapts extension-contributed interceptors (declared via the {@code interceptor} capability)
 * onto the agent runtime's native interception surfaces.
 *
 * <p>Composition semantics when a host interceptor is also configured: the host evaluates
 * first, then extension interceptors in registration order. Each interceptor sees the current
 * effective value — a {@code MODIFY} rewrites it for the rest of the chain; {@code BLOCK} /
 * {@code ROUTE_TO} short-circuit. Model-request interceptors are transform-only and apply
 * sequentially.</p>
 */
public final class ExtensionInterceptors {

    private ExtensionInterceptors() {
    }

    /** Adapt a single extension tool-call interceptor to the native {@link ToolInterceptor}. */
    public static ToolInterceptor adaptToolCall(final ExtensionToolCallInterceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException("interceptor must not be null");
        }
        return new ToolInterceptor() {
            public ToolCallDecision beforeToolCall(AgentToolCall call, AgentContext context) {
                return mapToolDecision(interceptor.beforeToolCall(toRequest(call)), call);
            }

            public ToolCallDecision afterToolCall(AgentToolCall call, String output, AgentContext context) {
                return mapToolDecision(interceptor.afterToolCall(toRequest(call), output), call);
            }
        };
    }

    /** Adapt a single extension prompt interceptor to the native {@link PromptInterceptor}. */
    public static PromptInterceptor adaptPrompt(final ExtensionPromptInterceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException("interceptor must not be null");
        }
        return new PromptInterceptor() {
            public PromptDecision beforePrompt(String input, AgentContext context) {
                return mapPromptDecision(interceptor.intercept(new ExtensionPromptRequest(input)));
            }
        };
    }

    /** Adapt a single extension model-request interceptor to the native {@link ModelRequestHook}. */
    public static ModelRequestHook adaptModelRequest(final ExtensionModelRequestInterceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException("interceptor must not be null");
        }
        return new ModelRequestHook() {
            public AgentPrompt beforeModelRequest(AgentPrompt prompt, AgentContext context) {
                ExtensionModelRequestDecision decision = interceptor.intercept(toRequest(prompt));
                return decision == null ? prompt : applyModelDecision(prompt, decision);
            }
        };
    }

    /**
     * Compose a host {@link ToolInterceptor} with extension tool-call interceptors. Evaluation
     * order: host first, then extensions in list order. Each sees the current effective call —
     * a {@code MODIFY} updates it for downstream evaluators; {@code BLOCK} / {@code ROUTE_TO}
     * end the chain. Returns null when nothing is configured.
     */
    public static ToolInterceptor composeToolCall(final ToolInterceptor host,
                                                final List<ExtensionToolCallInterceptor> extensions) {
        if (host == null && (extensions == null || extensions.isEmpty())) {
            return null;
        }
        return new ToolInterceptor() {
            public ToolCallDecision beforeToolCall(AgentToolCall call, AgentContext context) {
                AgentToolCall current = call;
                if (host != null) {
                    ToolCallDecision decision = host.beforeToolCall(current, context);
                    if (decision != null && decision.getType() == ToolCallDecision.Type.MODIFY) {
                        current = decision.getModifiedCall();
                    } else if (decision != null && decision.getType() != ToolCallDecision.Type.ALLOW) {
                        return decision;
                    }
                }
                if (extensions != null) {
                    for (ExtensionToolCallInterceptor interceptor : extensions) {
                        ToolCallDecision decision = mapToolDecision(
                                interceptor.beforeToolCall(toRequest(current)), current);
                        if (decision.getType() == ToolCallDecision.Type.MODIFY) {
                            current = decision.getModifiedCall();
                        } else if (decision.getType() != ToolCallDecision.Type.ALLOW) {
                            return decision;
                        }
                    }
                }
                return current == call ? ToolCallDecision.allow() : ToolCallDecision.modify(current);
            }

            public ToolCallDecision afterToolCall(AgentToolCall call, String output, AgentContext context) {
                if (host != null) {
                    ToolCallDecision decision = host.afterToolCall(call, output, context);
                    if (decision != null && decision.getType() != ToolCallDecision.Type.ALLOW) {
                        return decision;
                    }
                }
                if (extensions != null) {
                    for (ExtensionToolCallInterceptor interceptor : extensions) {
                        ToolCallDecision decision = mapToolDecision(
                                interceptor.afterToolCall(toRequest(call), output), call);
                        if (decision != null && decision.getType() != ToolCallDecision.Type.ALLOW) {
                            return decision;
                        }
                    }
                }
                return ToolCallDecision.allow();
            }
        };
    }

    /**
     * Compose a host {@link PromptInterceptor} with extension prompt interceptors. Same ordering
     * semantics as {@link #composeToolCall}: host first, MODIFY rewrites the input seen by
     * downstream evaluators, BLOCK short-circuits. Returns null when nothing is configured.
     */
    public static PromptInterceptor composePrompt(final PromptInterceptor host,
                                                  final List<ExtensionPromptInterceptor> extensions) {
        if (host == null && (extensions == null || extensions.isEmpty())) {
            return null;
        }
        return new PromptInterceptor() {
            public PromptDecision beforePrompt(String input, AgentContext context) {
                String current = input;
                if (host != null) {
                    PromptDecision decision = host.beforePrompt(current, context);
                    if (decision == null) {
                        return PromptDecision.allow();
                    }
                    if (decision.getType() != PromptDecision.Type.ALLOW) {
                        if (decision.getType() == PromptDecision.Type.MODIFY) {
                            current = decision.getModifiedInput();
                        } else {
                            return decision;
                        }
                    }
                }
                if (extensions != null) {
                    for (ExtensionPromptInterceptor interceptor : extensions) {
                        PromptDecision decision = mapPromptDecision(
                                interceptor.intercept(new ExtensionPromptRequest(current)));
                        if (decision == null) {
                            continue;
                        }
                        if (decision.getType() == PromptDecision.Type.MODIFY) {
                            current = decision.getModifiedInput();
                        } else if (decision.getType() != PromptDecision.Type.ALLOW) {
                            return decision;
                        }
                    }
                }
                return current == input ? PromptDecision.allow() : PromptDecision.modify(current);
            }
        };
    }

    /**
     * Compose a host {@link ModelRequestHook} with extension model-request interceptors. The
     * host transforms the prompt first; each extension then sees and adjusts the result in
     * list order. Returns null when nothing is configured.
     */
    public static ModelRequestHook composeModelRequest(final ModelRequestHook host,
                                                       final List<ExtensionModelRequestInterceptor> extensions) {
        if (host == null && (extensions == null || extensions.isEmpty())) {
            return null;
        }
        return new ModelRequestHook() {
            public AgentPrompt beforeModelRequest(AgentPrompt prompt, AgentContext context) {
                AgentPrompt current = prompt;
                if (host != null) {
                    AgentPrompt transformed = host.beforeModelRequest(current, context);
                    if (transformed != null) {
                        current = transformed;
                    }
                }
                if (extensions != null) {
                    for (ExtensionModelRequestInterceptor interceptor : extensions) {
                        ExtensionModelRequestDecision decision =
                                interceptor.intercept(toRequest(current));
                        current = applyModelDecision(current, decision);
                    }
                }
                return current;
            }
        };
    }

    private static ExtensionToolCallRequest toRequest(AgentToolCall call) {
        return new ExtensionToolCallRequest(
                call == null ? null : call.getName(),
                call == null ? null : call.getArguments(),
                call == null ? null : call.getCallId(),
                call == null ? null : call.getType()
        );
    }

    private static ExtensionModelRequest toRequest(AgentPrompt prompt) {
        return new ExtensionModelRequest(
                prompt == null ? null : prompt.getModel(),
                prompt == null ? null : prompt.getSystemPrompt(),
                prompt == null ? null : prompt.getInstructions(),
                prompt == null ? null : prompt.getTemperature(),
                prompt == null ? null : prompt.getTopP(),
                prompt == null ? null : prompt.getMaxOutputTokens()
        );
    }

    private static ToolCallDecision mapToolDecision(ExtensionToolCallDecision decision, AgentToolCall original) {
        if (decision == null || decision.getType() == ExtensionToolCallDecision.Type.ALLOW) {
            return ToolCallDecision.allow();
        }
        switch (decision.getType()) {
            case BLOCK:
                return ToolCallDecision.block(decision.getReason());
            case MODIFY:
                AgentToolCall modified = AgentToolCall.builder()
                        .name(decision.getModifiedName() != null ? decision.getModifiedName()
                                : original == null ? null : original.getName())
                        .arguments(decision.getModifiedArguments() != null ? decision.getModifiedArguments()
                                : original == null ? null : original.getArguments())
                        .callId(original == null ? null : original.getCallId())
                        .type(original == null ? null : original.getType())
                        .metadata(original == null ? null : original.getMetadata())
                        .build();
                return ToolCallDecision.modify(modified);
            case ROUTE_TO:
                SandboxSpec spec = SandboxSpec.builder()
                        .providerId(decision.getSandboxProviderId())
                        .profile(decision.getSandboxProfile())
                        .build();
                SandboxCommand command = SandboxCommand.builder()
                        .command(decision.getSandboxCommand())
                        .build();
                return ToolCallDecision.routeTo(spec, command);
            default:
                return ToolCallDecision.allow();
        }
    }

    private static PromptDecision mapPromptDecision(ExtensionPromptDecision decision) {
        if (decision == null || decision.getType() == ExtensionPromptDecision.Type.ALLOW) {
            return PromptDecision.allow();
        }
        switch (decision.getType()) {
            case BLOCK:
                return PromptDecision.block(decision.getReason());
            case MODIFY:
                return PromptDecision.modify(decision.getModifiedInput());
            default:
                return PromptDecision.allow();
        }
    }

    private static AgentPrompt applyModelDecision(AgentPrompt prompt, ExtensionModelRequestDecision decision) {
        if (prompt == null || decision == null
                || decision.getType() == ExtensionModelRequestDecision.Type.ALLOW) {
            return prompt;
        }
        AgentPrompt.AgentPromptBuilder builder = prompt.toBuilder();
        if (decision.getSystemPrompt() != null) {
            builder.systemPrompt(decision.getSystemPrompt());
        }
        if (decision.getInstructions() != null) {
            builder.instructions(decision.getInstructions());
        }
        if (decision.getTemperature() != null) {
            builder.temperature(decision.getTemperature());
        }
        if (decision.getTopP() != null) {
            builder.topP(decision.getTopP());
        }
        if (decision.getMaxOutputTokens() != null) {
            builder.maxOutputTokens(decision.getMaxOutputTokens());
        }
        return builder.build();
    }
}
