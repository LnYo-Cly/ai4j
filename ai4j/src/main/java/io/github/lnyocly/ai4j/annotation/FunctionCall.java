package io.github.lnyocly.ai4j.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/12 15:50
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FunctionCall {
    String name();
    String description();

    /**
     * 是否对该函数启用严格模式（strict mode）。OpenAI 官方建议开启。
     *
     * <p>开启后，{@link io.github.lnyocly.ai4j.platform.openai.tool.Tool.Function#getStrict()}
     * 为 {@code true}，且参数 schema 会被自动调整为严格模式要求的形态：
     * {@code additionalProperties=false}，所有字段列入 {@code required}，
     * 原本可选的字段标记为可空（{@code ["string","null"]}）。
     *
     * <p>默认 {@code false}，保持与历史版本一致的行为。
     */
    boolean strict() default false;
}
