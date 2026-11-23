package io.github.lnyocly.ai4j.dx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes a parameter of an {@link AiTool} method or an {@link AiService} template variable.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface AiToolParam {

    /** Parameter name; defaults to compiled parameter reflection name when blank. */
    String name() default "";

    /** Parameter description; alias for {@link #value()}. */
    String description() default "";

    /** Shorthand parameter description. */
    String value() default "";

    /** Whether this parameter is required in the JSON schema. */
    boolean required() default true;
}
