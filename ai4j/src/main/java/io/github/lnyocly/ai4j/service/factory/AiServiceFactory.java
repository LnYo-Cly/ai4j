package io.github.lnyocly.ai4j.service.factory;

import io.github.lnyocly.ai4j.service.Configuration;

/**
 * {@link AiService} 的创建工厂。
 */
public interface AiServiceFactory {

    AiService create(Configuration configuration);
}

