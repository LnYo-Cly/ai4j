package io.github.lnyocly.ai4j.agent.codeact;

public interface CodeExecutor {

    CodeExecutionResult execute(CodeExecutionRequest request) throws Exception;

    /**
     * Returns the primary scripting language expected by this executor
     * (for example {@code "javascript"} or {@code "python"}).
     */
    default String getLanguage() {
        return "python";
    }

    /**
     * Returns {@code true} when runtime prompt guidance should instruct the model
     * to emit ES5-compatible JavaScript with auto-parsed JSON tool results.
     */
    default boolean isEs5JavaScript() {
        String language = getLanguage();
        return "javascript".equalsIgnoreCase(language)
                || "js".equalsIgnoreCase(language)
                || "ecmascript".equalsIgnoreCase(language);
    }
}
