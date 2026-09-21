package io.github.lnyocly.ai4j.testing;

import io.github.lnyocly.ai4j.service.ISystemOneService;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneAnswer;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneModelCard;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneRequest;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneResponse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * A queue-driven {@link ISystemOneService} for deterministic agent tests.
 *
 * <p>Enqueue one response per expected evaluation; each call records the
 * {@link SystemOneRequest} it received and replays the next scripted
 * response. When the script is exhausted the service returns an empty
 * response, or throws {@link IllegalStateException} after
 * {@link #failWhenExhausted(boolean)}.</p>
 *
 * <pre>{@code
 * ScriptedSystemOneService jev = new ScriptedSystemOneService()
 *         .enqueueAnswer("route", choiceAnswer("billing", 0.93));
 *
 * String route = new SystemOneRouter(jev)
 *         .route("billing").route("support")
 *         .minConfidence(0.7)
 *         .route(context, request, null);
 * }</pre>
 */
public class ScriptedSystemOneService implements ISystemOneService {

    private final Deque<SystemOneResponse> script = new ArrayDeque<SystemOneResponse>();
    private final List<SystemOneRequest> requests = new ArrayList<SystemOneRequest>();
    private final List<SystemOneModelCard> models = new ArrayList<SystemOneModelCard>();
    private boolean failWhenExhausted;

    public ScriptedSystemOneService enqueue(SystemOneResponse response) {
        script.addLast(response);
        return this;
    }

    /** Enqueues a response whose {@code answers} map is the given map. */
    public ScriptedSystemOneService enqueueAnswers(Map<String, SystemOneAnswer> answers) {
        SystemOneResponse response = new SystemOneResponse();
        response.setAnswers(answers);
        return enqueue(response);
    }

    /** Enqueues a response with a single answer under {@code key}. */
    public ScriptedSystemOneService enqueueAnswer(String key, SystemOneAnswer answer) {
        return enqueueAnswers(Collections.singletonMap(key, answer));
    }

    /** Models returned by {@link #listModels()}. */
    public ScriptedSystemOneService withModels(List<SystemOneModelCard> modelCards) {
        models.clear();
        if (modelCards != null) {
            models.addAll(modelCards);
        }
        return this;
    }

    public ScriptedSystemOneService failWhenExhausted(boolean failWhenExhausted) {
        this.failWhenExhausted = failWhenExhausted;
        return this;
    }

    /** Every request this service received, in order. */
    public List<SystemOneRequest> getRequests() {
        return Collections.unmodifiableList(requests);
    }

    @Override
    public SystemOneResponse evaluate(String baseUrl, String apiKey, SystemOneRequest request) throws Exception {
        return evaluate(request);
    }

    @Override
    public SystemOneResponse evaluate(SystemOneRequest request) throws Exception {
        requests.add(request);
        SystemOneResponse next = script.pollFirst();
        if (next != null) {
            return next;
        }
        if (failWhenExhausted) {
            throw new IllegalStateException("ScriptedSystemOneService script exhausted");
        }
        return new SystemOneResponse();
    }

    @Override
    public List<SystemOneModelCard> listModels() throws Exception {
        return new ArrayList<SystemOneModelCard>(models);
    }
}
