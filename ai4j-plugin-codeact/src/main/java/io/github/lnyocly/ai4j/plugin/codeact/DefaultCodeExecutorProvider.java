package io.github.lnyocly.ai4j.plugin.codeact;

import io.github.lnyocly.ai4j.agent.codeact.CodeExecutor;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutorProvider;
import io.github.lnyocly.ai4j.agent.codeact.GraalVmCodeExecutor;
import io.github.lnyocly.ai4j.agent.codeact.NashornCodeExecutor;

/**
 * Default {@link CodeExecutorProvider} SPI implementation selecting Nashorn ES5 on JDK 8
 * (or when GraalVM polyglot is absent) and GraalVM Python on Java 9+ when GraalVM polyglot is available.
 */
public class DefaultCodeExecutorProvider implements CodeExecutorProvider {

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public CodeExecutor create() {
        String javaSpecVersion = System.getProperty("java.specification.version", "");
        if ("1.8".equals(javaSpecVersion) || "8".equals(javaSpecVersion)) {
            return new NashornCodeExecutor();
        }
        if (isGraalPolyglotAvailable()) {
            return new GraalVmCodeExecutor();
        }
        return new NashornCodeExecutor();
    }

    /**
     * graal-sdk is an optional dependency; fall back to Nashorn when it is absent.
     */
    private static boolean isGraalPolyglotAvailable() {
        try {
            Class.forName("org.graalvm.polyglot.Context");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
