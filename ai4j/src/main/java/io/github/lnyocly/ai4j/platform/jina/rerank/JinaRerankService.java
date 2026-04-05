package io.github.lnyocly.ai4j.platform.jina.rerank;

import io.github.lnyocly.ai4j.config.JinaConfig;
import io.github.lnyocly.ai4j.platform.standard.rerank.StandardRerankService;
import io.github.lnyocly.ai4j.service.Configuration;

public class JinaRerankService extends StandardRerankService {

    public JinaRerankService(Configuration configuration) {
        this(configuration, configuration == null ? null : configuration.getJinaConfig());
    }

    public JinaRerankService(Configuration configuration, JinaConfig jinaConfig) {
        super(configuration == null ? null : configuration.getOkHttpClient(),
                jinaConfig == null ? null : jinaConfig.getApiHost(),
                jinaConfig == null ? null : jinaConfig.getApiKey(),
                jinaConfig == null ? null : jinaConfig.getRerankUrl());
    }
}
