package io.github.lnyocly.ai4j.extension.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public API element as <strong>experimental</strong>.
 *
 * <p>An {@code @Experimental} type, method, or field is published so that early
 * adopters can try it and give feedback, but it is not yet covered by the
 * backward-compatibility promise that stable ai4j APIs carry. Concretely:
 *
 * <ul>
 *   <li>The signature, behaviour, or existence of the element may change in any
 *       future minor or patch release without being considered a breaking
 *       change.</li>
 *   <li>The element may be removed entirely once a better design is found, or
 *       promoted to stable (losing the annotation) once it proves itself.</li>
 *   <li>No deprecation grace period is guaranteed before such changes — if you
 *       depend on an experimental API, pin the exact version.</li>
 * </ul>
 *
 * <h3>When to use this annotation</h3>
 * <ul>
 *   <li>New public types or methods whose design is still settling and where
 *       real-world usage is needed before committing to a stable contract.</li>
 *   <li>APIs that integrate with fast-moving external surfaces (e.g. a new MCP
 *       transport, a draft provider capability) whose shape may shift upstream.</li>
 * </ul>
 *
 * <h3>Stability lifecycle</h3>
 * <p>ai4j API elements move through three states, and this annotation marks the
 * middle one:
 *
 * <ol>
 *   <li><em>Internal</em> — annotated {@link Internal}, not for consumer use at
 *       all.</li>
 *   <li><em>Experimental</em> — annotated {@code @Experimental}, usable but not
 *       stable.</li>
 *   <li><em>Stable</em> — no annotation; carries the full backward-compatibility
 *       promise within the current major version.</li>
 * </ol>
 *
 * <p>The {@code @Experimental} marker is inspired by similar concepts in other
 * Java AI/ML libraries (e.g. LangChain4j) but the semantics defined here are
 * specific to ai4j.
 *
 * @since 2.4.3
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Documented
public @interface Experimental {

    /**
     * The ai4j version in which this element first became experimental.
     *
     * @return a version string (e.g. {@code "2.4.3"}), or empty if unknown
     */
    String since() default "";

    /**
     * Optional short note describing what is still in flux or what feedback is
     * sought.
     *
     * @return a human-readable note, or empty
     */
    String note() default "";
}
