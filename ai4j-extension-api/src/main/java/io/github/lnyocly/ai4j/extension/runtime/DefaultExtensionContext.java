package io.github.lnyocly.ai4j.extension.runtime;

import io.github.lnyocly.ai4j.extension.Disposable;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionException;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ServiceInjector;
import io.github.lnyocly.ai4j.extension.ServiceKey;
import io.github.lnyocly.ai4j.extension.command.CommandRegistry;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandHandler;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandSpec;
import io.github.lnyocly.ai4j.extension.guardrail.ExtensionGuardrail;
import io.github.lnyocly.ai4j.extension.guardrail.GuardrailRegistry;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionModelRequestInterceptor;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionPromptInterceptor;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionToolCallInterceptor;
import io.github.lnyocly.ai4j.extension.interceptor.InterceptorRegistry;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleHook;
import io.github.lnyocly.ai4j.extension.lifecycle.LifecycleHookRegistry;
import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;
import io.github.lnyocly.ai4j.extension.prompt.PromptRegistry;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import io.github.lnyocly.ai4j.extension.skill.SkillRegistry;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import io.github.lnyocly.ai4j.extension.tool.ToolRegistry;

import java.util.Optional;
import java.util.function.Consumer;

public final class DefaultExtensionContext implements ExtensionContext {

    private final ExtensionManifest manifest;
    private final ExtensionRuntimeState runtimeState;
    private final ExtensionRuntimeState.ExtensionScope scope;
    private final ToolRegistry tools;
    private final CommandRegistry commands;
    private final SkillRegistry skills;
    private final PromptRegistry prompts;
    private final GuardrailRegistry guardrails;
    private final LifecycleHookRegistry lifecycle;
    private final InterceptorRegistry interceptors;

    public DefaultExtensionContext(final ExtensionManifest manifest, final ExtensionRuntimeState runtimeState) {
        if (manifest == null) {
            throw new IllegalArgumentException("extension manifest must not be null");
        }
        if (runtimeState == null) {
            throw new IllegalArgumentException("extension runtime state must not be null");
        }
        this.manifest = manifest;
        this.runtimeState = runtimeState;
        this.scope = runtimeState.activateScope(manifest);
        this.tools = new ToolRegistry() {
            public void register(ExtensionToolSpec spec, ExtensionToolExecutor executor) {
                requireActiveAndCapability(ExtensionCapability.TOOL);
                runtimeState.registerTool(manifest, spec, executor);
            }
        };
        this.commands = new CommandRegistry() {
            public void register(ExtensionCommandSpec spec, ExtensionCommandHandler handler) {
                requireActiveAndCapability(ExtensionCapability.COMMAND);
                runtimeState.registerCommand(manifest, spec, handler);
            }
        };
        this.skills = new SkillRegistry() {
            public void register(ExtensionSkillResource resource) {
                requireActiveAndCapability(ExtensionCapability.SKILL);
                runtimeState.registerSkill(manifest, resource);
            }
        };
        this.prompts = new PromptRegistry() {
            public void register(ExtensionPromptResource resource) {
                requireActiveAndCapability(ExtensionCapability.PROMPT);
                runtimeState.registerPrompt(manifest, resource);
            }
        };
        this.guardrails = new GuardrailRegistry() {
            public void register(ExtensionGuardrail guardrail) {
                requireActiveAndCapability(ExtensionCapability.GUARDRAIL);
                runtimeState.registerGuardrail(manifest, guardrail);
            }
        };
        this.lifecycle = new LifecycleHookRegistry() {
            public void register(AgentLifecycleHook hook) {
                requireActiveAndCapability(ExtensionCapability.LIFECYCLE);
                runtimeState.registerLifecycleHook(manifest, hook);
            }
        };
        this.interceptors = new InterceptorRegistry() {
            public void registerToolCall(ExtensionToolCallInterceptor interceptor) {
                requireActiveAndCapability(ExtensionCapability.INTERCEPTOR);
                runtimeState.registerToolCallInterceptor(manifest, interceptor);
            }

            public void registerPrompt(ExtensionPromptInterceptor interceptor) {
                requireActiveAndCapability(ExtensionCapability.INTERCEPTOR);
                runtimeState.registerPromptInterceptor(manifest, interceptor);
            }

            public void registerModelRequest(ExtensionModelRequestInterceptor interceptor) {
                requireActiveAndCapability(ExtensionCapability.INTERCEPTOR);
                runtimeState.registerModelRequestInterceptor(manifest, interceptor);
            }
        };
    }

    public ExtensionManifest manifest() {
        return manifest;
    }

    public ToolRegistry tools() {
        return tools;
    }

    public CommandRegistry commands() {
        return commands;
    }

    public SkillRegistry skills() {
        return skills;
    }

    public PromptRegistry prompts() {
        return prompts;
    }

    public GuardrailRegistry guardrails() {
        return guardrails;
    }

    public LifecycleHookRegistry lifecycle() {
        return lifecycle;
    }

    public InterceptorRegistry interceptors() {
        return interceptors;
    }

    @Override
    public Disposable effect(Disposable cleanup) {
        return runtimeState.registerEffect(scope, cleanup);
    }

    @Override
    public <T> Disposable provide(ServiceKey<T> key, T implementation) {
        return runtimeState.provideService(scope, key, implementation);
    }

    @Override
    public <T> Optional<T> findService(ServiceKey<T> key) {
        return runtimeState.findService(key);
    }

    @Override
    public <T> T service(ServiceKey<T> key) {
        return runtimeState.requireService(key);
    }

    @Override
    public <T> Disposable inject(ServiceKey<T> key, ServiceInjector<T> binder) {
        return runtimeState.injectService(scope, key, binder);
    }

    @Override
    public <T> Disposable inject(ServiceKey<T> key, final Consumer<T> consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("service consumer must not be null");
        }
        return inject(key, new ServiceInjector<T>() {
            @Override
            public Disposable inject(T service) {
                consumer.accept(service);
                return Disposable.noop();
            }
        });
    }

    private void requireActiveAndCapability(ExtensionCapability capability) {
        runtimeState.requireActiveScope(scope);
        if (!manifest.hasCapability(capability)) {
            throw new ExtensionException("extension " + manifest.getId() + " did not declare capability: " + capability.getId());
        }
    }
}
