package io.github.lnyocly.ai4j.agent;

import io.github.lnyocly.ai4j.agent.event.AgentEventPublisher;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutor;
import io.github.lnyocly.ai4j.agent.codeact.CodeActOptions;
import io.github.lnyocly.ai4j.agent.context.ContextBudget;
import io.github.lnyocly.ai4j.agent.context.ContextProjector;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;
import io.github.lnyocly.ai4j.agent.lifecycle.AgentLifecycleHookDispatcher;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.permission.AgentExecutionEnvironment;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionPolicy;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.agent.interceptor.ToolInterceptor;
import io.github.lnyocly.ai4j.agent.interceptor.PromptInterceptor;
import io.github.lnyocly.ai4j.agent.compact.CompactPolicy;
import io.github.lnyocly.ai4j.agent.interceptor.ModelRequestHook;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder(toBuilder = true)
public class AgentContext {

    private AgentModelClient modelClient;

    private AgentToolRegistry toolRegistry;

    private ToolExecutor toolExecutor;

    private ToolInterceptor toolInterceptor;

    private PromptInterceptor promptInterceptor;

    private SandboxProvider sandboxProvider;
    private CompactPolicy compactPolicy;
    private ModelRequestHook modelRequestHook;

    private CodeExecutor codeExecutor;

    private AgentMemory memory;

    private AgentOptions options;

    private CodeActOptions codeActOptions;

    private ContextProjector contextProjector;

    private ContextBudget contextBudget;

    private AgentEventPublisher eventPublisher;

    private AgentLifecycleHookDispatcher lifecycleHooks;

    private AgentPermissionPolicy permissionPolicy;

    private AgentExecutionEnvironment executionEnvironment;

    private String sessionId;

    private String model;

    private String instructions;

    private String systemPrompt;

    private Double temperature;

    private Double topP;

    private Integer maxOutputTokens;

    private Object reasoning;

    private Object toolChoice;

    private Boolean parallelToolCalls;

    private Boolean store;

    private String user;

    private Map<String, Object> extraBody;
}
