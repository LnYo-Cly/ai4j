package io.github.lnyocly.ai4j.extension.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a program element as <strong>internal</strong> to ai4j.
 *
 * <p>An {@code @Internal} type, method, field, constructor, or package is part
 * of the implementation and is explicitly <em>not</em> part of the public API.
 * Consumers should never call, extend, or depend on internal elements.
 *
 * <p>Unlike {@link Experimental}, which invites cautious consumer use, internal
 * elements carry no contract at all:
 *
 * <ul>
 *   <li>They may be renamed, moved, rewritten, or deleted in any release without
 *       notice.</li>
 *   <li>No effort is made to preserve source or binary compatibility.</li>
 *   <li>Depending on them may break your build on any version bump.</li>
 * </ul>
 *
 * <h3>When to use this annotation</h3>
 * <ul>
 *   <li>Implementation classes, helper utilities, and data holders that are only
 *       public because of Java's package-access rules or serialization needs.</li>
 *   <li>Extension points that are only meant to be invoked by ai4j itself, not
 *       by downstream code.</li>
 *   <li>Entire packages whose contents are implementation detail — apply to
 *       {@code package-info.java}.</li>
 * </ul>
 *
 * <h3>Relationship to package conventions</h3>
 * <p>As a convention, ai4j packages whose path segment is {@code internal} (e.g.
 * {@code ...runtime.internal...}) are implicitly internal even without this
 * annotation. The annotation makes the intent explicit and visible in IDE
 * inspections and generated Javadoc.
 *
 * @see Experimental
 * @since 2.4.3
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD,
         ElementType.CONSTRUCTOR, ElementType.PACKAGE})
@Documented
public @interface Internal {

    /**
     * Optional short note explaining why the element exists as a public-visible
     * internal type, or where its public replacement lives.
     *
     * @return a human-readable note, or empty
     */
    String value() default "";
}
