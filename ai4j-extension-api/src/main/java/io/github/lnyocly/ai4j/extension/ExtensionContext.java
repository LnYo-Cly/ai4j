package io.github.lnyocly.ai4j.extension;

import io.github.lnyocly.ai4j.extension.command.CommandRegistry;
import io.github.lnyocly.ai4j.extension.guardrail.GuardrailRegistry;
import io.github.lnyocly.ai4j.extension.interceptor.InterceptorRegistry;
import io.github.lnyocly.ai4j.extension.lifecycle.LifecycleHookRegistry;
import io.github.lnyocly.ai4j.extension.prompt.PromptRegistry;
import io.github.lnyocly.ai4j.extension.skill.SkillRegistry;
import io.github.lnyocly.ai4j.extension.tool.ToolRegistry;

import java.util.Optional;
import java.util.function.Consumer;

public interface ExtensionContext {

    ExtensionManifest manifest();

    ToolRegistry tools();

    CommandRegistry commands();

    SkillRegistry skills();

    PromptRegistry prompts();

    GuardrailRegistry guardrails();

    LifecycleHookRegistry lifecycle();

    InterceptorRegistry interceptors();

    default Disposable effect(Disposable cleanup) {
        if (cleanup == null) {
            throw new IllegalArgumentException("cleanup disposable must not be null");
        }
        throw new UnsupportedOperationException("extension context does not support effect registration");
    }

    default <T> Disposable provide(ServiceKey<T> key, T implementation) {
        if (key == null) {
            throw new IllegalArgumentException("service key must not be null");
        }
        if (implementation == null) {
            throw new IllegalArgumentException("service implementation must not be null: " + key.id());
        }
        throw new UnsupportedOperationException("extension context does not support service provision");
    }

    default <T> Optional<T> findService(ServiceKey<T> key) {
        if (key == null) {
            throw new IllegalArgumentException("service key must not be null");
        }
        return Optional.empty();
    }

    default <T> T service(ServiceKey<T> key) {
        if (key == null) {
            throw new IllegalArgumentException("service key must not be null");
        }
        Optional<T> found = findService(key);
        if (!found.isPresent()) {
            throw new ExtensionException("service not registered: " + key);
        }
        return found.get();
    }

    default <T> Disposable inject(ServiceKey<T> key, ServiceInjector<T> binder) {
        if (key == null) {
            throw new IllegalArgumentException("service key must not be null");
        }
        if (binder == null) {
            throw new IllegalArgumentException("service injector must not be null: " + key.id());
        }
        throw new UnsupportedOperationException("extension context does not support service injection");
    }

    default <T> Disposable inject(ServiceKey<T> key, Consumer<T> consumer) {
        if (key == null) {
            throw new IllegalArgumentException("service key must not be null");
        }
        if (consumer == null) {
            throw new IllegalArgumentException("service consumer must not be null: " + key.id());
        }
        return inject(key, new ServiceInjector<T>() {
            @Override
            public Disposable inject(T service) {
                consumer.accept(service);
                return Disposable.noop();
            }
        });
    }
}
