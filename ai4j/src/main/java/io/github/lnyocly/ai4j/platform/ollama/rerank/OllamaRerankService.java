package io.github.lnyocly.ai4j.platform.ollama.rerank;

import io.github.lnyocly.ai4j.config.OllamaConfig;
import io.github.lnyocly.ai4j.platform.standard.rerank.StandardRerankService;
import io.github.lnyocly.ai4j.service.Configuration;

public class OllamaRerankService extends StandardRerankService {

    public OllamaRerankService(Configuration configuration) {
        this(configuration, configuration == null ? null : configuration.getOllamaConfig());
    }

    public OllamaRerankService(Configuration configuration, OllamaConfig ollamaConfig) {
        super(configuration == null ? null : configuration.getOkHttpClient(),
                ollamaConfig == null ? null : ollamaConfig.getApiHost(),
                ollamaConfig == null ? null : ollamaConfig.getApiKey(),
                ollamaConfig == null ? null : ollamaConfig.getRerankUrl());
    }
}
