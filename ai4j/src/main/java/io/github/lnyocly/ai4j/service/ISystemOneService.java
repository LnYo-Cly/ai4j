package io.github.lnyocly.ai4j.service;

import io.github.lnyocly.ai4j.systemone.entity.SystemOneModelCard;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneRequest;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneResponse;

import java.util.List;

/**
 * System One evaluation service (TypeSafe AI / Jev).
 *
 * <p>Unlike chat/responses services, a System One model does not generate
 * text: it evaluates a {@code state} against typed questions (Choice, Score,
 * Noul) in a single parallel call and returns typed answers with probability
 * distributions and confidence values.</p>
 */
public interface ISystemOneService {

    SystemOneResponse evaluate(String baseUrl, String apiKey, SystemOneRequest request) throws Exception;

    SystemOneResponse evaluate(SystemOneRequest request) throws Exception;

    /** Lists the models available to the account ({@code GET /v1/models}). */
    List<SystemOneModelCard> listModels() throws Exception;
}
