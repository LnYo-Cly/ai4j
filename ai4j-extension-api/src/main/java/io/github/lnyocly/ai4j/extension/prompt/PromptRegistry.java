package io.github.lnyocly.ai4j.extension.prompt;

import io.github.lnyocly.ai4j.extension.api.annotation.Experimental;

@Experimental(since = "2.4.3")
public interface PromptRegistry {

    void register(ExtensionPromptResource resource);
}
