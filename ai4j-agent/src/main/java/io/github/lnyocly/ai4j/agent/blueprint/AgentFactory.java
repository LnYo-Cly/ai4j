package io.github.lnyocly.ai4j.agent.blueprint;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentBuilder;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.context.ContextBudget;
import io.github.lnyocly.ai4j.agent.context.ContextProjector;
import io.github.lnyocly.ai4j.agent.event.AgentEventPublisher;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.permission.AgentExecutionEnvironment;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionPolicy;
import io.github.lnyocly.ai4j.agent.session.AgentSessionStore;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Creates an {@link Agent} from a validated single-agent blueprint.
 *
 * P1-B deliberately uses host-supplied dependencies. It does not read provider
 * tokens, resolve local profiles, install plugins, or create a real sandbox.
 */
public class AgentFactory {

    private final AgentBlueprintValidator validator;

    public AgentFactory() {
        this(new AgentBlueprintValidator());
    }

    public AgentFactory(AgentBlueprintValidator validator) {
        this.validator = validator == null ? new AgentBlueprintValidator() : validator;
    }

    public Agent create(AgentBlueprint blueprint, AgentFactoryContext context) {
        AgentBuilder builder = builder(blueprint, context);
        return builder.build();
    }

    public AgentBuilder builder(AgentBlueprint blueprint, AgentFactoryContext context) {
        AgentFactoryContext resolvedContext = context == null ? AgentFactoryContext.builder().build() : context;
        AgentBlueprintValidationReport report = validator.validate(blueprint);
        if (!report.isValid()) {
            throw new AgentFactoryException("blueprint.validation.failed", "Agent Blueprint validation failed.", report);
        }
        if (blueprint.getSandbox() != null && Boolean.TRUE.equals(blueprint.getSandbox().getEnabled()) && !resolvedContext.isAllowSandboxDeclaration()) {
            throw new AgentFactoryException("blueprint.sandbox.unsupported", "sandbox.enabled is declared, but P1-B AgentFactory does not create sandbox sessions.", report);
        }

        AgentModelClient modelClient = resolvedContext.getModelClient();
        if (modelClient == null) {
            throw new AgentFactoryException("blueprint.modelClient.required", "AgentModelClient is required. Provide it from the host; AgentFactory does not read provider tokens or profiles.", report);
        }

        if (trimToNull(blueprint.getModel() == null ? null : blueprint.getModel().getModel()) == null) {
            throw new AgentFactoryException("blueprint.model.model.requiredForFactory", "model.model is required by AgentFactory. model.profile is host metadata only and is not resolved by P1-B.", report);
        }

        AgentBuilder builder = selectBuilder(blueprint);
        builder.modelClient(modelClient);
        applyModel(builder, blueprint.getModel());
        applyInstructions(builder, blueprint.getInstructions());
        applyWorkflowOptions(builder, blueprint.getWorkflow(), resolvedContext.getBaseOptions());
        applyHostDependencies(builder, resolvedContext);
        return builder;
    }

    private AgentBuilder selectBuilder(AgentBlueprint blueprint) {
        AgentBlueprintWorkflow workflow = blueprint.getWorkflow();
        String mode = trimToNull(workflow == null ? null : workflow.getMode());
        if ("codeact".equals(mode)) {
            return Agents.codeAct();
        }
        return Agents.react();
    }

