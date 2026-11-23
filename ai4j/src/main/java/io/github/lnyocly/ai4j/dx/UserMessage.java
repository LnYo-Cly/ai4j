package io.github.lnyocly.ai4j.dx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a user prompt template on an {@link AiService} method or marks a parameter as the user prompt text.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER})
public @interface UserMessage {

    String value() default "";
}
