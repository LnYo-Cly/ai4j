package io.github.lnyocly.ai4j.dx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an instance method on a POJO or service bean as an LLM-callable tool.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AiTool {

    /** Tool name exposed to the model; defaults to the Java method name when blank. */
    String name() default "";

    /** Tool description; alias for {@link #value()}. */
    String description() default "";

    /** Shorthand tool description, e.g. {@code @AiTool("Query customer orders")}. */
    String value() default "";
}
