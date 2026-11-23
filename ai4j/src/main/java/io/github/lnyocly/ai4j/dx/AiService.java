package io.github.lnyocly.ai4j.dx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a declarative AI service interface proxied by {@link AiServiceProxyFactory}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AiService {

    /** Optional model or {@code "provider:model"} spec, e.g. {@code "deepseek:deepseek-chat"}. */
    String model() default "";

    /** Optional default system prompt for all methods in the service. */
    String system() default "";

    /** Optional parameter name identifying the memory session id when {@link MemoryId} is omitted. */
    String memorySessionParam() default "";

    /** Maximum tool execution rounds per invocation. */
    int maxToolRounds() default 8;
}
