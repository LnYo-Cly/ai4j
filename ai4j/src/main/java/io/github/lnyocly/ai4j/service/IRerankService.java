package io.github.lnyocly.ai4j.service;

import io.github.lnyocly.ai4j.rerank.entity.RerankRequest;
import io.github.lnyocly.ai4j.rerank.entity.RerankResponse;

public interface IRerankService {

    RerankResponse rerank(String baseUrl, String apiKey, RerankRequest request) throws Exception;

    RerankResponse rerank(RerankRequest request) throws Exception;
}