    private void applyModel(AgentBuilder builder, AgentBlueprintModel model) {
        if (model == null) {
            return;
        }
        builder.model(trimToNull(model.getModel()));
        Map<String, Object> options = model.getOptions();
        if (options == null || options.isEmpty()) {
            return;
        }
        builder.temperature(readDouble(options.get("temperature"), "$.model.options.temperature"));
        builder.topP(readDouble(firstNonNull(options.get("topP"), options.get("top_p")), "$.model.options.topP"));
        builder.maxOutputTokens(readInteger(firstNonNull(options.get("maxOutputTokens"), options.get("max_output_tokens")), "$.model.options.maxOutputTokens"));
        if (options.containsKey("reasoning")) {
            builder.reasoning(options.get("reasoning"));
        }
        if (options.containsKey("toolChoice")) {
            builder.toolChoice(options.get("toolChoice"));
        } else if (options.containsKey("tool_choice")) {
            builder.toolChoice(options.get("tool_choice"));
        }
        builder.parallelToolCalls(readBoolean(firstNonNull(options.get("parallelToolCalls"), options.get("parallel_tool_calls")), "$.model.options.parallelToolCalls"));
        builder.store(readBoolean(options.get("store"), "$.model.options.store"));
        if (options.containsKey("user")) {
            builder.user(asString(options.get("user")));
        }
    }

    private void applyInstructions(AgentBuilder builder, AgentBlueprintInstructions instructions) {
        if (instructions == null) {
            return;
        }
        builder.systemPrompt(trimToNull(instructions.getSystem()));
        builder.instructions(trimToNull(instructions.getDeveloper()));
    }

    private void applyWorkflowOptions(AgentBuilder builder, AgentBlueprintWorkflow workflow, AgentOptions baseOptions) {
        AgentOptions.AgentOptionsBuilder optionsBuilder = baseOptions == null
                ? AgentOptions.builder()
                : baseOptions.toBuilder();
        if (workflow != null && workflow.getMaxTurns() != null) {
            optionsBuilder.maxSteps(workflow.getMaxTurns().intValue());
        }
        builder.options(optionsBuilder.build());
    }

    private void applyHostDependencies(AgentBuilder builder, AgentFactoryContext context) {
        AgentToolRegistry toolRegistry = context.getToolRegistry();
        builder.toolRegistry(toolRegistry == null ? StaticToolRegistry.empty() : toolRegistry);
        ToolExecutor toolExecutor = context.getToolExecutor();
        if (toolExecutor != null) {
            builder.toolExecutor(toolExecutor);
        }
        Supplier<AgentMemory> memorySupplier = context.getMemorySupplier();
        if (memorySupplier != null) {
            builder.memorySupplier(memorySupplier);
        }
        AgentPermissionPolicy permissionPolicy = context.getPermissionPolicy();
        if (permissionPolicy != null) {
            builder.permissionPolicy(permissionPolicy);
        }
        AgentExecutionEnvironment environment = context.getExecutionEnvironment();
        if (environment != null) {
            builder.executionEnvironment(environment);
        }
        ContextProjector contextProjector = context.getContextProjector();
        if (contextProjector != null) {
            builder.contextProjector(contextProjector);
        }
        ContextBudget contextBudget = context.getContextBudget();
        if (contextBudget != null) {
            builder.contextBudget(contextBudget);
        }
        AgentEventPublisher eventPublisher = context.getEventPublisher();
        if (eventPublisher != null) {
            builder.eventPublisher(eventPublisher);
        }
        AgentSessionStore sessionStore = context.getSessionStore();
        if (sessionStore != null) {
            builder.sessionStore(sessionStore);
        }
        if (context.getExtraBody() != null) {
            builder.extraBody(context.getExtraBody());
        }
    }

    private Object firstNonNull(Object first, Object second) {
        return first == null ? second : first;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double readDouble(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Double.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof String) {
            try {
                return Double.valueOf(((String) value).trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new AgentFactoryException("blueprint.option.number.invalid", path + " must be a number.");
    }

    private Integer readInteger(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            double numeric = ((Number) value).doubleValue();
            if (Math.floor(numeric) == numeric) {
                return Integer.valueOf(((Number) value).intValue());
            }
        }
        if (value instanceof String) {
            try {
                return Integer.valueOf(((String) value).trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new AgentFactoryException("blueprint.option.integer.invalid", path + " must be an integer.");
    }

    private Boolean readBoolean(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("true".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
        }
        throw new AgentFactoryException("blueprint.option.boolean.invalid", path + " must be a boolean.");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }
}
